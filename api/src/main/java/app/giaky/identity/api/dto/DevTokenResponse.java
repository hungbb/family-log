package app.giaky.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The token a {@code dev}-profile caller asked for.
 *
 * <p>Exists only so a local walkthrough does not require reading the log. The endpoint that returns
 * it is {@code @Profile("dev")} and is asserted absent under {@code prod}.
 *
 * @param token the raw single-use token, or null if none was issued for that address and purpose
 */
@Schema(description = "dev only: the last verification token issued to an address.")
public record DevTokenResponse(String token) {}
