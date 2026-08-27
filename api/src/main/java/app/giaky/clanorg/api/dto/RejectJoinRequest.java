package app.giaky.clanorg.api.dto;

import app.giaky.clanorg.domain.ClanJoinRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /clans/{clanId}/join-requests/{id}/reject} (FR-J3).
 *
 * <p>The reason is required. A refusal with no explanation is one the requester will simply send
 * again, and the person it is about is a relative.
 */
public record RejectJoinRequest(
        @NotBlank @Size(max = ClanJoinRequest.DECISION_REASON_MAX_LENGTH) String reason) {}
