package app.giaky.authorization.infrastructure;

import app.giaky.authorization.domain.Permission;
import app.giaky.authorization.domain.PermissionGrant;
import app.giaky.authorization.domain.PermissionScope;
import app.giaky.authorization.domain.ScopeType;
import org.springframework.stereotype.Component;

/**
 * {@link PermissionGrant} ↔ {@link PermissionGrantJpaEntity}.
 *
 * <p>Hand-written rather than MapStruct, unlike its neighbours in ClanOrg. The reason is the scope:
 * two columns on the row become one {@link PermissionScope} in the domain, and that pairing carries
 * I-G1 — a mapping generated field-by-field would either need a hand-written pair of helpers anyway
 * or would quietly construct a scope the compact constructor was written to forbid. Twenty explicit
 * lines are worth more here than a saved annotation.
 */
@Component
public class PermissionGrantMapper {

    PermissionGrantJpaEntity toEntity(PermissionGrant grant) {
        return new PermissionGrantJpaEntity(
                grant.id(),
                grant.clanId(),
                grant.granteeUserId(),
                grant.permission().name(),
                grant.scope().type().name(),
                grant.scope().refId(),
                grant.grantedBy(),
                grant.grantedAt(),
                grant.expiresAt(),
                grant.revokedAt(),
                grant.revokedBy());
    }

    PermissionGrant toDomain(PermissionGrantJpaEntity entity) {
        return new PermissionGrant(
                entity.getId(),
                entity.getClanId(),
                entity.getGranteeUserId(),
                Permission.valueOf(entity.getPermission()),
                PermissionScope.of(
                        ScopeType.valueOf(entity.getScopeType()), entity.getScopeRefId()),
                entity.getGrantedBy(),
                entity.getGrantedAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getRevokedBy());
    }

    /**
     * Applies a revocation to a row already loaded in this transaction.
     *
     * <p>The alternative — building a fresh entity with the same id and letting {@code save} merge
     * it — works, and would also silently resurrect a row somebody else deleted. This mutates the
     * managed instance, so the write is an {@code UPDATE} of exactly two columns.
     */
    void applyRevocation(PermissionGrantJpaEntity entity, PermissionGrant grant) {
        entity.setRevokedAt(grant.revokedAt());
        entity.setRevokedBy(grant.revokedBy());
        entity.setExpiresAt(grant.expiresAt());
    }
}
