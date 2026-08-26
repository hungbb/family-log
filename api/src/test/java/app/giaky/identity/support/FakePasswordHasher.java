package app.giaky.identity.support;

import app.giaky.identity.domain.PasswordHash;
import app.giaky.identity.domain.PasswordHasher;

/**
 * A {@link PasswordHasher} that prefixes instead of hashing.
 *
 * <p>Not a weaker BCrypt — a different thing entirely, and only ever used to test the code
 * <i>around</i> hashing. Real BCrypt at cost 12 costs about a quarter-second per call, and the
 * login tests make several per case; paying that to re-verify a library's arithmetic would make
 * the suite slow without testing anything of ours. {@code BCryptPasswordHasher} is exercised where
 * it belongs, in the integration tests.
 */
public final class FakePasswordHasher implements PasswordHasher {

    private static final String PREFIX = "fake-hash:";

    private int hashCalls;
    private int matchCalls;

    @Override
    public PasswordHash hash(String rawPassword) {
        hashCalls++;
        return PasswordHash.of(PREFIX + rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash hash) {
        matchCalls++;
        return rawPassword != null && hash != null && hash.value().equals(PREFIX + rawPassword);
    }

    /** How often a verification was attempted — the timing-decoy assertion depends on it. */
    public int matchCalls() {
        return matchCalls;
    }

    public int hashCalls() {
        return hashCalls;
    }
}
