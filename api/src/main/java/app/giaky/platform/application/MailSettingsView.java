package app.giaky.platform.application;

import app.giaky.platform.domain.MailSecurity;
import app.giaky.platform.domain.MailSettings;
import java.time.Instant;

/**
 * The mail settings as a super admin may read them back: everything except the password.
 *
 * @param loginStored whether an SMTP password is stored. All anyone needs to know about it; the
 *     value itself is write-only
 */
public record MailSettingsView(
        boolean enabled,
        String host,
        Integer port,
        String username,
        boolean loginStored,
        MailSecurity security,
        String fromAddress,
        String fromName,
        Instant updatedAt) {

    static MailSettingsView of(MailSettings settings) {
        return new MailSettingsView(
                settings.enabled(),
                settings.host(),
                settings.port(),
                settings.username(),
                settings.hasPassword(),
                settings.security(),
                settings.fromAddress(),
                settings.fromName(),
                settings.updatedAt());
    }
}
