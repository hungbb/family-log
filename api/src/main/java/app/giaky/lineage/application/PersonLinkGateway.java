package app.giaky.lineage.application;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.Paged;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.domain.LineageGraphService;
import app.giaky.lineage.domain.NoteSanitizer;
import app.giaky.lineage.domain.Person;
import app.giaky.lineage.domain.PersonRepository;
import app.giaky.lineage.domain.TreeVersionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lineage's published window onto the <i>link</i> column and the two fields a linked user may edit
 * for themselves — the seam Phase 6 reaches the {@code persons} table through.
 *
 * <p>The same shape, and the same reason, as {@link LineageDirectory}: {@code architecture
 * .LayeringTest} rule 4 forbids Linking from importing {@code lineage.domain}, and without a
 * published service it would import it anyway by the second feature that wanted a name to show.
 *
 * <p><b>Deliberately not {@code @Transactional} on the writes.</b> Every one of them runs inside its
 * caller's transaction and must: linking a node and writing the claim decision that authorized it
 * are one fact, and a gateway that opened a transaction of its own could commit half of it. Calling
 * a write here outside a transaction fails loudly at the {@code @Modifying} statement, which is the
 * right way for that mistake to end.
 *
 * <p><b>It decides nothing about access.</b> Who may link, unlink or edit is the caller's question —
 * {@code PersonUserLinker} for the first two, the {@code linked_user_id} predicate in the statement
 * itself for the third. A gateway that started refusing would become a second policy.
 */
@Service
public class PersonLinkGateway {

    private final PersonRepository persons;
    private final PersonService personService;
    private final LineageGraphService graph;
    private final TreeVersionRepository treeVersions;
    private final NoteSanitizer sanitizer;
    private final Clock clock;

    public PersonLinkGateway(
            PersonRepository persons,
            PersonService personService,
            LineageGraphService graph,
            TreeVersionRepository treeVersions,
            NoteSanitizer sanitizer,
            Clock clock) {

        this.persons = persons;
        this.personService = personService;
        this.graph = graph;
        this.treeVersions = treeVersions;
        this.sanitizer = sanitizer;
        this.clock = clock;
    }

    // --- reads ---------------------------------------------------------------------------------

    /** One live node, or empty when it is deleted or belongs to another clan. */
    @Transactional(readOnly = true)
    public Optional<NodeRef> node(ClanId clanId, PersonId personId) {
        return persons.findById(clanId, personId).map(NodeRef::of);
    }

    /** I-P4: the node this account holds in this clan, if it holds one. */
    @Transactional(readOnly = true)
    public Optional<NodeRef> nodeLinkedTo(ClanId clanId, UserId userId) {
        return persons.findByLinkedUser(clanId, userId).map(NodeRef::of);
    }

    /**
     * Every node this account holds, across every clan (FR-N6).
     *
     * <p>The one read here that is not clan-scoped, and it cannot be: a profile edit is not an act
     * inside one family, and the question is which families the person is recorded in at all.
     */
    @Transactional(readOnly = true)
    public List<NodeRef> nodesLinkedTo(UserId userId) {
        return persons.findAllByLinkedUser(userId).stream().map(NodeRef::of).toList();
    }

    /**
     * The claiming candidates: live nodes in the clan with no account attached (B6.6, F6.2).
     *
     * <p>Built here rather than in Linking because {@code PersonQuery} lives in {@code
     * lineage.domain}, which no other context may import — and because the {@code linked = false}
     * filter is not the caller's to choose. An endpoint whose whole purpose is "nodes nobody has
     * taken" must not accept a parameter that turns it into "every node".
     *
     * <p>Delegates to {@link PersonService#search}, so membership, paging and the {@code canEdit}
     * stamping are the same code the ordinary person search uses.
     */
    @Transactional(readOnly = true)
    public Paged<PersonSummaryView> unlinkedCandidates(
            UserId caller,
            ClanId clanId,
            String text,
            Integer generation,
            BranchId branchId,
            int page,
            int size) {

        return personService.search(
                caller,
                clanId,
                new PersonRepository.PersonQuery(
                        text, generation, null, branchId, Boolean.FALSE, page, size));
    }

    /**
     * Who each of these people sits between — the tree context an unlinked-node browser shows
     * (F6.2) and a claim inbox needs to verify a claim (F6.5).
     *
     * <p>Names only. A candidate list is a list of nodes somebody is deciding between, and the thing
     * that tells two same-named relatives apart is their father and their children — not their birth
     * year, which the book usually does not have.
     */
    @Transactional(readOnly = true)
    public Map<PersonId, NodeContext> context(ClanId clanId, List<PersonId> personIds) {
        Map<PersonId, NodeContext> contexts = new LinkedHashMap<>();
        for (PersonId personId : personIds) {
            contexts.put(
                    personId,
                    new NodeContext(
                            graph.parentEdgesOf(clanId, personId).stream()
                                    .map(edge -> persons.findById(clanId, edge.parentPersonId()))
                                    .flatMap(Optional::stream)
                                    .map(Person::fullName)
                                    .toList(),
                            graph.childrenOf(clanId, personId).stream()
                                    .map(Person::fullName)
                                    .toList()));
        }
        return contexts;
    }

    // --- the link column -----------------------------------------------------------------------

    /**
     * The compare-and-set behind an approval (B6.11).
     *
     * @return false when somebody else took the node first
     */
    public boolean linkIfUnlinked(
            ClanId clanId, PersonId personId, UserId userId, UserId actor, Instant now) {

        boolean linked = persons.linkUserIfUnlinked(clanId, personId, userId, actor, now);
        if (linked) {
            // The tree payload carries linkedUserId on every node, so a link changes what a cached
            // tree would serve. Without this, the person who has just been recognised reloads the
            // tree and is answered 304 with the version that does not know about them.
            treeVersions.bump(clanId);
        }
        return linked;
    }

    /** The other half (FR-N5). */
    public boolean unlink(ClanId clanId, PersonId personId, UserId actor, Instant now) {
        boolean unlinked = persons.unlinkUser(clanId, personId, actor, now);
        if (unlinked) {
            treeVersions.bump(clanId);
        }
        return unlinked;
    }

    // --- the self-service exception (doc 04 §6) ------------------------------------------------

    /**
     * The two fields a linked user may edit on their own node without {@code TREE_EDIT} (B6.7).
     *
     * <p>Notes are sanitized here, by the same sanitizer every other note write uses (B4.13). That
     * is the reason this method exists at all rather than Linking calling the repository through a
     * port: a second write path for {@code notes_html} would be a second place to forget.
     *
     * @return false when the caller is not the node's linked user, which the statement decides
     */
    public boolean updateOwnProfile(
            ClanId clanId,
            PersonId personId,
            UserId linkedUserId,
            UUID avatarMediaId,
            String notesHtml) {

        NoteSanitizer.SanitizedNote note = sanitizer.sanitize(notesHtml);
        boolean written =
                persons.updateSelfServiceFields(
                        clanId,
                        personId,
                        linkedUserId,
                        avatarMediaId,
                        note.html(),
                        note.text(),
                        clock.instant());
        if (written) {
            treeVersions.bump(clanId);
        }
        return written;
    }

    /** Opt-in propagation from the account profile (FR-N6, B6.8). */
    public boolean propagateProfile(
            ClanId clanId,
            PersonId personId,
            UserId linkedUserId,
            boolean avatar,
            UUID avatarMediaId,
            boolean displayName,
            String alias) {

        boolean written =
                persons.updatePropagatedFields(
                        clanId,
                        personId,
                        linkedUserId,
                        avatar,
                        avatarMediaId,
                        displayName,
                        alias,
                        clock.instant());
        if (written) {
            treeVersions.bump(clanId);
        }
        return written;
    }

    /**
     * A person node, reduced to the facts linking is entitled to.
     *
     * @param branchId null for an unplaced node, which no {@code BRANCH}-scoped grant reaches
     */
    public record NodeRef(
            PersonId id,
            ClanId clanId,
            BranchId branchId,
            String fullName,
            UserId linkedUserId,
            Integer generation) {

        static NodeRef of(Person person) {
            return new NodeRef(
                    person.id(),
                    person.clanId(),
                    person.branchId(),
                    person.fullName(),
                    person.linkedUserId(),
                    person.generation());
        }
    }

    /** Who a node sits between, as names. */
    public record NodeContext(List<String> parentNames, List<String> childNames) {

        public static NodeContext empty() {
            return new NodeContext(List.of(), List.of());
        }
    }
}
