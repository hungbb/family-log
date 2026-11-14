package app.giaky.platform.domain;

/**
 * Storage for the one {@link MailSettings}.
 *
 * <p>No {@code Optional}: the row is created by the migration, so "there are no settings" is not a
 * state any reader has to handle.
 */
public interface MailSettingsRepository {

    MailSettings load();

    /** Replaces the stored settings, returning what was stored. */
    MailSettings save(MailSettings settings);
}
