package app.giaky.identity.support;

import app.giaky.common.security.SecurityProperties;
import app.giaky.common.types.IdGenerator;
import app.giaky.common.types.SequentialIdGenerator;
import app.giaky.identity.domain.PasswordPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

/**
 * The shared starting point for the identity unit tests.
 *
 * <p>Fixtures rather than factories-with-defaults (doc 09 §4.6): a test that needs a suspended
 * account should say so, and everything it does not mention should be visible here rather than
 * hidden behind a builder.
 */
public final class IdentityTestFixtures {

    /** The same instant {@code CoreConfig} freezes the {@code test} profile at. */
    public static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    public static final String VALID_PASSWORD = "correct-horse-battery";

    private IdentityTestFixtures() {}

    public static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    /** A clock frozen {@code offset} after {@link #NOW}, for testing expiry. */
    public static Clock clockAt(Duration offset) {
        return Clock.fixed(NOW.plus(offset), ZoneOffset.UTC);
    }

    public static IdGenerator idGenerator() {
        return new SequentialIdGenerator();
    }

    /** A policy with one denied entry, so the deny-list branch is real without loading a file. */
    public static PasswordPolicy passwordPolicy() {
        return new PasswordPolicy(Set.of("password123", "qwertyuiop"));
    }

    public static SecurityProperties securityProperties() {
        return new SecurityProperties(
                new SecurityProperties.Jwt(
                        "dGVzdC1vbmx5LXNpZ25pbmcta2V5LWZvci1hdXRvbWF0ZWQtdGVzdHMtMDE=",
                        "giaky-test",
                        Duration.ofMinutes(15)),
                new SecurityProperties.RefreshToken(Duration.ofDays(30)),
                new SecurityProperties.Verification(Duration.ofHours(24), Duration.ofHours(1)),
                12);
    }
}
