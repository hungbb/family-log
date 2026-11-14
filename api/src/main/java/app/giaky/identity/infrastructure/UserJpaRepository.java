package app.giaky.identity.infrastructure;

import app.giaky.common.types.TenantExempt;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code users}.
 *
 * <p>Type-level {@code @TenantExempt}, and it is not a shortcut: accounts are not clan-scoped and
 * never will be. A user exists before any clan does, belongs to many at once (FR-C8), and the login
 * lookup happens when no clan is even known — the tenancy key would have nothing to hold.
 */
@TenantExempt(
        justification =
                "Accounts are not clan-scoped. A user predates any clan, may belong to several"
                    + " (FR-C8), and is looked up during login when no clan is known. Doc 02 §1.1"
                    + " makes Identity upstream of every tenant.")
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    /** Addresses are stored normalized (I-U1), so plain equality is the whole index lookup. */
    Optional<UserJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    List<UserJpaEntity> findByIdIn(Collection<UUID> ids);

    /**
     * The member-search lookup behind {@code UserDirectory.search}.
     *
     * <p>{@code LIKE} on two columns rather than a full-text index: the primary-key filter narrows
     * the rows to one clan's members first, so the pattern match runs over a clan, not the platform.
     */
    @Query(
            """
            select u.id from UserJpaEntity u
            where u.id in :ids
              and (lower(u.displayName) like lower(concat('%', :query, '%'))
                   or lower(u.email) like lower(concat('%', :query, '%')))
            """)
    List<UUID> searchIdsAmong(@Param("query") String query, @Param("ids") Collection<UUID> ids);

    /**
     * The super-admin user list.
     *
     * <p>Empty strings rather than nulls stand for "no filter": PostgreSQL cannot always infer the
     * type of a parameter that is only ever compared with {@code IS NULL}, and an untyped null
     * inside {@code lower(concat(...))} fails at execution rather than at startup.
     */
    @Query(
            """
            select u from UserJpaEntity u
            where (:status = '' or u.status = :status)
              and (:query = ''
                   or lower(u.displayName) like lower(concat('%', :query, '%'))
                   or lower(u.email) like lower(concat('%', :query, '%')))
            """)
    Page<UserJpaEntity> searchAll(
            @Param("query") String query, @Param("status") String status, Pageable pageable);
}
