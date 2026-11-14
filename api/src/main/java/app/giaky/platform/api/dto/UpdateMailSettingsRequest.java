package app.giaky.platform.api.dto;

import app.giaky.platform.domain.MailSecurity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A complete replacement of the mail settings.
 *
 * <p>Completeness when {@code enabled} is checked by the service rather than here, because it
 * depends on several fields at once and on what is already stored (the password).
 *
 * @param password null keeps the stored password; an empty string removes it; anything else
 *     replaces it
 */
@Schema(description = "Replace the outgoing mail settings.")
public record UpdateMailSettingsRequest(
        boolean enabled,
        @Size(max = 255) String host,
        Integer port,
        @Size(max = 320) String username,
        @Schema(
                        description =
                                "Omit or null to keep the stored password; empty string to remove it.",
                        accessMode = Schema.AccessMode.WRITE_ONLY)
                @Size(max = 500)
                String password,
        @NotNull MailSecurity security,
        @Size(max = 320) String fromAddress,
        @Size(max = 150) String fromName) {}
