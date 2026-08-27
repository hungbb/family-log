package app.giaky.clanorg.application;

import java.time.Instant;
import java.util.UUID;

/**
 * A join request, from either end (doc 05 §4.5, FR-J5).
 *
 * <p>Carries the clan's identity and the requester's, because the two readers need opposite halves:
 * an approver inbox shows who is asking, and the list on {@code /me} shows which family was asked.
 * One shape serves both rather than two that drift.
 *
 * <p>{@code requesterEmail} is populated only for an approver. On the requester's own list it would
 * be their own address and is left null; nobody without an active membership in the clan ever sees
 * it.
 */
public record JoinRequestView(
        UUID id,
        UUID clanId,
        String clanName,
        String clanCode,
        UUID targetBranchId,
        String targetBranchName,
        UUID requesterUserId,
        String requesterName,
        String requesterEmail,
        String message,
        String status,
        UUID decidedBy,
        Instant decidedAt,
        String decisionReason,
        Instant createdAt,
        Instant expiresAt) {}
