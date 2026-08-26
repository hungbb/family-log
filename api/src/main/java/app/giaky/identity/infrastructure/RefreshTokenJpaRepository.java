package app.giaky.identity.infrastructure;

import app.giaky.common.types.TenantExempt;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code refresh_tokens}.
 *
 * <p>Not clan-scoped, for the same reason as {@code UserJpaRepository}: a session belongs to an
 * account, not to a tenant.
 *
 * <p>The two revocations are bulk statements rather than load-and-save loops. Revoking a chain, or
 * every session an account has, must be one atomic write — a loop leaves a window in which half the
 * sessions of a compromised account are still live.
 */
@TenantExempt(
        justification =
                "Sessions belong to an account, not to a clan. Rotation and revocation happen"
                    + " outside any tenant context.")
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    @Query(
            """
            select t from RefreshTokenJpaEntity t
            where t.userId = :userId
              and t.revokedAt is null
              and t.expiresAt > :now
            order by t.issuedAt desc
            """)
    List<RefreshTokenJpaEntity> findActiveByUser(
            @Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update RefreshTokenJpaEntity t
            set t.revokedAt = :now, t.revokedReason = :reason
            where t.chainId = :chainId and t.revokedAt is null
            """)
    int revokeChain(
            @Param("chainId") UUID chainId,
            @Param("reason") String reason,
            @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update RefreshTokenJpaEntity t
            set t.revokedAt = :now, t.revokedReason = :reason
            where t.userId = :userId and t.revokedAt is null
            """)
    int revokeAllForUser(
            @Param("userId") UUID userId,
            @Param("reason") String reason,
            @Param("now") Instant now);
}
