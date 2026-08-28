package app.giaky.lineage.application;

import app.giaky.authorization.domain.EditableSet;
import app.giaky.authorization.domain.PermissionResolver;
import app.giaky.clanorg.application.ClanDirectory;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.domain.AncestryProjection;
import app.giaky.lineage.domain.AncestryRepository;
import app.giaky.lineage.domain.LifeStatus;
import app.giaky.lineage.domain.Parentage;
import app.giaky.lineage.domain.ParentageRepository;
import app.giaky.lineage.domain.Person;
import app.giaky.lineage.domain.PersonRepository;
import app.giaky.lineage.domain.TreeVersionRepository;
import app.giaky.lineage.domain.Union;
import app.giaky.lineage.domain.UnionRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading the tree (B4.9, doc 05 §4.9, FR-T1/FR-T2).
 *
 * <p><b>Flat, in three scans.</b> The whole-clan read is one query for persons, one for parentages,
 * one for unions, assembled by the client. Doc 03 §4 measured the alternative — per-node recursion —
 * and it loses by a wide margin at every size that matters. A subtree read is the same three queries
 * bounded by one closure lookup, which is the whole reason {@code person_ancestry} exists.
 *
 * <p><b>Reads are clan-wide</b> (doc 04 §5.5). A member who can open the clan sees all of it; a
 * <i>gia phả</i> in fragments is not a <i>gia phả</i>. What branch and subtree scopes constrain is
 * writing, and that appears here as the per-node {@code canEdit} flag — computed once for the whole
 * payload by the batch resolver (doc 04 §4.1), never per node.
 *
 * <p>Every response is tagged with the clan's tree version, so a client that already has the tree
 * revalidates with {@code If-None-Match} and gets a {@code 304} without any of this running (B4.12).
 */
@Service
public class TreeQueryService {

    /**
     * The deepest a bounded read will go, whatever the client asks for.
     *
     * <p>Not a modelling limit — the closure has no depth cap — but a guard on the payload: a
     * request for depth 10 000 should not become a full-clan read wearing a subtree's clothes.
     */
    public static final int MAX_DEPTH = 64;

    private final PersonRepository persons;
    private final ParentageRepository parentages;
    private final UnionRepository unions;
    private final AncestryProjection ancestry;
    private final TreeVersionRepository treeVersions;
    private final PermissionResolver resolver;
    private final ClanDirectory clans;
    private final Clock clock;

    public TreeQueryService(
            PersonRepository persons,
            ParentageRepository parentages,
            UnionRepository unions,
            AncestryProjection ancestry,
            TreeVersionRepository treeVersions,
            PermissionResolver resolver,
            ClanDirectory clans,
            Clock clock) {

        this.persons = persons;
        this.parentages = parentages;
        this.unions = unions;
        this.ancestry = ancestry;
        this.treeVersions = treeVersions;
        this.resolver = resolver;
        this.clans = clans;
        this.clock = clock;
    }

    /**
     * The clan's tree version, for an {@code ETag} check that costs one indexed read.
     *
     * <p>Separate from {@link #tree} on purpose: the point of the tag is to answer {@code 304}
     * without assembling the payload, and a method that returned the payload alongside the version
     * would have already done the work.
     */
    @Transactional(readOnly = true)
    public long treeVersion(UserId caller, ClanId clanId) {
        resolver.requireMembership(caller, clanId);
        return treeVersions.current(clanId);
    }

    /**
     * The master view (FR-T2), or a subtree of it.
     *
     * @param rootPersonId null for the whole clan; otherwise the top of the subtree to return
     * @param depth generations below the root to include; null for all of them. Ignored without a
     *     root, because "the first three generations of everybody" is not a question the tree asks
     */
    @Transactional(readOnly = true)
    public TreeView tree(UserId caller, ClanId clanId, PersonId rootPersonId, Integer depth) {
        resolver.requireMembership(caller, clanId);

        List<Person> people;
        boolean truncated = false;

        if (rootPersonId == null) {
            people = persons.findAllByClan(clanId);
        } else {
            requireExists(clanId, rootPersonId);
            int bound = depth == null ? MAX_DEPTH : Math.clamp(depth, 0, MAX_DEPTH);
            Set<PersonId> withinBound = ancestry.descendantsOf(clanId, rootPersonId, bound);
            people = persons.findAllByIds(clanId, withinBound);
            if (depth != null) {
                // Cheaper than it looks — one indexed count against the same closure rows — and it
                // is what lets the client say "there is more below" instead of showing a family
                // that appears to end.
                truncated = ancestry.descendantsOf(clanId, rootPersonId).size() > withinBound.size();
            }
        }

        Set<PersonId> included = new HashSet<>();
        for (Person person : people) {
            included.add(person.id());
        }

        List<Parentage> edges =
                (rootPersonId == null
                                ? parentages.findAllByClan(clanId)
                                : parentages.findByChildren(clanId, included))
                        .stream()
                        // Both endpoints have to be in the payload, or the client draws an edge to a
                        // node it does not have. A subtree read reaches exactly this case at its top.
                        .filter(
                                edge ->
                                        included.contains(edge.parentPersonId())
                                                && included.contains(edge.childPersonId()))
                        .toList();

        List<Union> marriages =
                (rootPersonId == null
                                ? unions.findAllByClan(clanId)
                                : unions.findByPartners(clanId, included))
                        .stream()
                        .filter(
                                union ->
                                        included.contains(union.partnerAId())
                                                && included.contains(union.partnerBId()))
                        .toList();

        return assemble(caller, clanId, people, edges, marriages, truncated);
    }

    /**
     * Everyone above a person, nearest first (doc 05 §4.9) — the breadcrumb of FR-T6.
     *
     * <p>Includes the person themselves at depth 0, because the closure does and because a
     * breadcrumb that omitted its own subject would need special-casing in every caller.
     */
    @Transactional(readOnly = true)
    public List<AncestorView> ancestors(UserId caller, ClanId clanId, PersonId personId) {
        resolver.requireMembership(caller, clanId);
        requireExists(clanId, personId);

        List<AncestryRepository.AncestorLink> links = ancestry.ancestorsOf(clanId, personId);
        Map<PersonId, Integer> depthById = new HashMap<>();
        for (AncestryRepository.AncestorLink link : links) {
            depthById.merge(link.ancestorId(), link.depth(), Math::min);
        }

        EditableSet editable = resolver.editableSet(caller, clanId);
        List<AncestorView> chain = new ArrayList<>();
        for (Person person : persons.findAllByIds(clanId, depthById.keySet())) {
            chain.add(
                    new AncestorView(
                            PersonService.summary(person, editable), depthById.get(person.id())));
        }
        chain.sort(Comparator.comparingInt(AncestorView::depth));
        return List.copyOf(chain);
    }

    /**
     * Counts for the overview (doc 05 §4.9).
     *
     * <p>One scan of the clan's people, and everything computed from it in memory. Deliberately not
     * five aggregate queries: this is the same single indexed scan the tree read already makes, and
     * at the scale NFR-1 sets, five round trips cost more than one pass over the rows.
     */
    @Transactional(readOnly = true)
    public TreeStatsView stats(UserId caller, ClanId clanId) {
        resolver.requireMembership(caller, clanId);

        List<Person> people = persons.findAllByClan(clanId);
        if (people.isEmpty()) {
            return TreeStatsView.empty();
        }

        long living = 0;
        long deceased = 0;
        long unlinked = 0;
        Integer maxGeneration = null;
        Map<Integer, Long> byGeneration = new LinkedHashMap<>();
        Map<UUID, Long> byBranch = new LinkedHashMap<>();

        for (Person person : people) {
            if (person.lifeStatus() == LifeStatus.ALIVE) {
                living++;
            } else if (person.lifeStatus() == LifeStatus.DECEASED) {
                deceased++;
            }
            if (person.linkedUserId() == null) {
                unlinked++;
            }
            Integer generation = person.generation();
            byGeneration.merge(generation, 1L, Long::sum);
            if (generation != null && (maxGeneration == null || generation > maxGeneration)) {
                maxGeneration = generation;
            }
            if (person.branchId() != null) {
                byBranch.merge(person.branchId().value(), 1L, Long::sum);
            }
        }

        List<TreeStatsView.BranchCount> branchCounts =
                byBranch.entrySet().stream()
                        .map(entry -> new TreeStatsView.BranchCount(entry.getKey(), entry.getValue()))
                        .toList();

        return new TreeStatsView(
                people.size(),
                living,
                deceased,
                unlinked,
                maxGeneration,
                TreeStatsView.generationCounts(byGeneration),
                branchCounts);
    }

    // --- internals -----------------------------------------------------------------------------

    private TreeView assemble(
            UserId caller,
            ClanId clanId,
            List<Person> people,
            List<Parentage> edges,
            List<Union> marriages,
            boolean truncated) {

        EditableSet editable = resolver.editableSet(caller, clanId);

        // The roots of the returned payload: everybody with no parent inside it. Usually several —
        // a founding ancestor, and every wife who married into the family.
        Set<PersonId> withParents = new HashSet<>();
        for (Parentage edge : edges) {
            withParents.add(edge.childPersonId());
        }

        List<UUID> rootPersonIds = new ArrayList<>();
        List<PersonSummaryView> summaries = new ArrayList<>(people.size());
        Integer maxGeneration = null;
        for (Person person : people) {
            summaries.add(PersonService.summary(person, editable));
            if (!withParents.contains(person.id())) {
                rootPersonIds.add(person.id().value());
            }
            if (person.generation() != null
                    && (maxGeneration == null || person.generation() > maxGeneration)) {
                maxGeneration = person.generation();
            }
        }

        List<TreeView.TreeBranch> branches =
                clans.branches(clanId).stream()
                        .map(
                                branch ->
                                        new TreeView.TreeBranch(
                                                branch.id().value(),
                                                branch.name(),
                                                branch.path(),
                                                branch.depth(),
                                                branch.rootPersonId()))
                        .toList();

        return new TreeView(
                clanId.value(),
                List.copyOf(rootPersonIds),
                List.copyOf(summaries),
                edges.stream().map(ParentageView::of).toList(),
                marriages.stream().map(UnionView::of).toList(),
                branches,
                new TreeView.Meta(people.size(), maxGeneration, truncated, clock.instant()),
                treeVersions.current(clanId));
    }

    private void requireExists(ClanId clanId, PersonId personId) {
        if (persons.findById(clanId, personId).isEmpty()) {
            throw new NotFoundException(ErrorCode.PERSON_NOT_FOUND, "No such person.");
        }
    }
}
