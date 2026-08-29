package app.giaky.linking.domain;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Somebody saying "this row in the book is me" (doc 02 §2.7, FR-N1).
 *
 * <p><b>A request, not a self-service link.</b> A <i>gia phả</i> has two Nguyễn Văn Ans of the same
 * <i>đời</i> more often than not, and the only person who can tell them apart is a relative who knew
 * both. That is the whole reason this aggregate exists rather than a {@code PUT} on the node.
 *
 * <p>The state machine (I-K5) is enforced here rather than only in the service, for the same reason
 * {@code ClanJoinRequest}'s is: "approve" is a double-click away, and the second click must not link
 * a node twice or write a second decision over the first.
 *
 * <p>The invariants this record cannot see are the service's: I-K1 (the node is unlinked), I-K2 (the
 * requester is a member), I-K3 (one pending claim per requester and node) and I-K4 (who may decide)
 * all need rows this aggregate does not hold. {@link PersonUserLinker} owns the first of those at
 * the moment it matters, which is approval rather than submission.
 *
 * @param evidence what the requester offers as proof, in their own words; null when they offered
 *     nothing, which is common and is not by itself a reason to refuse
 * @param decidedBy who answered; equal to the requester for a cancellation, because withdrawing is
 *     a decision the requester made
 */
public record NodeClaimRequest(
        UUID id,
        ClanId clanId,
        PersonId personId,
        UserId requesterUserId,
        String evidence,
        ClaimStatus status,
        UserId decidedBy,
        Instant decidedAt,
        String decisionReason,
        Instant createdAt,
        Instant updatedAt) {

    public static final int EVIDENCE_MAX_LENGTH = 2_000;

    public static final int DECISION_REASON_MAX_LENGTH = 1_000;

    public NodeClaimRequest {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(clanId, "clanId must not be null");
        Objects.requireNonNull(personId, "personId must not be null");
        Objects.requireNonNull(requesterUserId, "requesterUserId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        evidence = trimToNull(evidence, EVIDENCE_MAX_LENGTH, "evidence");
        decisionReason = trimToNull(decisionReason, DECISION_REASON_MAX_LENGTH, "decisionReason");

        // Mirrors ck_claim_decision_paired in V11. Everything that leaves PENDING is stamped; only
        // a live claim has no decision time.
        if (status.isPending() != (decidedAt == null)) {
            throw new IllegalArgumentException(
                    "a pending claim has no decision time, and a decided one must have it");
        }
    }

    /** A fresh claim. Everything it cannot check for itself belongs to the service above it. */
    public static NodeClaimRequest submit(
            UUID id,
            ClanId clanId,
            PersonId personId,
            UserId requesterUserId,
            String evidence,
            Instant now) {

        return new NodeClaimRequest(
                id,
                clanId,
                personId,
                requesterUserId,
                evidence,
                ClaimStatus.PENDING,
                null,
                null,
                null,
                now,
                now);
    }

    public boolean isPending() {
        return status.isPending();
    }

    /**
     * Accepted. The link itself is written by {@link PersonUserLinker}, in the same transaction.
     *
     * @throws IllegalStateException the claim has already been decided (I-K5)
     */
    public NodeClaimRequest approve(UserId decider, Instant now) {
        return decided(ClaimStatus.APPROVED, decider, null, now);
    }

    /** Refused, with a reason the requester will read. */
    public NodeClaimRequest reject(UserId decider, String reason, Instant now) {
        return decided(ClaimStatus.REJECTED, decider, reason, now);
    }

    /** Withdrawn by the requester. */
    public NodeClaimRequest cancel(Instant now) {
        return decided(ClaimStatus.CANCELLED, requesterUserId, null, now);
    }

    private NodeClaimRequest decided(
            ClaimStatus newStatus, UserId decider, String reason, Instant now) {

        // I-K5. The one-way door, enforced where it cannot be routed around.
        if (!status.isDecidable()) {
            throw new IllegalStateException("a " + status + " claim cannot become " + newStatus);
        }
        return new NodeClaimRequest(
                id,
                clanId,
                personId,
                requesterUserId,
                evidence,
                newStatus,
                decider,
                now,
                reason,
                createdAt,
                now);
    }

    private static String trimToNull(String value, int maxLength, String field) {
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
