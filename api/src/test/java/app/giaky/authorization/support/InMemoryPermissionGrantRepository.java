package app.giaky.authorization.support;

import app.giaky.authorization.domain.Permission;
import app.giaky.authorization.domain.PermissionGrant;
import app.giaky.authorization.domain.PermissionGrantRepository;
import app.giaky.authorization.domain.PermissionScope;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A {@link PermissionGrantRepository} backed by a map.
 *
 * <p>Enforces I-G5 the way {@code ux_grant_active} does — a second live grant of the same
 * (person, permission, scope) is refused rather than quietly stored — because the tests that matter
 * here are about what happens when something tries to create one.
 *
 * <p>The "not revoked and not expired" filter is duplicated from the SQL on purpose. If the two ever
 * disagree, the unit tests are green against a world the database will not produce, which is the
 * failure mode an in-memory double exists to avoid; {@code AuthorizationIsolationIT} runs the same
 * scenarios against real PostgreSQL to keep them honest.
 */
public final class InMemoryPermissionGrantRepository implements PermissionGrantRepository {

    private final Map<UUID, PermissionGrant> byId = new LinkedHashMap<>();

    @Override
    public Optional<PermissionGrant> findById(ClanId clanId, UUID grantId) {
        return Optional.ofNullable(byId.get(grantId))
                .filter(grant -> grant.clanId().equals(clanId));
    }

    @Override
    public List<PermissionGrant> findEffectiveFor(ClanId clanId, UserId user, Instant effectiveAt) {
        return byId.values().stream()
                .filter(grant -> grant.clanId().equals(clanId))
                .filter(grant -> grant.granteeUserId().equals(user))
                .filter(grant -> grant.isEffectiveAt(effectiveAt))
                .sorted(Comparator.comparing(PermissionGrant::grantedAt))
                .toList();
    }

    @Override
    public List<PermissionGrant> findAllEffective(ClanId clanId, Instant effectiveAt) {
        return byId.values().stream()
                .filter(grant -> grant.clanId().equals(clanId))
                .filter(grant -> grant.isEffectiveAt(effectiveAt))
                .sorted(Comparator.comparing(PermissionGrant::grantedAt).reversed())
                .toList();
    }

    @Override
    public List<PermissionGrant> findEffectiveByPermission(
            ClanId clanId, Permission permission, Instant effectiveAt) {

        return findAllEffective(clanId, effectiveAt).stream()
                .filter(grant -> grant.permission() == permission)
                .toList();
    }

    @Override
    public Optional<PermissionGrant> findEffectiveDuplicate(
            ClanId clanId,
            UserId grantee,
            Permission permission,
            PermissionScope scope,
            Instant effectiveAt) {

        return findEffectiveFor(clanId, grantee, effectiveAt).stream()
                .filter(grant -> grant.permission() == permission)
                .filter(grant -> grant.scope().equals(scope))
                .findFirst();
    }

    @Override
    public PermissionGrant save(PermissionGrant grant) {
        if (!grant.isRevoked()) {
            // ux_grant_active, in a map. Without it a test could create the state the index exists
            // to make impossible and then assert against a world that cannot happen.
            byId.values().stream()
                    .filter(existing -> !existing.id().equals(grant.id()))
                    .filter(existing -> !existing.isRevoked())
                    .filter(existing -> existing.clanId().equals(grant.clanId()))
                    .filter(existing -> existing.granteeUserId().equals(grant.granteeUserId()))
                    .filter(existing -> existing.permission() == grant.permission())
                    .filter(existing -> existing.scope().equals(grant.scope()))
                    .findAny()
                    .ifPresent(
                            existing -> {
                                throw new IllegalStateException(
                                        "I-G5: "
                                                + grant.granteeUserId()
                                                + " already holds "
                                                + grant.permission()
                                                + " at "
                                                + grant.scope());
                            });
        }
        byId.put(grant.id(), grant);
        return grant;
    }

    @Override
    public List<PermissionGrant> findLapsedNotYetRevoked(Instant now, int limit) {
        return byId.values().stream()
                .filter(grant -> !grant.isRevoked())
                .filter(grant -> grant.hasExpiredBy(now))
                .sorted(Comparator.comparing(PermissionGrant::expiresAt))
                .limit(limit)
                .toList();
    }

    /** Including revoked ones: revocation must keep the row rather than delete it. */
    public List<PermissionGrant> all() {
        return List.copyOf(byId.values());
    }
}
