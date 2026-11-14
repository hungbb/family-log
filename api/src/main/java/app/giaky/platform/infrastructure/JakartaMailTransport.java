package app.giaky.platform.infrastructure;

import app.giaky.platform.domain.MailSecurity;
import app.giaky.platform.domain.MailTransport;
import app.giaky.platform.domain.MailTransportException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * SMTP through Jakarta Mail.
 *
 * <p>A fresh {@code JavaMailSenderImpl} per message rather than one bean, because the settings
 * change at runtime and a cached sender would keep using the old ones until a restart. Building one
 * costs a {@code Properties} object; the connection it opens dominates.
 */
@Component
public class JakartaMailTransport implements MailTransport {

    /** Long enough for a slow provider, short enough that a wrong host fails while someone waits. */
    private static final String TIMEOUT_MILLIS = "15000";

    @Override
    public void send(SmtpConnection connection, MailMessage message) {
        JavaMailSenderImpl sender = senderFor(connection);
        try {
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, StandardCharsets.UTF_8.name());
            if (message.fromName() == null) {
                helper.setFrom(message.fromAddress());
            } else {
                helper.setFrom(message.fromAddress(), message.fromName());
            }
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), false);
            sender.send(mime);
        } catch (MailException | MessagingException | java.io.UnsupportedEncodingException e) {
            throw new MailTransportException(describe(e), e);
        }
    }

    private static JavaMailSenderImpl senderFor(SmtpConnection connection) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(connection.host());
        sender.setPort(connection.port());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        boolean authenticates = connection.username() != null;
        if (authenticates) {
            sender.setUsername(connection.username());
            sender.setPassword(connection.password());
        }

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", String.valueOf(authenticates));
        props.put("mail.smtp.connectiontimeout", TIMEOUT_MILLIS);
        props.put("mail.smtp.timeout", TIMEOUT_MILLIS);
        props.put("mail.smtp.writetimeout", TIMEOUT_MILLIS);
        if (connection.security() == MailSecurity.STARTTLS) {
            props.put("mail.smtp.starttls.enable", "true");
            // Required, not opportunistic: a server that does not offer STARTTLS fails the send
            // rather than receiving the password in clear text.
            props.put("mail.smtp.starttls.required", "true");
        } else if (connection.security() == MailSecurity.SSL) {
            sender.setProtocol("smtps");
            props.put("mail.smtps.auth", String.valueOf(authenticates));
            props.put("mail.smtps.connectiontimeout", TIMEOUT_MILLIS);
            props.put("mail.smtps.timeout", TIMEOUT_MILLIS);
            props.put("mail.smtps.writetimeout", TIMEOUT_MILLIS);
        }
        // Verify the server's certificate against its name, on both TLS modes.
        props.put("mail.smtp.ssl.checkserveridentity", "true");
        props.put("mail.smtps.ssl.checkserveridentity", "true");
        return sender;
    }

    /** The innermost cause's message, which is where Jakarta Mail puts what the server said. */
    private static String describe(Exception failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String detail = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        return detail.length() > 300 ? detail.substring(0, 300) + "…" : detail;
    }
}
