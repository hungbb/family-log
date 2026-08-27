package app.giaky.identity.infrastructure;

import app.giaky.common.types.TenantExempt;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
     * <p>{@code LIKE} on two columns rather than a full-text index: the result is intersected with
     * one clan's memberships, so the query runs against a table whose growth is bounded by
     * registrations rather than by tree size, and the {@code Pageable} caps it regardless.
     */
    @Query(
            """
            select u.id from UserJpaEntity u
            where lower(u.displayName) like lower(concat('%', :query, '%'))
               or lower(u.email) like lower(concat('%', :query, '%'))
            order by u.displayName asc
            """)
    List<UUID> searchIds(@Param("query") String query, Pageable pageable);
}
