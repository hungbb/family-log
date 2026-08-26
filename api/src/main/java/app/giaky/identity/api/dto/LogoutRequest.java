package app.giaky.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** @param refreshToken the session to end; an unrecognised value still succeeds */
@Schema(description = "End one session (FR-A5).")
public record LogoutRequest(@NotBlank String refreshToken) {}
