package app.giaky.identity.domain;

import app.giaky.common.types.UserId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Storage for {@link User} aggregates.
 *
 * <p>A port in the domain, implemented in {@code infrastructure} — the application layer talks to
 * this and never to Spring Data, which is what lets every use case here be tested with a map.
 *
 * <p>No {@code ClanId} anywhere, and none is coming: accounts exist before any clan does, and login
 * has no tenant to scope to. The architecture test's tenancy rule is satisfied by the adapter's
 * {@code @TenantExempt} justification rather than by pretending there is a clan.
 */
public interface UserRepository {

    Optional<User> findById(UserId id);

    /** The login lookup. The address is already normalized by {@link EmailAddress}. */
    Optional<User> findByEmail(EmailAddress email);

    boolean existsByEmail(EmailAddress email);

    /**
     * Several accounts at once, for the contexts that hold ids and need names (doc 02 §7 rule 1).
     *
     * <p>Reached only through {@code UserDirectory}. The alternative — every context joining {@code
     * users} in its own query — is how a bounded context stops being one.
     */
    List<User> findAllById(Collection<UserId> ids);

    /**
     * Ids of accounts whose display name or address contains {@code query}, case-insensitively.
     *
     * <p>Returns ids rather than users because the caller intersects them with something else (a
     * clan's memberships) before it needs anything else about them.
     *
     * @param limit hard cap on the result; a one-character query must not read the table
     */
    List<UserId> searchIds(String query, int limit);

    /** Inserts or updates, returning the stored state. */
    User save(User user);
}
