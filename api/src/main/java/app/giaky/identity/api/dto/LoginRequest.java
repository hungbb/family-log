package app.giaky.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * @param email deliberately not {@code @Email}-validated: a malformed address must get the same
 *     answer as a wrong password, and a 422 here would tell a caller their input reached a
 *     different code path than a real address does
 * @param password the candidate credential
 */
@Schema(description = "Exchange credentials for a token pair (FR-A3).")
public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
