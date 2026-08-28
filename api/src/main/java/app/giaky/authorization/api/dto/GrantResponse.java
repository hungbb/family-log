package app.giaky.authorization.api.dto;

import app.giaky.authorization.application.GrantView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One grant on the permissions screen (doc 05 §4.6).
 *
 * <p>A separate type from {@code GrantView} even though the two agree field for field today, for
 * the reason {@code ClanResponse} gives: the view is what a use case returns, this is the published
 * contract, and doc 05 §6 treats a change to this one as a versioning question.
 */
public record GrantResponse(
        UUID id,
        UUID clanId,
        UUID granteeUserId,
        String granteeName,
        String granteeEmail,
        String permission,
        String scopeType,
        UUID scopeRefId,
        @Schema(description = "The branch's name. Null for a clan-wide grant, which needs no label.")
                String scopeLabel,
        UUID grantedByUserId,
        String grantedByName,
        Instant grantedAt,
        @Schema(description = "Null for a grant that stands until somebody revokes it.")
                Instant expiresAt) {

    public static GrantResponse of(GrantView view) {
        return new GrantResponse(
                view.id(),
                view.clanId(),
                view.granteeUserId(),
                view.granteeName(),
                view.granteeEmail(),
                view.permission(),
                view.scopeType(),
                view.scopeRefId(),
                view.scopeLabel(),
                view.grantedByUserId(),
                view.grantedByName(),
                view.grantedAt(),
                view.expiresAt());
    }
}
