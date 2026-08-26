package app.giaky.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** @param token the single-use token from the verification message (FR-A6) */
@Schema(description = "Confirm an email address.")
public record VerifyEmailRequest(@NotBlank String token) {}
