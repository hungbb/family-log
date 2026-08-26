package app.giaky.identity.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An email address, normalized (FR-A1).
 *
 * <p>Normalization is trim plus lowercase, applied in the constructor, so there is no way to hold an
 * un-normalized one. That is what makes uniqueness mean anything: without it {@code An@Example.com}
 * and {@code an@example.com} are two accounts, and the second one is an account-takeover vector
 * against the first (I-U1).
 *
 * <p>The pattern is deliberately loose. Validating email syntax strictly is a famous way to reject
 * addresses that work; the only address that has really been verified is one that received a
 * message, which is what {@code EMAIL_VERIFY} is for. This rejects the shapes that are certainly
 * wrong and leaves the rest to delivery.
 */
public record EmailAddress(String value) {

    /** {@code users.email} is {@code VARCHAR(320)} — 64 for the local part, 255 for the domain. */
    public static final int MAX_LENGTH = 320;

    private static final Pattern SHAPE =
            Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    public EmailAddress {
        if (value == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("email must be at most " + MAX_LENGTH + " characters");
        }
        if (!SHAPE.matcher(value).matches()) {
            throw new IllegalArgumentException("email is not a valid address");
        }
    }

    public static EmailAddress of(String raw) {
        return new EmailAddress(raw);
    }

    /**
     * Whether {@code raw} would produce a valid address, without throwing.
     *
     * <p>Used where an invalid address is an ordinary outcome rather than a bug — the
     * forgot-password endpoint, which must answer identically for anything it is given.
     */
    public static boolean isValid(String raw) {
        try {
            of(raw);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
