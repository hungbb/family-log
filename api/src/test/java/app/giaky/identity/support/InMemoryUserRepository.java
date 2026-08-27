package app.giaky.identity.support;

import app.giaky.common.types.UserId;
import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.User;
import app.giaky.identity.domain.UserRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    public List<User> findAllById(Collection<UserId> ids) {
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    @Override
    public List<UserId> searchIds(String query, int limit) {
        String needle = query.toLowerCase(Locale.ROOT);
        return byId.values().stream()
                .filter(
                        user ->
                                user.displayName().toLowerCase(Locale.ROOT).contains(needle)
                                        || user.email().value().contains(needle))
                .map(User::id)
                .limit(limit)
                .toList();
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
