package app.giaky.authorization.infrastructure;

import app.giaky.authorization.domain.Permission;
import app.giaky.authorization.domain.PermissionGrant;
import app.giaky.authorization.domain.PermissionGrantRepository;
import app.giaky.authorization.domain.PermissionScope;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link PermissionGrantRepository}. */
@Repository
public class PermissionGrantRepositoryAdapter implements PermissionGrantRepository {

    private final PermissionGrantJpaRepository repository;
    private final PermissionGrantMapper mapper;

    public PermissionGrantRepositoryAdapter(
            PermissionGrantJpaRepository repository, PermissionGrantMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<PermissionGrant> findById(ClanId clanId, UUID grantId) {
        return repository.findByIdAndClanId(grantId, clanId).map(mapper::toDomain);
    }

    @Override
    public List<PermissionGrant> findEffectiveFor(ClanId clanId, UserId user, Instant effectiveAt) {
        return repository.findEffectiveFor(clanId, user, effectiveAt).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<PermissionGrant> findAllEffective(ClanId clanId, Instant effectiveAt) {
        return repository.findAllEffective(clanId, effectiveAt).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<PermissionGrant> findEffectiveByPermission(
            ClanId clanId, Permission permission, Instant effectiveAt) {

        return repository.findEffectiveByPermission(clanId, permission.name(), effectiveAt).stream()
                .map(mapper::toDomain)
                .toList();
    }

    /**
     * I-G5, read from the grantee's own live grants rather than with a fifth finder.
     *
     * <p>That list is at most a handful of rows and is already indexed and — during a request —
     * already loaded, so filtering it in memory costs nothing and keeps one fewer query in step with
     * the "not revoked, not expired" rule.
     */
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
    public Optional<PermissionGrant> findLapsedDuplicate(
            ClanId clanId,
            UserId grantee,
            Permission permission,
            PermissionScope scope,
            Instant now) {
        return repository.findLapsedFor(clanId, grantee, now).stream()
                .map(mapper::toDomain)
                .filter(grant -> grant.permission() == permission)
                .filter(grant -> grant.scope().equals(scope))
                .findFirst();
    }

    @Override
    public PermissionGrant save(PermissionGrant grant) {
        return repository
                .findById(grant.id())
                .map(
                        existing -> {
                            // An existing row can only be revoked or have its expiry adjusted;
                            // everything else on a grant is immutable by design and by column.
                            mapper.applyRevocation(existing, grant);
                            return mapper.toDomain(repository.saveAndFlush(existing));
                        })
                .orElseGet(
                        () ->
                                mapper.toDomain(
                                        repository.saveAndFlush(mapper.toEntity(grant))));
    }

    @Override
    public List<PermissionGrant> findLapsedNotYetRevoked(Instant now, int limit) {
        return repository.findLapsedNotYetRevoked(now, Limit.of(limit)).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
