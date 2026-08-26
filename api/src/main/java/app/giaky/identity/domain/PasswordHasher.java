package app.giaky.identity.domain;

/**
 * Turns a password into a {@link PasswordHash}, and checks one against a hash.
 *
 * <p>A port, so the domain can talk about credentials without importing a crypto library, and so
 * the work factor can be raised — or the algorithm replaced — in one adapter.
 *
 * <p>Implementations must compare in constant time. A verification whose duration depends on how
 * many leading characters matched is a side channel, and BCrypt's own comparison already handles
 * it; anything hand-rolled here would not.
 */
public interface PasswordHasher {

    /** Hashes a raw password. The result is safe to store; the input must never be. */
    PasswordHash hash(String rawPassword);

    /** Whether {@code rawPassword} produced {@code hash}. False for any malformed hash. */
    boolean matches(String rawPassword, PasswordHash hash);
}
