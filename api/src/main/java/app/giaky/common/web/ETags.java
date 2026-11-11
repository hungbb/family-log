package app.giaky.common.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Strong {@code ETag}s derived from a resource's version counter.
 *
 * <p>Used on the read paths that carry an explicit version — the clan tree is stamped with its
 * {@code treeVersion}, an aggregate with its optimistic-locking {@code version} — so a client can
 * revalidate with {@code If-None-Match} and get a {@code 304} instead of the payload.
 *
 * <p><b>A validator identifies a representation, not a resource.</b> Where one URL can answer with
 * different bodies at the same version — the tree's {@code canEdit} flags are computed for the
 * caller, and {@code rootPersonId} and {@code depth} select a subtree — the version alone is not a
 * validator, and a {@code 304} built on it hands one caller another's representation (#49). {@link
 * #ofVariant(long, String...)} folds those discriminators in; what they are is the caller's business,
 * because only the caller knows what its body varies by.
 *
 * <p>Distinct from the {@code ShallowEtagHeaderFilter} registered in {@code WebConfig}: that one
 * hashes the rendered body and still pays the cost of producing it, this one short-circuits before
 * the work happens.
 */
public final class ETags {

    /**
     * Hex characters of the digest kept in a variant tag.
     *
     * <p>Sixty-four bits. A collision is a wrong {@code 304}, so this is not a place to be
     * economical; it is also not a secret, so it does not need all 256.
     */
    private static final int FINGERPRINT_CHARS = 16;

    /**
     * Separates the discriminators before hashing, so that {@code ("ab", "c")} and {@code ("a",
     * "bc")} do not fingerprint alike. A unit separator cannot occur in a UUID, a number or a
     * scope name.
     */
    private static final char SEPARATOR = '\u001f';

    private ETags() {}

    /** Formats a version as a strong entity tag, e.g. {@code "v42"}. */
    public static String of(long version) {
        return "\"v" + version + "\"";
    }

    /**
     * A tag for a representation that varies by more than its version, e.g. {@code
     * "v42.1f3c0a9b7e5d2648"}.
     *
     * <p>The discriminators are hashed rather than spelled out: they can name a caller and the
     * grants they hold, and an {@code ETag} travels through caches and logs. The hash also keeps
     * the header a fixed size however many of them there are.
     *
     * @param discriminators everything other than the version that the body depends on; order
     *     matters, and a null entry is distinct from an empty one
     */
    public static String ofVariant(long version, String... discriminators) {
        return "\"v" + version + "." + fingerprint(discriminators) + "\"";
    }

    /**
     * Whether an {@code If-None-Match} header names the tag the resource currently has.
     *
     * @param ifNoneMatch the raw header value, possibly {@code null} or a comma-separated list
     * @param currentTag the tag from {@link #of} or {@link #ofVariant}, quotes included
     */
    public static boolean matches(String ifNoneMatch, String currentTag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String trimmed = candidate.trim();
            // A weak validator ("W/\"v42\"") still identifies the same representation here.
            if (trimmed.startsWith("W/")) {
                trimmed = trimmed.substring(2);
            }
            if ("*".equals(trimmed) || currentTag.equals(trimmed)) {
                return true;
            }
        }
        return false;
    }

    /** Whether an {@code If-None-Match} header matches the current version. */
    public static boolean matches(String ifNoneMatch, long version) {
        return matches(ifNoneMatch, of(version));
    }

    private static String fingerprint(String... discriminators) {
        StringBuilder material = new StringBuilder();
        for (String discriminator : discriminators) {
            material.append(discriminator == null ? "\u0000" : discriminator).append(SEPARATOR);
        }
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(material.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(FINGERPRINT_CHARS);
            for (int i = 0; i < FINGERPRINT_CHARS / 2; i++) {
                hex.append(Character.forDigit((digest[i] >> 4) & 0xF, 16));
                hex.append(Character.forDigit(digest[i] & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            // Every JVM is required to provide SHA-256.
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
