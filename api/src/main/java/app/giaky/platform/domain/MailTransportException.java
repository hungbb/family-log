package app.giaky.platform.domain;

/**
 * The SMTP server refused a message or could not be reached.
 *
 * <p>The message is meant for a super admin diagnosing their own configuration ("authentication
 * failed", "connection refused") and never contains the password.
 */
public class MailTransportException extends RuntimeException {

    public MailTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
