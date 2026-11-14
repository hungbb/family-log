package app.giaky.platform.application;

import java.util.Objects;

/**
 * A plain-text message another context wants sent. The sender comes from the mail settings.
 *
 * @param to one recipient address
 * @param subject the subject line
 * @param text the body, plain text
 */
public record OutgoingMail(String to, String subject, String text) {

    public OutgoingMail {
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(text, "text must not be null");
    }
}
