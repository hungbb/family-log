package app.giaky.clanorg.domain;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Somebody has asked to join a clan (doc 02 §2.4).
 *
 * <p>A one-way state machine (I-J4). Every transition below refuses to run on a request that is not
 * {@code PENDING}, and refuses in the domain rather than only in the service, because "approve this
 * twice" is a double-click away and the second click must not create a second membership.
 *
 * @param targetBranchId the branch the requester asked for; null means the clan as a whole
 * @param message what the requester said about themselves; the approver often has nothing else
 * @param decidedBy who answered; null for an expiry, which nobody answered
 * @param expiresAt {@code createdAt + 90 days}
 */
public record ClanJoinRequest(
        UUID id,
        ClanId clanId,
        BranchId targetBranchId,
        UserId requesterUserId,
        String message,
        JoinRequestStatus status,
        UserId decidedBy,
        Instant decidedAt,
        String decisionReason,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt) {

    /** Doc 02 §2.4: a request nobody answers is closed after ninety days. */
    public static final Duration LIFETIME = Duration.ofDays(90);

    public static final int MESSAGE_MAX_LENGTH = 1_000;

    public static final int DECISION_REASON_MAX_LENGTH = 1_000;

    public ClanJoinRequest {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(clanId, "clanId must not be null");
        Objects.requireNonNull(requesterUserId, "requesterUserId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        message = Clan.trimToNull(message, MESSAGE_MAX_LENGTH, "message");
        decisionReason =
                Clan.trimToNull(decisionReason, DECISION_REASON_MAX_LENGTH, "decisionReason");
        // Mirrors ck_join_decision_paired in V5. Everything that leaves PENDING is stamped, the
        // scheduled sweep included; only a live request has no decision time.
        if (status.isPending() != (decidedAt == null)) {
            throw new IllegalArgumentException(
                    "a pending request has no decision time, and a decided one must have it");
        }
    }

    /** A fresh request. I-J1 and I-J2 belong to the service, which can see the other rows. */
    public static ClanJoinRequest submit(
            UUID id,
            ClanId clanId,
            BranchId targetBranchId,
            UserId requesterUserId,
            String message,
            Instant now) {

        return new ClanJoinRequest(
                id,
                clanId,
                targetBranchId,
                requesterUserId,
                message,
                JoinRequestStatus.PENDING,
                null,
                null,
                null,
                now,
                now,
                now.plus(LIFETIME));
    }

    public boolean isPending() {
        return status.isPending();
    }

    /** Whether the ninety days are up as of {@code now}. */
    public boolean hasExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * Accepted. The membership it produces is created by the service, in the same transaction.
     *
     * @throws IllegalStateException the request has already been decided (I-J4)
     */
    public ClanJoinRequest approve(UserId decider, Instant now) {
        return decided(JoinRequestStatus.APPROVED, decider, null, now);
    }

    /** Refused, with a reason the requester will read. */
    public ClanJoinRequest reject(UserId decider, String reason, Instant now) {
        return decided(JoinRequestStatus.REJECTED, decider, reason, now);
    }

    /** Withdrawn by the requester (FR-J5). */
    public ClanJoinRequest cancel(Instant now) {
        return decided(JoinRequestStatus.CANCELLED, requesterUserId, null, now);
    }

    /** Closed by the sweep. No decider, because nobody decided anything. */
    public ClanJoinRequest expire(Instant now) {
        return decided(JoinRequestStatus.EXPIRED, null, null, now);
    }

    private ClanJoinRequest decided(
            JoinRequestStatus newStatus, UserId decider, String reason, Instant now) {

        // I-J4. The one-way door, enforced where it cannot be routed around: a request that has
        // been answered stays answered, whatever the caller believes.
        if (!status.isDecidable()) {
            throw new IllegalStateException(
                    "a " + status + " join request cannot become " + newStatus);
        }
        return new ClanJoinRequest(
                id,
                clanId,
                targetBranchId,
                requesterUserId,
                message,
                newStatus,
                decider,
                now,
                reason,
                createdAt,
                now,
                expiresAt);
    }
}
