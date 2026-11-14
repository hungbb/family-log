package app.giaky.platform.domain;

import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The outgoing mail transport, as a super admin configured it.
 *
 * <p>One per installation. The SMTP password is held only as ciphertext ({@link SecretCipher}); this
 * type never sees it in the clear, so neither a log line nor a careless {@code toString} can leak it.
 *
 * @param enabled whether anything is sent at all. Off by default: an installation sends nothing
 *     until somebody has said where to send it from
 * @param host the SMTP server; required when enabled
 * @param port the SMTP port; required when enabled
 * @param username the SMTP login; null for a relay that does not authenticate
 * @param passwordCiphertext the SMTP password, encrypted; null when none is stored
 * @param security how the connection is protected
 * @param fromAddress the sender address; required when enabled
 * @param fromName the sender's display name; null for the bare address
 * @param version optimistic-lock version
 * @param updatedAt when the settings last changed
 * @param updatedBy who changed them; null before anyone has
 */
public record MailSettings(
        boolean enabled,
        String host,
        Integer port,
        String username,
        String passwordCiphertext,
        MailSecurity security,
        String fromAddress,
        String fromName,
        long version,
        Instant updatedAt,
        UserId updatedBy) {

    /** Deliberately loose: the SMTP server is the authority on what it accepts. */
    private static final Pattern ADDRESS = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    public MailSettings {
        Objects.requireNonNull(security, "security must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        host = blankToNull(host);
        username = blankToNull(username);
        fromAddress = blankToNull(fromAddress);
        fromName = blankToNull(fromName);
    }

    public boolean hasPassword() {
        return passwordCiphertext != null;
    }

    /**
     * What is wrong with these settings, as {@code field: message} pairs; empty when nothing is.
     *
     * <p>Shape is always checked. Completeness only matters once the transport is enabled: a
     * super admin may save half a configuration while waiting for the password from whoever runs
     * the mailbox.
     */
    public List<Violation> violations() {
        List<Violation> found = new ArrayList<>();
        if (port != null && (port < 1 || port > 65_535)) {
            found.add(new Violation("port", "must be between 1 and 65535"));
        }
        if (fromAddress != null && !ADDRESS.matcher(fromAddress).matches()) {
            found.add(new Violation("fromAddress", "must be an email address"));
        }
        if (enabled) {
            if (host == null) {
                found.add(new Violation("host", "is required to enable mail"));
            }
            if (port == null) {
                found.add(new Violation("port", "is required to enable mail"));
            }
            if (fromAddress == null) {
                found.add(new Violation("fromAddress", "is required to enable mail"));
            }
        }
        return List.copyOf(found);
    }

    /** One problem with one field. */
    public record Violation(String field, String message) {}

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
