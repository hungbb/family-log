package app.giaky.identity.infrastructure;

import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.TokenPurpose;
import app.giaky.identity.domain.VerificationTokenDelivery;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Delivery in a deployment with a mail server: sends the verification or reset link by SMTP.
 *
 * <p>Active outside {@code dev} and {@code test} when {@code app.mail.host} is set; with it empty,
 * {@link LoggedVerificationTokenDelivery} takes its place. Exactly one of the two exists.
 *
 * <p>Three choices the port's contract forces:
 *
 * <ul>
 *   <li><b>After commit.</b> The token is issued inside the caller's transaction. Sending at once
 *       would mail a dead link whenever that transaction rolled back, so the send waits for the
 *       commit and is dropped on a rollback.
 *   <li><b>Off the request thread.</b> One worker sends, in order, so a slow or unreachable mail
 *       server delays a message rather than a registration. It also keeps a personal mail account
 *       to one connection at a time.
 *   <li><b>Never throws.</b> A failed send is logged at {@code WARN} and dropped; the user can ask
 *       for another link. The address and the token are never logged.
 * </ul>
 *
 * <p>The {@link JavaMailSender} is built here rather than registered as a bean, on purpose: Spring
 * Boot adds a mail health indicator for a sender bean, and a mail provider's bad minute would then
 * report the whole API as down and fail a deployment's smoke test.
 */
@Component
@Profile("!(dev | test)")
@ConditionalOnExpression("!'${app.mail.host:}'.isBlank()")
public class SmtpVerificationTokenDelivery implements VerificationTokenDelivery, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SmtpVerificationTokenDelivery.class);

    private static final String SENDER_NAME = "Gia Ký";
    private static final int TIMEOUT_MS = 10_000;

    private final JavaMailSender sender;
    private final InternetAddress from;
    private final RedemptionLinks links;
    private final ExecutorService worker;

    @Autowired
    public SmtpVerificationTokenDelivery(
            @Value("${app.mail.host}") String host,
            @Value("${app.mail.port:587}") int port,
            @Value("${app.mail.username:}") String username,
            @Value("${app.mail.password:}") String password,
            @Value("${app.mail.starttls:true}") boolean starttls,
            @Value("${app.mail.from:}") String from,
            @Value("${app.web.base-url}") String webBaseUrl) {
        this(
                mailSender(host.trim(), port, username.trim(), password, starttls),
                sender(from, username.trim()),
                webBaseUrl,
                Executors.newSingleThreadExecutor(Thread.ofVirtual().name("mail-", 0).factory()));
        log.info("Mail delivery by SMTP through {}:{}", host.trim(), port);
    }

    SmtpVerificationTokenDelivery(
            JavaMailSender sender, InternetAddress from, String webBaseUrl, ExecutorService worker) {
        this.sender = sender;
        this.from = from;
        this.links = new RedemptionLinks(webBaseUrl);
        this.worker = worker;
    }

    @Override
    public void deliver(
            EmailAddress recipient, TokenPurpose purpose, String rawToken, Instant expiresAt) {
        try {
            VerificationEmail email =
                    VerificationEmail.of(purpose, links.link(purpose, rawToken), expiresAt);
            Runnable send = () -> worker.execute(() -> send(recipient, purpose, email));

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                send.run();
                            }
                        });
            } else {
                send.run();
            }
        } catch (RuntimeException unexpected) {
            log.warn("Could not queue a {} email: {}", purpose, unexpected.toString());
        }
    }

    private void send(EmailAddress recipient, TokenPurpose purpose, VerificationEmail email) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(recipient.value());
            helper.setSubject(email.subject());
            helper.setText(email.text(), email.html());
            sender.send(message);
            log.info("Sent a {} email", purpose);
        } catch (Exception failed) {
            log.warn("Sending a {} email failed: {}", purpose, failed.toString());
        }
    }

    /** Lets the messages already queued go out on a graceful shutdown, within reason. */
    @Override
    public void destroy() throws InterruptedException {
        worker.shutdown();
        if (!worker.awaitTermination(15, TimeUnit.SECONDS)) {
            log.warn("Mail worker stopped with messages still queued");
            worker.shutdownNow();
        }
    }

    private static JavaMailSender mailSender(
            String host, int port, String username, String password, boolean starttls) {
        JavaMailSenderImpl impl = new JavaMailSenderImpl();
        impl.setHost(host);
        impl.setPort(port);
        impl.setDefaultEncoding(StandardCharsets.UTF_8.name());
        boolean auth = !username.isEmpty();
        if (auth) {
            impl.setUsername(username);
            impl.setPassword(password);
        }
        Properties props = impl.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(auth));
        // Required, not merely enabled: a server that does not offer TLS gets no password.
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        props.put("mail.smtp.starttls.required", String.valueOf(starttls));
        props.put("mail.smtp.ssl.checkserveridentity", "true");
        props.put("mail.smtp.connectiontimeout", String.valueOf(TIMEOUT_MS));
        props.put("mail.smtp.timeout", String.valueOf(TIMEOUT_MS));
        props.put("mail.smtp.writetimeout", String.valueOf(TIMEOUT_MS));
        return impl;
    }

    /**
     * {@code app.mail.from} if set ({@code Name <address>} or a bare address), else the SMTP
     * username, which is what a personal account such as Gmail will send as anyway.
     */
    static InternetAddress sender(String from, String username) {
        String address = from == null || from.isBlank() ? username : from.trim();
        if (address.isEmpty()) {
            throw new IllegalStateException(
                    "app.mail.host is set but there is no sender: set MAIL_FROM or SMTP_USERNAME");
        }
        try {
            InternetAddress parsed = new InternetAddress(address, true);
            if (parsed.getPersonal() == null) {
                parsed.setPersonal(SENDER_NAME, StandardCharsets.UTF_8.name());
            }
            return parsed;
        } catch (jakarta.mail.internet.AddressException | UnsupportedEncodingException invalid) {
            throw new IllegalStateException("MAIL_FROM is not a valid address: " + invalid.getMessage());
        }
    }
}
