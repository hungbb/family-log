package app.giaky.identity.domain;

/**
 * A stored password hash (I-U3).
 *
 * <p>The type exists to make {@code String} the wrong shape everywhere a hash travels, so a raw
 * password can never be assigned where a hash belongs and a hash can never be handed to something
 * that expects display text. {@link #toString()} is overridden for the same reason: an accidental
 * interpolation into a log line prints a marker rather than the value.
 *
 * <p>Nothing in this package compares hashes. Verification belongs to {@link PasswordHasher},
 * because only the implementation knows the algorithm and only it can compare in constant time.
 */
public record PasswordHash(String value) {

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("password hash must not be blank");
        }
    }

    public static PasswordHash of(String value) {
        return new PasswordHash(value);
    }

    /** Never the hash itself: this type ends up inside domain objects that get logged. */
    @Override
    public String toString() {
        return "PasswordHash[redacted]";
    }
}
