package app.giaky.common.security;

import app.giaky.common.types.UserId;
import java.time.Instant;

/**
 * The verified contents of an access token — exactly the claim set doc 05 §5 fixes.
 *
 * <p>No clan, no permissions, by design: a bearer token is a cache, and permissions are the one
 * thing in this product that must never be cached outside a request.
 *
 * @param userId the {@code sub} claim
 * @param email the {@code email} claim
 * @param tokenVersion the {@code tokenVersion} claim, still to be checked against the account
 * @param tokenId the {@code jti} claim
 * @param expiresAt the {@code exp} claim
 */
public record AccessTokenClaims(
        UserId userId, String email, long tokenVersion, String tokenId, Instant expiresAt) {}
