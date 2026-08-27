package app.giaky.clanorg.api.dto;

import app.giaky.clanorg.application.ClanCardView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * {@code GET /clans/lookup} — the card shown before somebody asks to join (doc 05 §4.3).
 *
 * <p>Note what is missing: the clan id, its description, its members. The caller is not a member,
 * and this endpoint answers to anyone with a well-formed code, so it says only enough to let a
 * person recognize their own family.
 */
public record ClanCardResponse(
        String clanCode,
        String name,
        String originPlace,
        long memberCount,
        @Schema(description = "Set when the code was a branch code") UUID targetBranchId,
        String targetBranchName,
        boolean alreadyMember,
        boolean hasPendingRequest) {

    public static ClanCardResponse of(ClanCardView view) {
        return new ClanCardResponse(
                view.clanCode(),
                view.name(),
                view.originPlace(),
                view.memberCount(),
                view.targetBranchId(),
                view.targetBranchName(),
                view.alreadyMember(),
                view.hasPendingRequest());
    }
}
