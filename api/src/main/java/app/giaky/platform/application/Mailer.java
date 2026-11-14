package app.giaky.platform.application;

import app.giaky.platform.domain.MailSettings;
import app.giaky.platform.domain.MailSettingsRepository;
import app.giaky.platform.domain.MailTransport;
import app.giaky.platform.domain.MailTransport.MailMessage;
import app.giaky.platform.domain.MailTransport.SmtpConnection;
import app.giaky.platform.domain.SecretCipher;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sends mail through the transport a super admin configured. This is the published service other
 * contexts use; Identity sends verification and reset links through it.
 *
 * <p><b>Never throws, and never holds up the caller's transaction.</b> The message goes to a
 * background thread once the caller's transaction commits:
 *
 * <ul>
 *   <li>a slow or unreachable SMTP server cannot keep a database connection open, or make a
 *       registration time out;
 *   <li>a registration that rolls back does not send a link for an account that never existed;
 *   <li>a mail outage cannot roll back the account change that asked for the message. That is the
 *       contract {@code VerificationTokenDelivery} states.
 * </ul>
 *
 * <p>So a failed delivery is logged and not reported. The super admin's test message is the one
 * synchronous send ({@code MailSettingsService.sendTest}), because there the failure is the answer.
 */
@Service
public class Mailer implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(Mailer.class);

    private final MailSettingsRepository settings;
    private final SecretCipher cipher;
    private final MailTransport transport;
    private final Executor executor;

    /**
     * Sends on a virtual thread per message: an SMTP send is a few seconds of waiting on a socket,
     * which is what virtual threads are for, and messages come one per registration or reset rather
     * than in bulk.
     *
     * <p>The executor is private rather than a bean on purpose. Spring Boot backs off its own
     * {@code applicationTaskExecutor} as soon as any {@code Executor} bean exists.
     */
    @Autowired
    public Mailer(MailSettingsRepository settings, SecretCipher cipher, MailTransport transport) {
        this(
                settings,
                cipher,
                transport,
                Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("mail-", 0).factory()));
    }

    /** For tests, which pass a synchronous executor. */
    Mailer(
            MailSettingsRepository settings,
            SecretCipher cipher,
            MailTransport transport,
            Executor executor) {
        this.settings = settings;
        this.cipher = cipher;
        this.transport = transport;
        this.executor = executor;
    }

    /** Lets sends already under way finish before the application stops. */
    @Override
    public void destroy() {
        if (executor instanceof ExecutorService service) {
            service.close();
        }
    }

    /**
     * Queues a message for delivery once the current transaction commits, or at once if there is
     * no transaction.
     *
     * @return false when mail is switched off or cannot be used, so the caller can say that
     *     nothing was sent; true when the message was queued (which does not mean delivered)
     */
    public boolean send(OutgoingMail mail) {
        MailSettings current;
        SmtpConnection connection;
        try {
            current = settings.load();
            if (!current.enabled()) {
                return false;
            }
            connection = connectionFor(current);
        } catch (RuntimeException unusable) {
            // Most likely a rotated SETTINGS_ENCRYPTION_KEY that the stored password predates.
            log.error("Outgoing mail is enabled but cannot be used: {}", unusable.getMessage());
            return false;
        }

        MailMessage message = messageFor(current, mail);
        Runnable delivery = () -> deliver(connection, message);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            executor.execute(delivery);
                        }
                    });
        } else {
            executor.execute(delivery);
        }
        return true;
    }

    /** The connection the stored settings describe, with the password decrypted. */
    SmtpConnection connectionFor(MailSettings current) {
        String password =
                current.hasPassword() ? cipher.decrypt(current.passwordCiphertext()) : null;
        return new SmtpConnection(
                current.host(), current.port(), current.username(), password, current.security());
    }

    static MailMessage messageFor(MailSettings current, OutgoingMail mail) {
        return new MailMessage(
                current.fromAddress(), current.fromName(), mail.to(), mail.subject(), mail.text());
    }

    private void deliver(SmtpConnection connection, MailMessage message) {
        try {
            transport.send(connection, message);
            // No recipient in the log line: an address is personal data (doc 08 O8.6).
            log.info("Mail delivered via {}", connection);
        } catch (RuntimeException failed) {
            log.warn("Mail delivery via {} failed: {}", connection, failed.getMessage());
        }
    }
}
