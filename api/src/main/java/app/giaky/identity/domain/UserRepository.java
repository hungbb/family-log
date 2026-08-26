package app.giaky.identity.domain;

import app.giaky.common.types.UserId;
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

    /** Inserts or updates, returning the stored state. */
    User save(User user);
}
