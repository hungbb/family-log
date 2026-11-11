package app.giaky.authorization.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.TenantExempt;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code permission_grants}.
 *
 * <p>Every finder spells out "not revoked and not expired" rather than leaving it to the caller.
 * A read that forgot one of the two halves would hand out access somebody has already taken back,
 * which is the single worst bug this table can have — so the filter lives where it cannot be
 * omitted.
 */
public interface PermissionGrantJpaRepository extends JpaRepository<PermissionGrantJpaEntity, UUID> {

    Optional<PermissionGrantJpaEntity> findByIdAndClanId(UUID id, ClanId clanId);

    /** The hot path, behind {@code ix_grant_lookup}. */
    @Query(
            """
            select g from PermissionGrantJpaEntity g
            where g.clanId = :clanId
              and g.granteeUserId = :userId
              and g.revokedAt is null
              and (g.expiresAt is null or g.expiresAt > :now)
            order by g.grantedAt
            """)
    List<PermissionGrantJpaEntity> findEffectiveFor(
            @Param("clanId") ClanId clanId,
            @Param("userId") UserId userId,
            @Param("now") Instant now);

    @Query(
            """
            select g from PermissionGrantJpaEntity g
            where g.clanId = :clanId
              and g.revokedAt is null
              and (g.expiresAt is null or g.expiresAt > :now)
            order by g.grantedAt desc
            """)
    List<PermissionGrantJpaEntity> findAllEffective(
            @Param("clanId") ClanId clanId, @Param("now") Instant now);

    @Query(
            """
            select g from PermissionGrantJpaEntity g
            where g.clanId = :clanId
              and g.permission = :permission
              and g.revokedAt is null
              and (g.expiresAt is null or g.expiresAt > :now)
            """)
    List<PermissionGrantJpaEntity> findEffectiveByPermission(
            @Param("clanId") ClanId clanId,
            @Param("permission") String permission,
            @Param("now") Instant now);

    /** One member's lapsed, not-yet-swept grants in one clan — what a renewal has to close first. */
    @Query(
            """
            select g from PermissionGrantJpaEntity g
            where g.clanId = :clanId
              and g.granteeUserId = :userId
              and g.revokedAt is null
              and g.expiresAt is not null
              and g.expiresAt <= :now
            """)
    List<PermissionGrantJpaEntity> findLapsedFor(
            @Param("clanId") ClanId clanId,
            @Param("userId") UserId userId,
            @Param("now") Instant now);

    /**
     * Grants that have lapsed and are not yet marked revoked — the sweep's input (B3.14).
     *
     * <p>Bounded by {@code limit} so a long-neglected deployment cannot stall the job on its first
     * run.
     */
    @TenantExempt(
            justification =
                    "The expiry sweep runs across every tenant and has no clan to be given. It"
                        + " grants nothing: it marks rows whose own expires_at has already passed,"
                        + " and every read path filters on that column regardless, so the sweep"
                        + " changes tidiness rather than access.")
    @Query(
            """
            select g from PermissionGrantJpaEntity g
            where g.revokedAt is null
              and g.expiresAt is not null
              and g.expiresAt <= :now
            order by g.expiresAt
            """)
    List<PermissionGrantJpaEntity> findLapsedNotYetRevoked(
            @Param("now") Instant now, Limit limit);
}
