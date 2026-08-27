package app.giaky.clanorg.api.dto;

import app.giaky.clanorg.domain.ClanJoinRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /join-requests} (FR-J1).
 *
 * <p>Exactly one of the two codes. Both shapes are identical, and somebody pasting a code from a
 * message has no way of knowing which kind they were given — so the client sends whichever field it
 * knows and the server resolves it either way.
 */
public record SubmitJoinRequest(
        @Schema(example = "NGUYEN-7F3K") @Size(max = 32) String clanCode,
        @Schema(example = "CHIHAI-9Q2M") @Size(max = 32) String branchCode,
        @Schema(description = "What the requester wants the approver to know about them")
                @Size(max = ClanJoinRequest.MESSAGE_MAX_LENGTH)
                String message) {}
