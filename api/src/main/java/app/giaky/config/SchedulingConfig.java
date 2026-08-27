package app.giaky.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled}.
 *
 * <p>Off under {@code test}: a background thread writing rows while integration tests assert on them
 * is a source of failures that reproduce once a fortnight. Every scheduled job is written so its
 * work is a plain method a test can call.
 *
 * <p>Jobs run on virtual threads along with everything else ({@code spring.threads.virtual.enabled}),
 * so a sweep that blocks on JDBC costs no platform thread.
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {}
