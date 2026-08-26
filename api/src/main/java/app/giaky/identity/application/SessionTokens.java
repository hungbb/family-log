package app.giaky.identity.application;

import java.time.Duration;

/**
 * The result of authenticating: a short-lived access token, a long-lived refresh token, and who
 * the caller now is (FR-A3).
 *
 * <p>The refresh token appears here and in the response body, and nowhere else — not in a log, not
 * in the database, which holds only its hash.
 *
 * @param accessToken the compact JWT, 15 minutes
 * @param refreshToken the opaque rotation credential, 30 days
 * @param accessTokenTtl reported to the client as {@code expiresIn}, so it can refresh ahead of
 *     expiry rather than after a failed request
 * @param user the signed-in account, so a login costs one round trip rather than two
 */
public record SessionTokens(
        String accessToken, String refreshToken, Duration accessTokenTtl, UserProfile user) {}
