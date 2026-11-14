package app.giaky.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.TokenPurpose;
import app.giaky.identity.domain.VerificationTokenDelivery;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SmtpVerificationTokenDeliveryTest {

    private static final EmailAddress RECIPIENT = EmailAddress.of("an@example.com");
    private static final Instant EXPIRES = Instant.parse("2026-11-15T03:30:00Z"); // 10:30 in Hà Nội

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private SmtpVerificationTokenDelivery delivery;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage())
                .thenAnswer(call -> new MimeMessage(Session.getInstance(new Properties())));
        delivery =
                new SmtpVerificationTokenDelivery(
                        mailSender,
                        SmtpVerificationTokenDelivery.sender("", "giaky.sender@example.com"),
                        "https://giaky.example.com/",
                        new DirectExecutor());
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("a verification email carries the web client's link, the expiry and both languages")
    void verificationEmail() throws Exception {
        delivery.deliver(RECIPIENT, TokenPurpose.EMAIL_VERIFY, "tok-123", EXPIRES);

        MimeMessage sent = sent();
        assertThat(sent.getRecipients(Message.RecipientType.TO))
                .extracting(Object::toString)
                .containsExactly("an@example.com");
        InternetAddress from = (InternetAddress) sent.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("giaky.sender@example.com");
        assertThat(from.getPersonal()).isEqualTo("Gia Ký");
        assertThat(sent.getSubject()).isEqualTo("Xác nhận địa chỉ email — Gia Ký");

        String text = part(sent, "text/plain");
        assertThat(text)
                .contains("https://giaky.example.com/verify-email?token=tok-123")
                .contains("10:30 15/11/2026")
                .contains("xác nhận địa chỉ email")
                .contains("confirm the email address");
        assertThat(part(sent, "text/html"))
                .contains("href=\"https://giaky.example.com/verify-email?token=tok-123\"");
    }

    @Test
    @DisplayName("a reset email links to the reset page")
    void resetEmail() throws Exception {
        delivery.deliver(RECIPIENT, TokenPurpose.PASSWORD_RESET, "tok-456", EXPIRES);

        MimeMessage sent = sent();
        assertThat(sent.getSubject()).isEqualTo("Đặt lại mật khẩu — Gia Ký");
        assertThat(part(sent, "text/plain"))
                .contains("https://giaky.example.com/reset-password?token=tok-456");
    }

    @Test
    @DisplayName("inside a transaction, the email waits for the commit")
    void sendsAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        delivery.deliver(RECIPIENT, TokenPurpose.EMAIL_VERIFY, "tok", EXPIRES);
        verify(mailSender, never()).send(any(MimeMessage.class));

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("a rolled-back transaction sends nothing: no dead link in anybody's inbox")
    void rollbackSendsNothing() {
        TransactionSynchronizationManager.initSynchronization();

        delivery.deliver(RECIPIENT, TokenPurpose.EMAIL_VERIFY, "tok", EXPIRES);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("a mail server failure never reaches the caller (the port's contract)")
    void failureDoesNotThrow() {
        doThrow(new MailSendException("connection refused"))
                .when(mailSender)
                .send(any(MimeMessage.class));

        assertThatCode(() -> delivery.deliver(RECIPIENT, TokenPurpose.EMAIL_VERIFY, "tok", EXPIRES))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the sender is MAIL_FROM when given, the SMTP username otherwise, and must exist")
    void senderAddress() throws Exception {
        assertThat(SmtpVerificationTokenDelivery.sender("Họ Nguyễn <no-reply@example.com>", "u@example.com"))
                .satisfies(a -> {
                    assertThat(a.getAddress()).isEqualTo("no-reply@example.com");
                    assertThat(a.getPersonal()).isEqualTo("Họ Nguyễn");
                });
        assertThat(SmtpVerificationTokenDelivery.sender(" ", "u@example.com").getAddress())
                .isEqualTo("u@example.com");
        assertThatThrownBy(() -> SmtpVerificationTokenDelivery.sender("", ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Nested
    @DisplayName("which adapter a deployment gets")
    class Selection {

        private final ApplicationContextRunner prod =
                new ApplicationContextRunner()
                        .withUserConfiguration(
                                LoggedVerificationTokenDelivery.class,
                                SmtpVerificationTokenDelivery.class)
                        .withPropertyValues(
                                "spring.profiles.active=prod", "app.web.base-url=https://giaky.example.com");

        @Test
        @DisplayName("no host: the logging adapter, alone")
        void withoutHost() {
            prod.withPropertyValues("app.mail.host=")
                    .run(context -> assertThat(context)
                            .hasSingleBean(VerificationTokenDelivery.class)
                            .hasSingleBean(LoggedVerificationTokenDelivery.class));
        }

        @Test
        @DisplayName("a host: the SMTP adapter, alone")
        void withHost() {
            prod.withPropertyValues(
                            "app.mail.host=smtp.example.com", "app.mail.username=u@example.com")
                    .run(context -> assertThat(context)
                            .hasSingleBean(VerificationTokenDelivery.class)
                            .hasSingleBean(SmtpVerificationTokenDelivery.class));
        }

        @Test
        @DisplayName("a host with no sender anywhere fails at startup, not at the first registration")
        void withHostButNoSender() {
            prod.withPropertyValues("app.mail.host=smtp.example.com")
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    private MimeMessage sent() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        message.saveChanges(); // Until saved, a message reports text/plain whatever it holds.
        return message;
    }

    /** The first body part of the given type, searched through the multipart tree. */
    private static String part(Part part, String type) throws Exception {
        if (part.isMimeType(type)) {
            return (String) part.getContent();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            List<String> found = new ArrayList<>();
            for (int i = 0; i < multipart.getCount(); i++) {
                String inner = part(multipart.getBodyPart(i), type);
                if (inner != null) {
                    found.add(inner);
                }
            }
            return found.isEmpty() ? null : found.get(0);
        }
        return null;
    }

    /** Runs each task on the calling thread, so a test sees the send happen. */
    private static final class DirectExecutor extends AbstractExecutorService {
        private boolean shutdown;

        @Override public void execute(Runnable command) { command.run(); }
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }
}
