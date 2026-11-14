package app.giaky.platform.application;

import app.giaky.common.audit.AuditAction;
import app.giaky.common.audit.AuditRecord;
import app.giaky.common.audit.AuditWriter;
import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.FieldViolation;
import app.giaky.common.types.UserId;
import app.giaky.identity.application.SuperAdminGuard;
import app.giaky.platform.domain.MailSettings;
import app.giaky.platform.domain.MailSettingsRepository;
import app.giaky.platform.domain.MailTransport;
import app.giaky.platform.domain.MailTransportException;
import app.giaky.platform.domain.SecretCipher;
import java.time.Clock;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The outgoing mail transport, as a super admin manages it.
 *
 * <p>Every method asks {@link SuperAdminGuard} first. The SMTP password is write-only: it is
 * encrypted on the way in, and nothing here returns it, logs it or audits it.
 */
@Service
public class MailSettingsService {

    private static final Pattern ADDRESS = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    private static final String TEST_SUBJECT = "Gia Ký — thư kiểm tra / test message";
    private static final String TEST_TEXT =
            """
            Đây là thư kiểm tra từ Gia Ký. Nếu bạn nhận được thư này, cấu hình gửi thư đã hoạt động.

            This is a test message from Gia Ký. If you received it, outgoing mail works.
            """;

    private final SuperAdminGuard guard;
    private final MailSettingsRepository repository;
    private final SecretCipher cipher;
    private final MailTransport transport;
    private final Mailer mailer;
    private final AuditWriter auditWriter;
    private final Clock clock;

    public MailSettingsService(
            SuperAdminGuard guard,
            MailSettingsRepository repository,
            SecretCipher cipher,
            MailTransport transport,
            Mailer mailer,
            AuditWriter auditWriter,
            Clock clock) {
        this.guard = guard;
        this.repository = repository;
        this.cipher = cipher;
        this.transport = transport;
        this.mailer = mailer;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MailSettingsView get(UserId caller) {
        guard.require(caller);
        return MailSettingsView.of(repository.load());
    }

    /**
     * Replaces the settings.
     *
     * @throws DomainRuleViolation {@code VALIDATION_FAILED} with one entry per field at fault
     */
    @Transactional
    public MailSettingsView update(UserId caller, UpdateMailSettingsCommand command) {
        guard.require(caller);
        MailSettings existing = repository.load();

        String passwordCiphertext =
                switch (command.password()) {
                    case null -> existing.passwordCiphertext();
                    case String cleared when cleared.isEmpty() -> null;
                    case String replaced -> cipher.encrypt(replaced);
                };

        MailSettings candidate =
                new MailSettings(
                        command.enabled(),
                        command.host(),
                        command.port(),
                        command.username(),
                        passwordCiphertext,
                        command.security(),
                        command.fromAddress(),
                        command.fromName(),
                        existing.version(),
                        clock.instant(),
                        caller);

        List<MailSettings.Violation> violations = candidate.violations();
        if (!violations.isEmpty()) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED,
                    "The mail settings are incomplete or invalid.",
                    violations.stream()
                            .map(violation -> new FieldViolation(violation.field(), violation.message()))
                            .toList());
        }

        MailSettings saved = repository.save(candidate);
        auditWriter.write(
                AuditRecord.onPlatformSetting(AuditAction.MAIL_SETTINGS_UPDATED, caller)
                        .withChange(describe(existing), describe(saved)));
        return MailSettingsView.of(saved);
    }

    /**
     * Sends a test message through the stored settings, synchronously, and reports what the server
     * said.
     *
     * <p>Works while mail is still disabled, so the transport can be proven before it is switched
     * on. It only needs the settings to be complete.
     *
     * @throws DomainRuleViolation {@code MAIL_NOT_CONFIGURED} when there is no host, port or sender
     *     yet; {@code MAIL_DELIVERY_FAILED} with the server's complaint when the send fails;
     *     {@code VALIDATION_FAILED} when {@code to} is not an address
     */
    @Transactional
    public void sendTest(UserId caller, String to) {
        guard.require(caller);
        if (to == null || !ADDRESS.matcher(to.trim()).matches()) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED,
                    "The recipient must be an email address.",
                    List.of(new FieldViolation("to", "must be an email address")));
        }

        MailSettings current = repository.load();
        if (current.host() == null || current.port() == null || current.fromAddress() == null) {
            throw new DomainRuleViolation(
                    ErrorCode.MAIL_NOT_CONFIGURED,
                    "Save a host, port and sender address before sending a test message.");
        }

        try {
            transport.send(
                    mailer.connectionFor(current),
                    Mailer.messageFor(current, new OutgoingMail(to.trim(), TEST_SUBJECT, TEST_TEXT)));
        } catch (MailTransportException | IllegalStateException failed) {
            throw new DomainRuleViolation(ErrorCode.MAIL_DELIVERY_FAILED, failed.getMessage());
        }
        auditWriter.write(AuditRecord.onPlatformSetting(AuditAction.MAIL_TEST_SENT, caller));
    }

    /**
     * The audited fields, as JSON. Hand-built for the reason {@code AccountService.describe} gives:
     * a serializer would follow every field, and the first one it followed here would be the
     * password ciphertext.
     */
    private static String describe(MailSettings settings) {
        return "{"
                + "\"enabled\":" + settings.enabled()
                + ",\"host\":" + json(settings.host())
                + ",\"port\":" + settings.port()
                + ",\"username\":" + json(settings.username())
                + ",\"loginStored\":" + settings.hasPassword()
                + ",\"security\":" + json(settings.security().name())
                + ",\"fromAddress\":" + json(settings.fromAddress())
                + ",\"fromName\":" + json(settings.fromName())
                + "}";
    }

    private static String json(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }
}
