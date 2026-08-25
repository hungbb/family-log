package app.giaky.config;

import app.giaky.common.types.IdGenerator;
import app.giaky.common.types.RandomIdGenerator;
import app.giaky.common.types.SequentialIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Time and identity, as beans rather than static calls.
 *
 * <p>{@code Instant.now()} and {@code UUID.randomUUID()} are never called directly in application
 * code: a test that cannot pin the clock or the id sequence is a test that eventually flakes. See
 * {@code .docs/09-engineering-conventions.md} §4.6.
 */
@Configuration
public class CoreConfig {

    @Bean
    @Profile("!test")
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * A clock frozen at a date well inside the product's world — recent enough that "years ago"
     * arithmetic in fixtures reads naturally, fixed so nothing depends on when the suite runs.
     */
    @Bean
    @Profile("test")
    public Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    }

    @Bean
    @Profile("!test")
    public IdGenerator idGenerator() {
        return new RandomIdGenerator();
    }

    @Bean
    @Profile("test")
    public IdGenerator sequentialIdGenerator() {
        return new SequentialIdGenerator();
    }
}
