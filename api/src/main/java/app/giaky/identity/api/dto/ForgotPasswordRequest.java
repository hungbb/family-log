package app.giaky.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * @param email not {@code @Email}-validated, for the same reason as {@code LoginRequest}: this
 *     endpoint answers {@code 202} to everything, and a 422 for a malformed address would be the
 *     one response that varies with the input
 */
@Schema(description = "Request a password-reset token. Always answers 202.")
public record ForgotPasswordRequest(@NotBlank String email) {}
