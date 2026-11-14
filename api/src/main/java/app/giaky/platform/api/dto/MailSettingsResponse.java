package app.giaky.platform.api.dto;

import app.giaky.platform.application.MailSettingsView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** The mail settings, without the password. */
@Schema(description = "The outgoing mail transport. The SMTP password is write-only.")
public record MailSettingsResponse(
        boolean enabled,
        String host,
        Integer port,
        String username,
        @Schema(description = "Whether an SMTP password is stored. Its value is never returned.")
                boolean loginStored,
        @Schema(allowableValues = {"NONE", "STARTTLS", "SSL"}) String security,
        String fromAddress,
        String fromName,
        Instant updatedAt) {

    public static MailSettingsResponse of(MailSettingsView view) {
        return new MailSettingsResponse(
                view.enabled(),
                view.host(),
                view.port(),
                view.username(),
                view.loginStored(),
                view.security().name(),
                view.fromAddress(),
                view.fromName(),
                view.updatedAt());
    }
}
