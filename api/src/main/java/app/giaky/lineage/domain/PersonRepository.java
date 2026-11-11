package app.giaky.lineage.domain;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.TenantExempt;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage for {@link Person} nodes.
 *
 * <p>Every method takes a {@link ClanId} (doc 02 §7 rule 4). A person id arrives from a URL, and
 * this parameter is the entire difference between a 404 and another family's records — which for a
 * private <i>gia phả</i> is the worst disclosure the product could make. {@code TenancyBoundaryIT}
 * substitutes ids across clans on every one of these paths.
 *
 * <p>Reads exclude soft-deleted rows unless the name says otherwise (I-P7). The two that do not are
 * for the restore flow, which is the one screen that has to see them.
 */
public interface PersonRepository {

    /** One live person, or empty when they are deleted or belong to another clan. */
    Optional<Person> findById(ClanId clanId, PersonId id);

    /** One person, deleted or not — the restore path, and the only caller that should want this. */
    Optional<Person> findByIdIncludingDeleted(ClanId clanId, PersonId id);

    /** Every live person of a clan. The master tree read (FR-T2) is one call to this. */
    List<Person> findAllByClan(ClanId clanId);

    /** The live people among {@code ids}, in no particular order. Used by subtree reads. */
    List<Person> findAllByIds(ClanId clanId, Collection<PersonId> ids);

    /** Everyone placed in one branch — the branch detail view and the move-branch impact check. */
    List<Person> findByBranch(ClanId clanId, BranchId branchId);

    /**
     * Recently soft-deleted people, most recent first (doc 05 §4.7).
     *
     * @param deletedSince the start of the recovery window; rows older than this are past restoring
     */
    List<Person> findDeletedSince(ClanId clanId, Instant deletedSince);

    /** I-P6, checked before a create that claims the title. */
    Optional<Person> findFoundingAncestor(ClanId clanId);

    /** I-P4: the node this account holds in this clan, if it holds one (Phase 6). */
    Optional<Person> findByLinkedUser(ClanId clanId, UserId linkedUserId);

    /**
     * The live nodes these accounts hold in this clan — at most one each (I-P4). What the member
     * list reads to say who is already in the tree (doc 05 §4.5).
     */
    List<Person> findLinkedTo(ClanId clanId, Collection<UserId> linkedUserIds);

    /**
     * Every node this account holds, across every clan (FR-N6).
     *
     * <p>The one read here with no {@link ClanId}, and it cannot have one: a profile edit is not a
     * clan-scoped act, and the whole question is which families the person is recorded in. The
     * account id is the scope, and it comes from the access token.
     */
    @TenantExempt(
            justification =
                    "Profile propagation spans every clan the account is linked in (FR-N6), so no"
                        + " ClanId exists to scope by. Scoped instead by the account id, which"
                        + " comes from the access token rather than from the request.")
    List<Person> findAllByLinkedUser(UserId linkedUserId);

    /** Search (B4.11, FR-P11). Diacritic-insensitive; see {@link PersonQuery}. */
    PersonPage search(ClanId clanId, PersonQuery query);

    /** How many live people the clan has — tree statistics, and the performance budget's subject. */
    long countByClan(ClanId clanId);

    Person save(Person person);

    /**
     * Writes a batch of people whose only change is {@code generation}.
     *
     * <p>Its own method because a re-parent can restamp a subtree of hundreds, and doing that
     * through {@link #save} means hundreds of round trips inside the transaction that is also
     * maintaining the closure. One statement keeps the mutation bounded (I-R5).
     *
     * @return how many rows were rewritten
     */
    int updateGenerations(ClanId clanId, Collection<GenerationAssignment> assignments, Instant now);

    /** One person's new <i>đời</i>, for {@link #updateGenerations}. */
    record GenerationAssignment(PersonId personId, Integer generation) {}

    // --- the link column (Phase 6) --------------------------------------------------------------

    /**
     * Attaches an account to a node, but only while the node is still unattached (B6.11).
     *
     * <p>A single conditional statement rather than a read followed by {@link #save}, and that is
     * the point: two approvers deciding the same claim at the same moment both pass the check above
     * them, and this is where exactly one of them wins. A read-modify-write through the aggregate
     * would let the second overwrite the first, silently.
     *
     * <p>Reached only through {@code PersonUserLinker} — {@code architecture.LinkWriterTest} fails
     * the build for a second caller, which is what makes the phase-6 exit criterion structural.
     *
     * @return false when the node was linked, deleted or gone by the time the statement ran
     */
    boolean linkUserIfUnlinked(
            ClanId clanId, PersonId personId, UserId userId, UserId actor, Instant now);

    /**
     * Detaches whatever account is on the node (FR-N5).
     *
     * @return false when there was nothing attached, which is not an error
     */
    boolean unlinkUser(ClanId clanId, PersonId personId, UserId actor, Instant now);

    /**
     * The self-service field whitelist of doc 04 §6, as a statement.
     *
     * <p>The {@code WHERE linked_user_id = :userId} predicate <i>is</i> the authorization: no
     * argument this method can be given reaches a node that is not the caller's own, and no column
     * outside the two named here can be written through it. That is a narrower guarantee than a
     * permission check, and a cheaper one to keep true.
     *
     * @return false when the caller is not the node's linked user
     */
    boolean updateSelfServiceFields(
            ClanId clanId,
            PersonId personId,
            UserId linkedUserId,
            UUID avatarMediaId,
            String notesHtml,
            String notesText,
            Instant now);

    /**
     * Copies opted-in account fields onto a linked node (FR-N6).
     *
     * <p>Separate from {@link #updateSelfServiceFields} because the fields differ and the trigger
     * does: this one runs from a profile edit, writes {@code alias} rather than notes, and touches
     * only the columns whose flag is on.
     *
     * @return false when the caller is not the node's linked user
     */
    boolean updatePropagatedFields(
            ClanId clanId,
            PersonId personId,
            UserId linkedUserId,
            boolean avatar,
            UUID avatarMediaId,
            boolean displayName,
            String alias,
            Instant now);

    /**
     * A page of search results.
     *
     * @param total matches across every page, for the envelope of doc 05 §3.2
     */
    record PersonPage(List<Person> content, long total) {}

    /**
     * The filters of doc 05 §4.7's {@code GET /persons}.
     *
     * @param text free text, matched against the unaccented name vector and by trigram; null or
     *     blank means "no text filter", which is how the page loads before anybody types
     * @param generation exact <i>đời</i>; null for any
     * @param lifeStatus null for any
     * @param branchId null for any; matches the branch itself, not its descendants, because the
     *     filter is a placement question rather than a scope question
     * @param linked {@code TRUE} for nodes with an account attached, {@code FALSE} for those
     *     without — the claiming candidate list of Phase 6 — and null for both
     */
    record PersonQuery(
            String text,
            Integer generation,
            LifeStatus lifeStatus,
            BranchId branchId,
            Boolean linked,
            int page,
            int size) {

        /** The largest page the search endpoint will serve, however large a {@code size} asks for. */
        public static final int MAX_SIZE = 100;

        public PersonQuery {
            text = text == null || text.isBlank() ? null : text.trim();
            page = Math.max(0, page);
            size = Math.clamp(size, 1, MAX_SIZE);
        }
    }
}
