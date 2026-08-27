package app.giaky.clanorg.domain;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A person belongs to a clan (doc 02 §2.3).
 *
 * <p>The thinnest aggregate in the product, and deliberately so: it says who is in, where they sit,
 * and when that changed. It says nothing about what they may <i>do</i> — that is Phase 3's grant
 * model, and putting a {@code role} column here under time pressure is the mistake phase 2 doc §8
 * names in advance.
 *
 * <p>The id is a plain {@code UUID} rather than a wrapper type. Wrappers exist for the ids that
 * cross a context boundary or key a tenant ({@link ClanId}, {@link UserId}, {@link BranchId}); a
 * membership id does neither, and never appears in a signature next to another id it could be
 * confused with.
 *
 * @param homeBranchId where in the clan this member sits; null when they were admitted clan-wide
 * @param invitedBy who invited them; null for a join-by-code, which is every route in v1
 * @param approvedBy who let them in
 */
public record ClanMembership(
        UUID id,
        ClanId clanId,
        UserId userId,
        BranchId homeBranchId,
        MembershipStatus status,
        Instant joinedAt,
        Instant leftAt,
        UserId invitedBy,
        UserId approvedBy,
        Instant createdAt,
        Instant updatedAt) {

    public ClanMembership {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(clanId, "clanId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(joinedAt, "joinedAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        // Mirrors ck_membership_left_at in V5: an ended membership records when, a live one cannot.
        if (status.isActive() != (leftAt == null)) {
            throw new IllegalArgumentException(
                    "an active membership has no leaving time, and an ended one must have it");
        }
    }

    /**
     * A new active membership.
     *
     * <p>I-M1 — one active membership per (clan, user) — is not checked here, because an aggregate
     * cannot see its siblings. It is held by {@code ux_membership_active} in the database and by the
     * service that reads before it writes; the index is the one that survives a race.
     */
    public static ClanMembership grant(
            UUID id,
            ClanId clanId,
            UserId userId,
            BranchId homeBranchId,
            UserId approvedBy,
            Instant now) {

        return new ClanMembership(
                id,
                clanId,
                userId,
                homeBranchId,
                MembershipStatus.ACTIVE,
                now,
                null,
                null,
                approvedBy,
                now,
                now);
    }

    public boolean isActive() {
        return status.isActive();
    }

    /** The member left of their own accord (FR-J5). */
    public ClanMembership leave(Instant now) {
        return ended(MembershipStatus.LEFT, now);
    }

    /** Somebody else removed them (FR-C8). */
    public ClanMembership remove(Instant now) {
        return ended(MembershipStatus.REMOVED, now);
    }

    /** Places or re-places a member in a branch. */
    public ClanMembership movedToBranch(BranchId branchId, Instant now) {
        return new ClanMembership(
                id,
                clanId,
                userId,
                branchId,
                status,
                joinedAt,
                leftAt,
                invitedBy,
                approvedBy,
                createdAt,
                now);
    }

    private ClanMembership ended(MembershipStatus newStatus, Instant now) {
        if (!isActive()) {
            // Already over. Returning `this` keeps a double-click on "remove" from rewriting when
            // the membership ended, which is a fact somebody may one day need to be accurate.
            return this;
        }
        return new ClanMembership(
                id,
                clanId,
                userId,
                homeBranchId,
                newStatus,
                joinedAt,
                now,
                invitedBy,
                approvedBy,
                createdAt,
                now);
    }
}
