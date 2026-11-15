package app.giaky.platform.domain;

/**
 * Hands one message to an SMTP server.
 *
 * <p>A port so the use cases can be tested without a mail server, and so nothing outside {@code
 * infrastructure} knows that Jakarta Mail exists.
 */
public interface MailTransport {

    /**
     * Sends synchronously.
     *
     * @throws MailTransportException the server refused the message or could not be reached
     */
    void send(SmtpConnection connection, MailMessage message);

    /**
     * Where to connect, with the password already decrypted.
     *
     * <p>{@code toString} is overridden: a record's default would print the password into any log
     * line or exception message the connection ever reached.
     */
    record SmtpConnection(
            String host, int port, String username, String password, MailSecurity security) {

        @Override
        public String toString() {
            return "SmtpConnection[" + host + ":" + port + ", " + security + "]";
        }
    }

    /** One plain-text message. */
    record MailMessage(
            String fromAddress, String fromName, String to, String subject, String text) {}
}
