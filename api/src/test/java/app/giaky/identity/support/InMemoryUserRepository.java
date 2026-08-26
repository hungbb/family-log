package app.giaky.identity.support;

import app.giaky.common.types.UserId;
import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.User;
import app.giaky.identity.domain.UserRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link UserRepository} backed by a map.
 *
 * <p>This is why the port exists. Every use case in {@code identity.application} can be driven
 * against real domain objects with no Spring context and no database, which is what makes the
 * authentication tests fast enough to run on every save and precise enough to assert on state
 * rather than on SQL.
 */
public final class InMemoryUserRepository implements UserRepository {

    private final Map<UserId, User> byId = new LinkedHashMap<>();

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<User> findByEmail(EmailAddress email) {
        return byId.values().stream().filter(user -> user.email().equals(email)).findFirst();
    }

    @Override
    public boolean existsByEmail(EmailAddress email) {
        return findByEmail(email).isPresent();
    }

    @Override
    public User save(User user) {
        byId.put(user.id(), user);
        return user;
    }

    /** How many accounts exist. Lets a test assert that a failed registration created nothing. */
    public int count() {
        return byId.size();
    }
}
