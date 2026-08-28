package app.giaky.clanorg.domain;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The clan aggregate root (doc 02 §2.2) — a <i>dòng họ</i>, and the tenancy boundary everything else
 * in the product hangs from.
 *
 * <p>Immutable, in the same style as {@code identity.domain.User}: every transition is a named
 * method returning a new instance, which the application service saves. What that buys is worth the
 * extra allocation — a change is a value you can assert on, and the whole lifecycle is testable
 * without a database.
 *
 * @param id the tenancy key
 * @param clanCode the shareable join code; immutable for the life of the clan (I-C1)
 * @param name the family name as it is displayed, diacritics and all
 * @param description free text; nullable
 * @param originPlace <i>quê quán</i>, the ancestral village or province; nullable
 * @param coverMediaId reference into {@code media_objects}; null until Phase 7
 * @param visibility {@code PRIVATE} in v1
 * @param rootBranchId the clan single root branch (I-C2); null only between the two inserts of the
 *     creating transaction
 * @param version optimistic lock, checked against the {@code version} a PATCH carries
 * @param createdBy the founder — and, until Phase 3, the only administrator (phase 2 doc §1)
 * @param updatedBy who last changed any of the above
 * @param deletedAt soft-delete marker; a deleted clan is invisible to every read
 */
public record Clan(
        ClanId id,
        ClanCode clanCode,
        String name,
        String description,
        String originPlace,
        UUID coverMediaId,
        ClanVisibility visibility,
        BranchId rootBranchId,
        long version,
        UserId createdBy,
        UserId updatedBy,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {

    /** {@code clans.name} is {@code VARCHAR(200)}. */
    public static final int NAME_MAX_LENGTH = 200;

    /** {@code clans.origin_place} is {@code VARCHAR(255)}. */
    public static final int ORIGIN_PLACE_MAX_LENGTH = 255;

    /** Long enough for a family history, short enough that nobody pastes a book into it. */
    public static final int DESCRIPTION_MAX_LENGTH = 4_000;

    public Clan {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(clanCode, "clanCode must not be null");
        Objects.requireNonNull(visibility, "visibility must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        name = requireName(name);
        description = trimToNull(description, DESCRIPTION_MAX_LENGTH, "description");
        originPlace = trimToNull(originPlace, ORIGIN_PLACE_MAX_LENGTH, "originPlace");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    /**
     * A brand-new clan, with no root branch yet.
     *
     * <p>{@code rootBranchId} is null for the length of one transaction: the clan is inserted, its
     * root branch is inserted against it, and {@link #withRootBranch} closes the loop. The alternative
     * — generating both ids first and inserting the branch before the clan — trades a deferred
     * foreign key for a harder-to-read service, and the deferred constraint is already in V4.
     */
    public static Clan create(
            ClanId id,
            ClanCode clanCode,
            String name,
            String description,
            String originPlace,
            UserId createdBy,
            Instant now) {

        return new Clan(
                id,
                clanCode,
                name,
                description,
                originPlace,
                null,
                ClanVisibility.PRIVATE,
                null,
                0L,
                createdBy,
                createdBy,
                now,
                now,
                null);
    }

    /** Points the clan at the root branch that was just created for it (I-C2). */
    public Clan withRootBranch(BranchId branchId) {
        Objects.requireNonNull(branchId, "branchId must not be null");
        if (rootBranchId != null) {
            // I-C2. A clan gets exactly one root, once; a second call means the creating service
            // was re-entered, which is a bug worth failing on rather than a state to repair.
            throw new IllegalStateException("clan already has a root branch");
        }
        return new Clan(
                id,
                clanCode,
                name,
                description,
                originPlace,
                coverMediaId,
                visibility,
                branchId,
                version,
                createdBy,
                updatedBy,
                createdAt,
                updatedAt,
                deletedAt);
    }

    /**
     * Edits the descriptive fields (FR-C3).
     *
     * <p>A null argument means "leave it alone", which is what an absent field in a PATCH means.
     * {@code clanCode} is not a parameter and never will be (I-C1).
     */
    public Clan update(
            String newName,
            String newDescription,
            String newOriginPlace,
            UUID newCoverMediaId,
            UserId editor,
            Instant now) {

        return new Clan(
                id,
                clanCode,
                newName == null ? name : newName,
                newDescription == null ? description : newDescription,
                newOriginPlace == null ? originPlace : newOriginPlace,
                newCoverMediaId == null ? coverMediaId : newCoverMediaId,
                visibility,
                rootBranchId,
                version,
                createdBy,
                editor,
                createdAt,
                now,
                deletedAt);
    }

    /**
     * Soft delete (doc 05 §4.3): the rows stay, every read stops seeing them.
     *
     * <p>Idempotent — deleting a deleted clan changes nothing rather than moving the recovery
     * window, which is the difference between a retry and a lost thirty days.
     */
    public Clan softDelete(UserId actor, Instant now) {
        if (isDeleted()) {
            return this;
        }
        return new Clan(
                id,
                clanCode,
                name,
                description,
                originPlace,
                coverMediaId,
                visibility,
                rootBranchId,
                version,
                createdBy,
                actor,
                createdAt,
                now,
                now);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Who founded this clan.
     *
     * <p>In Phase 2 this <i>was</i> the authorization model. It is not any more — administering a
     * clan means holding a live {@code CLAN_ADMIN} grant, which the founder is merely the first
     * person to be given (doc 04 §6) and can be revoked from once somebody else has one. What
     * remains is a fact about history, kept because a clan's origin is worth recording and because
     * nothing else answers "whose idea was this?".
     *
     * <p>Nothing in the product decides access with it. If something starts to, that is the second
     * policy doc 04 §1.4 exists to prevent.
     */
    public boolean isCreatedBy(UserId user) {
        return createdBy != null && createdBy.equals(user);
    }

    private static String requireName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "name must be at most " + NAME_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    /**
     * Trims, and turns an empty result into null.
     *
     * <p>An empty string and a null both mean "not filled in", and storing both is how a query grows
     * an {@code OR field = ''} that somebody eventually forgets.
     */
    static String trimToNull(String value, int maxLength, String what) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(what + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }
}
