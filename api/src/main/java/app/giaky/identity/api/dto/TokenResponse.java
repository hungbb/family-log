package app.giaky.identity.api.dto;

import app.giaky.identity.application.SessionTokens;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Doc 05 §4.1: {@code { accessToken, refreshToken, expiresIn, user }}.
 *
 * <p>{@code expiresIn} is seconds, as every OAuth-shaped client expects, and is what lets the web
 * client refresh <i>before</i> a request fails rather than after.
 */
@Schema(description = "A token pair and the account it belongs to.")
public record TokenResponse(
        String accessToken, String refreshToken, long expiresIn, UserSummary user) {

    public static TokenResponse of(SessionTokens tokens) {
        return new TokenResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.accessTokenTtl().toSeconds(),
                UserSummary.of(tokens.user()));
    }
}
