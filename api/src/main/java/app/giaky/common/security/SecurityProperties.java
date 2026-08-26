package app.giaky.common.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Everything tunable about authentication, in one validated place.
 *
 * <p>The values are deliberately not scattered across {@code @Value} annotations: a token lifetime
 * that lives next to the code that mints it is a lifetime nobody can audit. The constraints are the
 * point — a deployment with a 16-byte JWT secret or a 30-day access token should fail at startup,
 * not quietly ship.
 *
 * @param jwt access-token signing and lifetime
 * @param refreshToken refresh-token lifetime
 * @param verification email-verification and password-reset token lifetimes
 * @param bcryptCost BCrypt work factor; ≥ 12 per FR-A2
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @NotNull Jwt jwt,
        @NotNull RefreshToken refreshToken,
        @NotNull Verification verification,
        @Min(12) @Max(16) int bcryptCost) {

    /**
     * @param secret base64-encoded HMAC key, at least 32 bytes decoded (HS256 needs 256 bits)
     * @param issuer the {@code iss} claim; tokens from anywhere else are rejected
     * @param accessTokenTtl 15 minutes in production (doc 05 §5); longer in {@code dev} only
     */
    public record Jwt(
            @NotBlank String secret, @NotBlank String issuer, @NotNull Duration accessTokenTtl) {}

    /** @param ttl 30 days (doc 03 §3.2). Rotation means a live session refreshes long before this. */
    public record RefreshToken(@NotNull Duration ttl) {}

    /**
     * @param emailVerifyTtl 24 hours (FR-A6)
     * @param passwordResetTtl 1 hour (FR-A6) — shorter, because it grants account takeover
     */
    public record Verification(
            @NotNull Duration emailVerifyTtl, @NotNull Duration passwordResetTtl) {}
}
