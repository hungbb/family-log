package app.giaky.linking.api.dto;

import app.giaky.linking.application.NodeClaimView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A claim (doc 05 section 4.10).
 *
 * <p>One shape for the approver inbox and for the requester list on {@code /me}. Fields a
 * particular reader has no business seeing come back null rather than getting a second type: a
 * requester never receives another member address, and only an approver receives the tree context.
 */
public record NodeClaimResponse(
        UUID id,
        UUID clanId,
        String clanName,
        UUID personId,
        String personName,
        @Schema(description = "doi; null when unknown") Integer personGeneration,
        @Schema(description = "The node parents, so an approver can verify the claim (F6.5).")
                List<String> parentNames,
        List<String> childNames,
        UUID requesterUserId,
        String requesterName,
        String requesterEmail,
        String evidence,
        @Schema(description = "PENDING | APPROVED | REJECTED | CANCELLED") String status,
        UUID decidedBy,
        String decidedByName,
        Instant decidedAt,
        String decisionReason,
        Instant createdAt) {

    public static NodeClaimResponse of(NodeClaimView view) {
        return new NodeClaimResponse(
                view.id(),
                view.clanId(),
                view.clanName(),
                view.personId(),
                view.personName(),
                view.personGeneration(),
                view.parentNames(),
                view.childNames(),
                view.requesterUserId(),
                view.requesterName(),
                view.requesterEmail(),
                view.evidence(),
                view.status(),
                view.decidedBy(),
                view.decidedByName(),
                view.decidedAt(),
                view.decisionReason(),
                view.createdAt());
    }
}
