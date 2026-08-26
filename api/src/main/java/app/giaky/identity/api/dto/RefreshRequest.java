package app.giaky.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** @param refreshToken the opaque token from the previous pair; it is spent by this call */
@Schema(description = "Exchange a refresh token for a new pair. Rotates; reuse revokes the chain.")
public record RefreshRequest(@NotBlank String refreshToken) {}
