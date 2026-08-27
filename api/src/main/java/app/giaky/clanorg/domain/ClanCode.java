package app.giaky.clanorg.domain;

/**
 * A clan's shareable join code, {@code NGUYEN-7F3K} (doc 02 §3).
 *
 * <p>Generated once and never changed (I-C1), and never reissued even after the clan is deleted —
 * the uniqueness index in V4 has no soft-delete predicate for exactly that reason. A code that came
 * back into circulation would point a family at somebody else's records.
 *
 * <p>The constructor normalizes, so {@code ClanCode.of(" nguyen-7f3k ")} and the code as issued are
 * the same value. That is what lets a lookup be an equality check instead of a fuzzy match.
 */
public record ClanCode(String value) {

    public ClanCode {
        value = CodeFormat.normalize(value, "clan code");
    }

    public static ClanCode of(String raw) {
        return new ClanCode(raw);
    }

    /** Whether {@code raw} is a well-formed code. The lookup endpoint answers 400 rather than 404 when it is not. */
    public static boolean isValid(String raw) {
        return CodeFormat.isValid(raw);
    }

    @Override
    public String toString() {
        return value;
    }
}
