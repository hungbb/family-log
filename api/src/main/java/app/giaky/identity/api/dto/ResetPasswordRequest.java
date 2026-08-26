package app.giaky.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * @param token the single-use reset token (FR-A6)
 * @param newPassword checked against the policy, which reports its own field errors
 */
@Schema(description = "Set a new password and end every session.")
public record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {}
