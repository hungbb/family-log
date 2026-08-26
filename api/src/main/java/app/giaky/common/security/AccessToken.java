package app.giaky.common.security;

import java.time.Duration;
import java.time.Instant;

/**
 * A freshly minted access token and the two facts a caller needs about it.
 *
 * @param value the compact JWT
 * @param expiresAt when it stops verifying
 * @param ttl its lifetime, which the login response reports as {@code expiresIn}
 */
public record AccessToken(String value, Instant expiresAt, Duration ttl) {}
