package app.giaky.lineage.domain;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PartialDate;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A node in the family tree (doc 02 §2.5) — the aggregate goal G1 is about.
 *
 * <p><b>A person is not a user.</b> {@code linkedUserId} is null on almost every row and nothing
 * here needs it: a node is created, placed, related, searched and rendered without any account
 * existing. That is the whole point of the decoupling, and the demo script proves it by running end
 * to end without a single link.
 *
 * <p><b>What this aggregate does not know.</b> Its parents, children and spouses are edges, not
 * fields — {@link Parentage} and {@link Union} own those, because an aggregate that held its own
 * relationships would have to be loaded with its neighbours to be edited, and every tree operation
 * would fan out. {@code generation} is likewise not derived here: it is computed from the edges by
 * {@link LineageGraphService} and stamped on, which is why it is nullable and why the UI must
 * tolerate that (phase 4 §8).
 *
 * @param branchId organizational placement; null for an unplaced node, which no {@code BRANCH}
 *     grant can reach (doc 04 §3)
 * @param generation <i>đời</i>, 1 for the founding ancestor; null while unknown, never 0
 * @param birthOrder <i>thứ tự sinh</i>, 1-based sibling ordering; null when the book does not say
 * @param notesHtml sanitized on write, never on read (doc 02 §3)
 * @param notesText the plain-text projection of {@code notesHtml}, for search and excerpts
 * @param linkedUserId the account this node belongs to; null by default and written only by Linking
 * @param deletedAt soft delete; a deleted person is excluded from every tree read (I-P7)
 */
public record Person(
        PersonId id,
        ClanId clanId,
        BranchId branchId,
        PersonName name,
        Gender gender,
        LifeStatus lifeStatus,
        PartialDate birth,
        PartialDate death,
        String burialPlace,
        Integer generation,
        Integer birthOrder,
        UUID avatarMediaId,
        String notesHtml,
        String notesText,
        UserId linkedUserId,
        boolean foundingAncestor,
        long version,
        UserId createdBy,
        UserId updatedBy,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {

    /** {@code persons.burial_place} is {@code VARCHAR(255)}. */
    public static final int BURIAL_PLACE_MAX_LENGTH = 255;

    /** The founding ancestor's <i>đời</i>. Generations count from one, never from zero. */
    public static final int FIRST_GENERATION = 1;

    public Person {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(clanId, "clanId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        Objects.requireNonNull(gender, "gender must not be null");

        lifeStatus = lifeStatus == null ? LifeStatus.UNKNOWN : lifeStatus;
        birth = birth == null ? PartialDate.unknown() : birth;
        death = death == null ? PartialDate.unknown() : death;
        burialPlace = trimToNull(burialPlace, BURIAL_PLACE_MAX_LENGTH, "burialPlace");

        // I-P3. Somebody recorded as living cannot carry a date of death: the two facts contradict
        // each other, and the pair is what a careless edit produces when a status is changed and a
        // date is not cleared.
        if (lifeStatus == LifeStatus.ALIVE && death.isKnown()) {
            throw new IllegalArgumentException("a living person cannot have a date of death (I-P3)");
        }
        // I-P2. Only refused when the dates are certainly the wrong way round — an overlap, a circa
        // year, or an unknown date is INDETERMINATE, and refusing those would reject records that
        // real books contain (see DateComparison).
        if (birth.isCertainlyAfter(death)) {
            throw new IllegalArgumentException("birth cannot fall after death (I-P2)");
        }
        if (generation != null && generation < FIRST_GENERATION) {
            throw new IllegalArgumentException("generation counts from " + FIRST_GENERATION);
        }
        if (birthOrder != null && birthOrder < 1) {
            throw new IllegalArgumentException("birth order counts from 1");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    /**
     * A new node.
     *
     * <p>Placement — the branch, and whether this is the founding ancestor — is decided by the
     * application service before this is called, because it is an authorization question as much as
     * a modelling one (doc 05 §4.7). What arrives here is the decision, already made.
     */
    public static Person create(
            PersonId id,
            ClanId clanId,
            BranchId branchId,
            PersonName name,
            Gender gender,
            LifeStatus lifeStatus,
            PartialDate birth,
            PartialDate death,
            String burialPlace,
            Integer birthOrder,
            String notesHtml,
            String notesText,
            boolean foundingAncestor,
            UserId createdBy,
            Instant now) {

        return new Person(
                id,
                clanId,
                branchId,
                name,
                gender,
                lifeStatus,
                birth,
                death,
                burialPlace,
                // Unknown until the edges say otherwise, except for the one person the edges can
                // never reach from above: the founding ancestor is đời 1 by definition.
                foundingAncestor ? FIRST_GENERATION : null,
                birthOrder,
                null,
                notesHtml,
                notesText,
                null,
                foundingAncestor,
                0L,
                createdBy,
                createdBy,
                now,
                now,
                null);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Whether this node is placed in a branch at all. An unplaced node has no branch scope. */
    public boolean isPlaced() {
        return branchId != null;
    }

    public String fullName() {
        return name.fullName();
    }

    /**
     * The editable attributes, replaced wholesale (doc 05 §4.7, {@code PUT}).
     *
     * <p>Not structural: placement is {@link #movedTo}, relationships are their own aggregates, and
     * {@code linkedUserId} is Linking's alone (doc 02 §5). A single method that could change all of
     * those is a method whose permission check cannot be stated in one sentence.
     */
    public Person update(
            PersonName newName,
            Gender newGender,
            LifeStatus newLifeStatus,
            PartialDate newBirth,
            PartialDate newDeath,
            String newBurialPlace,
            Integer newBirthOrder,
            String newNotesHtml,
            String newNotesText,
            UserId editor,
            Instant now) {

        return new Person(
                id,
                clanId,
                branchId,
                newName,
                newGender,
                newLifeStatus,
                newBirth,
                newDeath,
                newBurialPlace,
                generation,
                newBirthOrder,
                avatarMediaId,
                newNotesHtml,
                newNotesText,
                linkedUserId,
                foundingAncestor,
                version,
                createdBy,
                editor,
                createdAt,
                now,
                deletedAt);
    }

    /**
     * This person's <i>đời</i>, recomputed from the edges.
     *
     * <p>Deliberately not an {@code update}: generation is derived, cascades to descendants, and is
     * written by {@link LineageGraphService} rather than by anybody editing a form. Keeping it out
     * of {@link #update} is what stops a stale value in a PUT body from overwriting a computed one.
     */
    public Person withGeneration(Integer newGeneration, Instant now) {
        if (Objects.equals(generation, newGeneration)) {
            return this;
        }
        return new Person(
                id,
                clanId,
                branchId,
                name,
                gender,
                lifeStatus,
                birth,
                death,
                burialPlace,
                newGeneration,
                birthOrder,
                avatarMediaId,
                notesHtml,
                notesText,
                linkedUserId,
                foundingAncestor,
                version,
                createdBy,
                updatedBy,
                createdAt,
                now,
                deletedAt);
    }

    /**
     * This person, placed in another branch (FR-P8).
     *
     * <p>Changes where the node sits organizationally, and therefore which grants reach it — which
     * is why doc 04 §5.2 requires permission on the person <i>and</i> on the destination branch, and
     * why that check lives in the service that can see both.
     */
    public Person movedTo(BranchId newBranchId, UserId editor, Instant now) {
        return new Person(
                id,
                clanId,
                newBranchId,
                name,
                gender,
                lifeStatus,
                birth,
                death,
                burialPlace,
                generation,
                birthOrder,
                avatarMediaId,
                notesHtml,
                notesText,
                linkedUserId,
                foundingAncestor,
                version,
                createdBy,
                editor,
                createdAt,
                now,
                deletedAt);
    }

    /**
     * Soft delete (FR-P10, I-P7).
     *
     * <p>The row stays, the edges stay, and the closure stays — so restoring is a timestamp rather
     * than a repair. Every read filters on {@code deletedAt}; nothing about the graph is unwound,
     * because a family arguing about whether a duplicate should have been merged needs the original
     * back exactly as it was.
     */
    public Person softDelete(UserId actor, Instant now) {
        if (isDeleted()) {
            return this;
        }
        return new Person(
                id,
                clanId,
                branchId,
                name,
                gender,
                lifeStatus,
                birth,
                death,
                burialPlace,
                generation,
                birthOrder,
                avatarMediaId,
                notesHtml,
                notesText,
                linkedUserId,
                foundingAncestor,
                version,
                createdBy,
                actor,
                createdAt,
                now,
                now);
    }

    /** Undoes {@link #softDelete}, within the recovery window the service enforces. */
    public Person restore(UserId actor, Instant now) {
        return new Person(
                id,
                clanId,
                branchId,
                name,
                gender,
                lifeStatus,
                birth,
                death,
                burialPlace,
                generation,
                birthOrder,
                avatarMediaId,
                notesHtml,
                notesText,
                linkedUserId,
                foundingAncestor,
                version,
                createdBy,
                actor,
                createdAt,
                now,
                null);
    }

    /**
     * The same node with no account on it.
     *
     * <p>Linking owns this field and is the only context that <i>sets</i> it (doc 02 §5). This is
     * for the one moment Lineage has to let go of it itself: bringing back a deleted node whose
     * account has, in the meantime, been given another node or left the clan. That row cannot come
     * back to life still pointing at them — I-P4 and I-P5 are about live nodes, and it is about to
     * be one. See {@code PersonService.restore}.
     */
    public Person withoutLink() {
        return new Person(
                id,
                clanId,
                branchId,
                name,
                gender,
                lifeStatus,
                birth,
                death,
                burialPlace,
                generation,
                birthOrder,
                avatarMediaId,
                notesHtml,
                notesText,
                null,
                foundingAncestor,
                version,
                createdBy,
                updatedBy,
                createdAt,
                updatedAt,
                deletedAt);
    }

    /** A new avatar (Phase 7 uploads it; the column and the setter exist from here). */
    public Person withAvatar(UUID mediaId, UserId editor, Instant now) {
        return new Person(
                id,
                clanId,
                branchId,
                name,
                gender,
                lifeStatus,
                birth,
                death,
                burialPlace,
                generation,
                birthOrder,
                mediaId,
                notesHtml,
                notesText,
                linkedUserId,
                foundingAncestor,
                version,
                createdBy,
                editor,
                createdAt,
                now,
                deletedAt);
    }

    static String trimToNull(String value, int maxLength, String field) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }
}
