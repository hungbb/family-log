package app.giaky.lineage.application;

import app.giaky.authorization.domain.EditableSet;
import app.giaky.authorization.domain.Permission;
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
import app.giaky.lineage.domain.Person;
import app.giaky.lineage.domain.PersonRepository;
import app.giaky.lineage.domain.TreeSnapshotCache;
import app.giaky.lineage.domain.TreeSnapshotRepository;
import app.giaky.lineage.domain.TreeSnapshotRepository.NodeRow;
import app.giaky.lineage.domain.TreeSnapshotRepository.TreeSnapshot;
import app.giaky.lineage.domain.TreeVersionRepository;
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
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading the tree (B4.9 and B5.1–B5.5, doc 05 §4.9, FR-T1/FR-T2).
 *
 * <p><b>Flat, in three scans.</b> The whole-clan read is one query for persons, one for parentages,
 * one for unions, assembled by the client. Doc 03 §4 measured the alternative — per-node recursion —
 * and it loses by a wide margin at every size that matters. A subtree read is the same three queries
 * bounded by one closure lookup, which is the whole reason {@code person_ancestry} exists. Phase 5
 * moved those three off JPA and onto {@link TreeSnapshotRepository}: the payload wants fourteen
 * columns per person, and an entity read hands over the aggregate and a dirty-checking snapshot of
 * it (B5.2).
 *
 * <p><b>Reads are clan-wide</b> (doc 04 §5.5). A member who can open the clan sees all of it; a
 * <i>gia phả</i> in fragments is not a <i>gia phả</i>. What branch and subtree scopes constrain is
 * writing, and that appears here as the per-node {@code canEdit} flag — computed once for the whole
 * payload by the batch resolver (doc 04 §4.1), never per node.
 *
 * <p><b>Three layers of not doing the work</b>, cheapest first. A client that already holds the tree
 * revalidates with {@code If-None-Match} and gets a {@code 304} for the cost of one indexed read
 * (B4.12) — none of this runs. A client that does not, but asks for a clan somebody else has read
 * since its last edit, gets the snapshot from {@link TreeSnapshotCache} keyed on the same version
 * (B5.3). Only the first reader after an edit pays for the scans.
 *
 * <p>The cache holds the rows, never the finished payload: rows are the same for every member, and
 * {@code canEdit} is not.
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
    private final TreeSnapshotRepository snapshots;
    private final TreeSnapshotCache cache;
    private final AncestryProjection ancestry;
    private final TreeVersionRepository treeVersions;
    private final PermissionResolver resolver;
    private final ClanDirectory clans;
    private final Clock clock;

    public TreeQueryService(
            PersonRepository persons,
            TreeSnapshotRepository snapshots,
            TreeSnapshotCache cache,
            AncestryProjection ancestry,
            TreeVersionRepository treeVersions,
            PermissionResolver resolver,
            ClanDirectory clans,
            Clock clock) {

        this.persons = persons;
        this.snapshots = snapshots;
        this.cache = cache;
        this.ancestry = ancestry;
        this.treeVersions = treeVersions;
        this.resolver = resolver;
        this.clans = clans;
        this.clock = clock;
    }

    /**
     * What a tree-read {@code ETag} has to be built from, for a check that costs no more than the
     * read it is avoiding.
     *
     * <p>Separate from {@link #tree} on purpose: the point of the tag is to answer {@code 304}
     * without assembling the payload, and a method that returned the payload alongside the version
     * would have already done the work.
     *
     * <p><b>The version alone is not a validator.</b> It identifies the clan's rows, and the rows
     * are the same for everybody — but {@code canEdit} is not, so at one version this URL has as
     * many representations as there are distinct sets of edit rights among the members (#49).
     * Answering {@code 304} on the version alone hands a plain member the flags of whoever
     * populated the cache. {@link #editRights} is what separates them.
     *
     * <p><b>Nor does the version cover the whole body.</b> It counts lineage mutations, and the
     * payload also carries the clan's branches — their names, paths, depths and root people. A
     * branch renamed or moved changes the response without touching the counter, so a revalidation
     * kept the old names until some unrelated birth happened to bump it (#54). {@link
     * #branchStructure} closes that.
     *
     * @param version the clan's tree version, which the payload also carries
     * @param editRights a stable rendering of the scopes at which the caller holds {@code
     *     TREE_EDIT} — the whole of what {@code canEdit} is computed from
     * @param branchStructure a stable rendering of the branch block of the payload
     */
    public record TreeValidator(long version, String editRights, String branchStructure) {}

    /**
     * The validator for this caller's view of this clan's tree.
     *
     * <p>Three indexed reads: the tree version, the caller's grants — which {@code GrantPort} has
     * already cached for this request if anything has asked about permissions — and the clan's
     * branches. Still a fixed, small cost against a payload that grows with the family, which is
     * what B4.12 bought.
     *
     * <p>Deliberately not the expanded {@link EditableSet} — that costs a branch-path load and a
     * closure query per subtree grant, and the scopes it is expanded *from* change whenever its
     * answer does.
     */
    @Transactional(readOnly = true)
    public TreeValidator treeValidator(UserId caller, ClanId clanId) {
        resolver.requireMembership(caller, clanId);
        return new TreeValidator(
                treeVersions.current(clanId),
                editRights(caller, clanId),
                branchStructure(clanId));
    }

    /**
     * A rendering of exactly the branch block the payload will carry.
     *
     * <p><b>Derived, not counted.</b> A {@code branchVersion} counter beside {@code treeVersion}
     * would have to be bumped by every branch mutation — and #54 exists precisely because five of
     * them did not bump anything. This is computed from {@link #branchesOf}, the same method that
     * fills the payload, so the fingerprint changes if and only if that block changes, and a sixth
     * branch mutation is covered the day it is written rather than the day somebody remembers it.
     *
     * <p>It also leaves the snapshot cache alone. Bumping {@code treeVersion} for a rename would
     * evict the person rows keyed on it (B5.3) and make a five-thousand-person rescan the price of
     * correcting a typo.
     *
     * <p>The name goes last and length-prefixed because it is the one free-text field here: a
     * branch called {@code "Chi|Hai"} must not be able to spell the same line as two other
     * branches, which is the sort of collision that ends in a wrong {@code 304}.
     */
    private String branchStructure(ClanId clanId) {
        return branchesOf(clanId).stream()
                .map(
                        branch ->
                                branch.id()
                                        + "|"
                                        + branch.depth()
                                        + "|"
                                        + branch.rootPersonId()
                                        + "|"
                                        + branch.path()
                                        + "|"
                                        + branch.name().length()
                                        + ":"
                                        + branch.name())
                // Sorted, so the order the rows arrive in cannot change the tag. The id leads, so
                // this is an order by branch id.
                .sorted()
                .collect(Collectors.joining(";"));
    }

    /** The clan's branches as the tree payload carries them — see {@link #branchStructure}. */
    private List<TreeView.TreeBranch> branchesOf(ClanId clanId) {
        return clans.branches(clanId).stream()
                .map(
                        branch ->
                                new TreeView.TreeBranch(
                                        branch.id().value(),
                                        branch.name(),
                                        branch.path(),
                                        branch.depth(),
                                        branch.rootPersonId()))
                .toList();
    }

    /**
     * The caller's {@code TREE_EDIT} scopes, sorted and joined.
     *
     * <p>Sorted because a set has no order and a validator must be stable across requests. Two
     * members holding the same scopes genuinely share a representation, so they share a tag —
     * folding in the user id instead would be correct but would miss every such cache hit, and
     * would go stale the moment somebody's grants changed without the tree changing.
     */
    private String editRights(UserId caller, ClanId clanId) {
        return resolver.scopesHolding(caller, clanId, Permission.TREE_EDIT).stream()
                .map(scope -> scope.type() + ":" + scope.refId())
                .sorted()
                .collect(Collectors.joining(","));
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

        // Read first, and inside this transaction: the snapshot the loader below produces is the
        // tree at exactly this version, because a mutation bumps the counter in its own transaction
        // (B4.12). A version read after the scans could name a tree the scans never saw.
        long version = treeVersions.current(clanId);

        if (rootPersonId == null) {
            return assemble(
                    caller,
                    clanId,
                    cache.get(clanId, version, () -> snapshots.ofClan(clanId)),
                    false,
                    version);
        }

        requireExists(clanId, rootPersonId);
        int bound = depth == null ? MAX_DEPTH : Math.clamp(depth, 0, MAX_DEPTH);
        Set<PersonId> withinBound = ancestry.descendantsOf(clanId, rootPersonId, bound);

        boolean truncated = false;
        if (depth != null) {
            // Cheaper than it looks — one indexed count against the same closure rows — and it is
            // what lets the client say "there is more below" instead of showing a family that
            // appears to end.
            truncated = ancestry.descendantsOf(clanId, rootPersonId).size() > withinBound.size();
        }

        // Deliberately uncached. A subtree read is keyed by root *and* depth as well as by version,
        // so its key space is the clan times its depth bound — one entry per person a client has
        // ever expanded — while the payload it saves is by definition a small one. The whole-clan
        // read is the one that is both expensive and shared.
        return assemble(
                caller, clanId, snapshots.ofPersons(clanId, withinBound), truncated, version);
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
     * Counts for the overview (doc 05 §4.9, B5.5).
     *
     * <p>One pass over the same snapshot the tree read uses, and everything computed from it in
     * memory. Deliberately not five aggregate queries and deliberately not its own scan: whoever
     * asks for the statistics is looking at the tree, so the rows are already in the cache under the
     * same key, and five round trips would cost more than a loop over rows already in hand.
     */
    @Transactional(readOnly = true)
    public TreeStatsView stats(UserId caller, ClanId clanId) {
        resolver.requireMembership(caller, clanId);

        long version = treeVersions.current(clanId);
        List<NodeRow> people =
                cache.get(clanId, version, () -> snapshots.ofClan(clanId)).persons();
        if (people.isEmpty()) {
            return TreeStatsView.empty();
        }

        long living = 0;
        long deceased = 0;
        long unlinked = 0;
        Integer maxGeneration = null;
        Map<Integer, Long> byGeneration = new LinkedHashMap<>();
        Map<UUID, Long> byBranch = new LinkedHashMap<>();

        for (NodeRow person : people) {
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
                byBranch.merge(person.branchId(), 1L, Long::sum);
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
            TreeSnapshot snapshot,
            boolean truncated,
            long treeVersion) {

        EditableSet editable = resolver.editableSet(caller, clanId);

        // The roots of the returned payload: everybody with no parent inside it. Usually several —
        // a founding ancestor, and every wife who married into the family.
        Set<UUID> withParents = new HashSet<>();
        for (TreeSnapshotRepository.EdgeRow edge : snapshot.parentages()) {
            withParents.add(edge.childPersonId());
        }

        List<UUID> rootPersonIds = new ArrayList<>();
        List<PersonSummaryView> summaries = new ArrayList<>(snapshot.persons().size());
        Integer maxGeneration = null;
        for (NodeRow person : snapshot.persons()) {
            summaries.add(PersonSummaryView.of(person, editable));
            if (!withParents.contains(person.id())) {
                rootPersonIds.add(person.id());
            }
            if (person.generation() != null
                    && (maxGeneration == null || person.generation() > maxGeneration)) {
                maxGeneration = person.generation();
            }
        }

        // The same method the validator fingerprints, so the tag and the body cannot disagree
        // about what the branch block is (#54).
        List<TreeView.TreeBranch> branches = branchesOf(clanId);

        return new TreeView(
                clanId.value(),
                List.copyOf(rootPersonIds),
                List.copyOf(summaries),
                snapshot.parentages().stream().map(ParentageView::of).toList(),
                snapshot.unions().stream().map(UnionView::of).toList(),
                branches,
                new TreeView.Meta(
                        snapshot.persons().size(), maxGeneration, truncated, clock.instant()),
                treeVersion);
    }

    private void requireExists(ClanId clanId, PersonId personId) {
        if (persons.findById(clanId, personId).isEmpty()) {
            throw new NotFoundException(ErrorCode.PERSON_NOT_FOUND, "No such person.");
        }
    }
}
