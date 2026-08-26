package app.giaky.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param email normalized server-side; uniqueness is checked against the normalized form (I-U1)
 * @param password checked against the policy, which reports its own field errors; deliberately
 *     carries no {@code @Size} here, so the one authority on what a password may be is
 *     {@code PasswordPolicy}
 * @param displayName what other members see
 * @param locale optional; defaults to Vietnamese
 */
@Schema(description = "Create an account (FR-A1).")
public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank String password,
        @NotBlank @Size(max = 150) String displayName,
        @Pattern(regexp = LocalePattern.REGEX, message = LocalePattern.MESSAGE) String locale) {}
