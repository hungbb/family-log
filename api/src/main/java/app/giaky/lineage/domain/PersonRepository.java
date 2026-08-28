package app.giaky.lineage.domain;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
