package app.giaky.linking.api.dto;

import app.giaky.linking.domain.NodeClaimRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /clans/{clanId}/claims/{id}/reject} (FR-N2).
 *
 * <p>The reason is required, for the reason a rejected join request needs one: the person being
 * refused is a relative, and a bare no is one they will simply send again.
 */
public record RejectClaimRequest(
        @NotBlank @Size(max = NodeClaimRequest.DECISION_REASON_MAX_LENGTH) String reason) {}
