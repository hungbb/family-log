package app.giaky.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.common.audit.AuditAction;
import app.giaky.common.audit.AuditRecord;
import app.giaky.common.error.ApiException;
import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.PermissionDeniedException;
import app.giaky.common.types.UserId;
import app.giaky.identity.application.SuperAdminGuard;
import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.LocaleTag;
import app.giaky.identity.domain.PasswordHash;
import app.giaky.identity.domain.User;
import app.giaky.identity.support.IdentityTestFixtures;
import app.giaky.identity.support.InMemoryUserRepository;
import app.giaky.identity.support.RecordingAuditWriter;
import app.giaky.platform.domain.MailSecurity;
import app.giaky.platform.domain.MailSettings;
import app.giaky.platform.domain.MailSettingsRepository;
import app.giaky.platform.domain.MailTransport;
import app.giaky.platform.domain.MailTransportException;
import app.giaky.platform.domain.SecretCipher;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MailSettingsServiceTest {

    private StoredSettings repository;
    private RecordingTransport transport;
    private RecordingAuditWriter audit;
    private MailSettingsService service;
    private Mailer mailer;
    private UserId operator;
    private UserId member;

    @BeforeEach
    void setUp() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        operator = users.save(account("operator@example.com").verifyEmail(IdentityTestFixtures.NOW)).id();
        member = users.save(account("member@example.com")).id();

        repository = new StoredSettings();
        transport = new RecordingTransport();
        audit = new RecordingAuditWriter();
        SecretCipher cipher = new ReversingCipher();
        mailer = new Mailer(repository, cipher, transport, Runnable::run);
        service =
                new MailSettingsService(
                        new SuperAdminGuard(users, List.of("operator@example.com")),
                        repository,
                        cipher,
                        transport,
                        mailer,
                        audit,
                        IdentityTestFixtures.fixedClock());
    }

    @Test
    @DisplayName("a member who is not a super admin can neither read nor change the settings")
    void refusesNonAdmins() {
        assertThatThrownBy(() -> service.get(member)).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> service.update(member, command(true, "new-password")))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(repository.stored.enabled()).isFalse();
    }

    @Test
    @DisplayName("the password is stored encrypted and never read back")
    void passwordIsWriteOnly() {
        MailSettingsView view = service.update(operator, command(true, "s3cret"));

        assertThat(view.loginStored()).isTrue();
        assertThat(repository.stored.passwordCiphertext()).isEqualTo("enc:terc3s");
        assertThat(view.toString()).doesNotContain("s3cret").doesNotContain("terc3s");
    }

    @Test
    @DisplayName("null keeps the stored password, empty removes it")
    void passwordSemantics() {
        service.update(operator, command(true, "s3cret"));

        service.update(operator, command(true, null));
        assertThat(repository.stored.passwordCiphertext()).isEqualTo("enc:terc3s");

        MailSettingsView cleared = service.update(operator, command(true, ""));
        assertThat(cleared.loginStored()).isFalse();
        assertThat(repository.stored.passwordCiphertext()).isNull();
    }

    @Test
    @DisplayName("enabling an incomplete configuration is refused field by field")
    void validates() {
        UpdateMailSettingsCommand incomplete =
                new UpdateMailSettingsCommand(
                        true, null, null, null, null, MailSecurity.STARTTLS, null, null);

        assertThatThrownBy(() -> service.update(operator, incomplete))
                .isInstanceOf(DomainRuleViolation.class)
                .satisfies(
                        e ->
                                assertThat(((ApiException) e).errors())
                                        .extracting(violation -> violation.field())
                                        .containsExactlyInAnyOrder("host", "port", "fromAddress"));
    }

    @Test
    @DisplayName("the audit trail records what changed, and not the password")
    void auditsWithoutPassword() {
        service.update(operator, command(true, "s3cret"));

        AuditRecord entry = audit.written().getFirst();
        assertThat(entry.action()).isEqualTo(AuditAction.MAIL_SETTINGS_UPDATED);
        assertThat(entry.actorUserId()).isEqualTo(operator);
        assertThat(entry.afterJson())
                .contains("\"host\":\"smtp.example.com\"")
                .contains("\"loginStored\":true")
                .doesNotContain("s3cret")
                .doesNotContain("terc3s");
    }

    @Test
    @DisplayName("a test message goes out with the decrypted password, even before mail is enabled")
    void sendsTest() {
        service.update(operator, command(false, "s3cret"));

        service.sendTest(operator, "me@example.com");

        assertThat(transport.sent).singleElement().satisfies(sent -> {
            assertThat(sent.connection().password()).isEqualTo("s3cret");
            assertThat(sent.message().to()).isEqualTo("me@example.com");
            assertThat(sent.message().fromAddress()).isEqualTo("noreply@example.com");
        });
        assertThat(audit.recorded(AuditAction.MAIL_TEST_SENT)).isTrue();
    }

    @Test
    @DisplayName("a test with no host yet says so instead of trying")
    void testNeedsSettings() {
        assertThatThrownBy(() -> service.sendTest(operator, "me@example.com"))
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.MAIL_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("a refused test reports what the server said")
    void testFailureIsReported() {
        service.update(operator, command(true, "wrong"));
        transport.failWith = new MailTransportException("535 Authentication failed", null);

        assertThatThrownBy(() -> service.sendTest(operator, "me@example.com"))
                .isInstanceOf(DomainRuleViolation.class)
                .hasMessageContaining("535 Authentication failed")
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.MAIL_DELIVERY_FAILED);
    }

    @Test
    @DisplayName("the mailer sends nothing while mail is disabled, and says so")
    void mailerRespectsDisabled() {
        service.update(operator, command(false, null));

        boolean queued = mailer.send(new OutgoingMail("an@example.com", "Hi", "Body"));

        assertThat(queued).isFalse();
        assertThat(transport.sent).isEmpty();
    }

    @Test
    @DisplayName("the mailer sends once enabled, and swallows a delivery failure")
    void mailerSendsWhenEnabled() {
        service.update(operator, command(true, "s3cret"));
        assertThat(mailer.send(new OutgoingMail("an@example.com", "Hi", "Body"))).isTrue();
        assertThat(transport.sent).hasSize(1);

        transport.failWith = new MailTransportException("connection refused", null);
        assertThat(mailer.send(new OutgoingMail("an@example.com", "Hi", "Body"))).isTrue();
    }

    @Test
    @DisplayName("the connection's string form never contains the password")
    void connectionToStringHidesPassword() {
        var connection =
                new MailTransport.SmtpConnection("h", 25, "u", "s3cret", MailSecurity.NONE);

        assertThat(connection.toString()).doesNotContain("s3cret");
    }

    private static UpdateMailSettingsCommand command(boolean enabled, String password) {
        return new UpdateMailSettingsCommand(
                enabled,
                "smtp.example.com",
                587,
                "mailer",
                password,
                MailSecurity.STARTTLS,
                "noreply@example.com",
                "Gia Ký");
    }

    private static User account(String email) {
        return User.register(
                UserId.of(UUID.randomUUID()),
                EmailAddress.of(email),
                PasswordHash.of("$2a$12$hash"),
                email,
                LocaleTag.VI_VN,
                IdentityTestFixtures.NOW);
    }

    /** The one row, as the migration leaves it: disabled and empty. */
    private static final class StoredSettings implements MailSettingsRepository {
        MailSettings stored =
                new MailSettings(
                        false,
                        null,
                        null,
                        null,
                        null,
                        MailSecurity.STARTTLS,
                        null,
                        null,
                        0,
                        IdentityTestFixtures.NOW,
                        null);

        @Override
        public MailSettings load() {
            return stored;
        }

        @Override
        public MailSettings save(MailSettings settings) {
            stored = settings;
            return settings;
        }
    }

    /** Visibly not the plaintext, and reversible, which is all a unit test needs from a cipher. */
    private static final class ReversingCipher implements SecretCipher {
        @Override
        public String encrypt(String plaintext) {
            return "enc:" + new StringBuilder(plaintext).reverse();
        }

        @Override
        public String decrypt(String ciphertext) {
            return new StringBuilder(ciphertext.substring(4)).reverse().toString();
        }
    }

    private static final class RecordingTransport implements MailTransport {
        final List<Sent> sent = new ArrayList<>();
        MailTransportException failWith;

        @Override
        public void send(SmtpConnection connection, MailMessage message) {
            if (failWith != null) {
                throw failWith;
            }
            sent.add(new Sent(connection, message));
        }

        record Sent(SmtpConnection connection, MailMessage message) {}
    }
}
