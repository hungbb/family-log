package app.giaky.authorization.application;

import app.giaky.authorization.domain.PermissionGrant;
import java.time.Instant;
import java.util.UUID;

/**
 * One grant, as the permissions screen shows it (doc 05 §4.6).
 *
 * <p>Carries the display names for the grantee, the granter and the branch alongside their ids.
 * "TREE_EDIT / BRANCH / 8f0e…" is a row only a developer can read; a family arguing about who may
 * change what needs "Nguyễn Văn An can edit Chi Hai, granted by Nguyễn Thị Bích".
 *
 * @param scopeLabel the branch's name, or null for a clan-wide grant, which needs no label
 * @param expiresAt null for a grant that stands until somebody revokes it
 */
public record GrantView(
        UUID id,
        UUID clanId,
        UUID granteeUserId,
        String granteeName,
        String granteeEmail,
        String permission,
        String scopeType,
        UUID scopeRefId,
        String scopeLabel,
        UUID grantedByUserId,
        String grantedByName,
        Instant grantedAt,
        Instant expiresAt) {

    static GrantView of(
            PermissionGrant grant,
            String granteeName,
            String granteeEmail,
            String grantedByName,
            String scopeLabel) {

        return new GrantView(
                grant.id(),
                grant.clanId().value(),
                grant.granteeUserId().value(),
                granteeName,
                granteeEmail,
                grant.permission().name(),
                grant.scope().type().name(),
                grant.scope().refId(),
                scopeLabel,
                grant.grantedBy() == null ? null : grant.grantedBy().value(),
                grantedByName,
                grant.grantedAt(),
                grant.expiresAt());
    }
}
