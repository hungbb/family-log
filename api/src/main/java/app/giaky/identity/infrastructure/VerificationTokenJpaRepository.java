package app.giaky.identity.infrastructure;

import app.giaky.common.types.TenantExempt;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code verification_tokens}.
 *
 * <p>Not clan-scoped: verifying an address and resetting a password both happen before, and
 * independently of, any clan membership.
 */
@TenantExempt(
        justification =
                "Verification and reset tokens act on an account, not on a clan, and are used"
                    + " before any membership exists.")
public interface VerificationTokenJpaRepository
        extends JpaRepository<VerificationTokenJpaEntity, UUID> {

    Optional<VerificationTokenJpaEntity> findByTokenHash(String tokenHash);

    /** Spends every live token of one purpose, so issuing a new one retires the old (see the service). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update VerificationTokenJpaEntity t
            set t.consumedAt = :now
            where t.userId = :userId and t.purpose = :purpose and t.consumedAt is null
            """)
    int consumeOutstanding(
            @Param("userId") UUID userId,
            @Param("purpose") String purpose,
            @Param("now") Instant now);
}
