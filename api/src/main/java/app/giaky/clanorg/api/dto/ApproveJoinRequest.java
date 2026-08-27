package app.giaky.clanorg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * {@code POST /clans/{clanId}/join-requests/{id}/approve} (FR-J3).
 *
 * <p>The body is optional in full: omitting it accepts the branch the requester asked for, which is
 * what the approver usually means when they click through without changing anything.
 */
public record ApproveJoinRequest(
        @Schema(
                        description =
                                "Where to place the new member. Defaults to the branch they asked"
                                    + " for, and then to no branch at all.")
                UUID homeBranchId) {}
