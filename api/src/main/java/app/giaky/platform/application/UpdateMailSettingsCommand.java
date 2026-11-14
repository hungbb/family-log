package app.giaky.platform.application;

import app.giaky.platform.domain.MailSecurity;

/**
 * A complete replacement of the mail settings.
 *
 * @param password three meanings. {@code null} keeps the stored password, so the form can be saved
 *     without asking for it again. An empty string removes it. Anything else replaces it
 */
public record UpdateMailSettingsCommand(
        boolean enabled,
        String host,
        Integer port,
        String username,
        String password,
        MailSecurity security,
        String fromAddress,
        String fromName) {}
