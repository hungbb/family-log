package app.giaky.clanorg.api.dto;

import app.giaky.clanorg.application.JoinRequestView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A join request (doc 05 §4.5, FR-J5).
 *
 * <p>One shape for the approver inbox and for the requester list on {@code /me}. Fields the reader
 * has no business seeing come back null rather than getting a second type: a requester never
 * receives another member name, and an applicant never receives a branch name from a clan they are
 * not in.
 */
public record JoinRequestResponse(
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
        @Schema(description = "PENDING | APPROVED | REJECTED | CANCELLED | EXPIRED") String status,
        UUID decidedBy,
        Instant decidedAt,
        String decisionReason,
        Instant createdAt,
        Instant expiresAt) {

    public static JoinRequestResponse of(JoinRequestView view) {
        return new JoinRequestResponse(
                view.id(),
                view.clanId(),
                view.clanName(),
                view.clanCode(),
                view.targetBranchId(),
                view.targetBranchName(),
                view.requesterUserId(),
                view.requesterName(),
                view.requesterEmail(),
                view.message(),
                view.status(),
                view.decidedBy(),
                view.decidedAt(),
                view.decisionReason(),
                view.createdAt(),
                view.expiresAt());
    }
}
