package app.giaky.clanorg.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The shape shared by {@link ClanCode} and {@link BranchCode}: {@code SURNAME-XXXX}.
 *
 * <p>Package-private, because a code's format is nobody else's business — the two value objects are
 * the public surface and this is the rule they both obey. Keeping it in one place is what stops the
 * two from drifting apart the first time one of them is relaxed.
 *
 * <p>The suffix alphabet is <a href="https://www.crockford.com/base32.html">Crockford base32</a>:
 * the digits and the letters minus {@code I}, {@code L}, {@code O} and {@code U}. Those four are
 * excluded because a code's whole purpose is to survive being read aloud, written on paper, and
 * typed back in — and {@code I}/{@code 1}, {@code O}/{@code 0} are the pairs that never survive it.
 */
final class CodeFormat {

    /** {@code clans.clan_code} and {@code branches.branch_code} are both {@code VARCHAR(32)}. */
    static final int MAX_LENGTH = 32;

    /** The Crockford alphabet, in value order. */
    static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    /** How many characters the random suffix has: 32⁴ ≈ 1.05 million per prefix. */
    static final int SUFFIX_LENGTH = 4;

    /** The prefix is a surname, so it is short; 20 leaves room for the separator and the suffix. */
    static final int MAX_PREFIX_LENGTH = 20;

    /** Mirrors {@code ck_clans_code_shape} / {@code ck_branches_code_shape} in V4. */
    private static final Pattern SHAPE =
            Pattern.compile("^[A-Z0-9]{1,20}-[0-9A-HJKMNP-TV-Z]{4,8}$");

    /** Combining diacritical marks, left behind once a string is decomposed. */
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private static final Pattern NOT_CODE_CHARACTER = Pattern.compile("[^A-Z0-9]");

    private CodeFormat() {}

    /**
     * Validates and canonicalizes a code someone typed.
     *
     * <p>Trims and upper-cases before checking, because a code arrives from a chat message or a
     * scrap of paper and the person entering it should not have to care about either.
     *
     * @throws IllegalArgumentException the value is not a code
     */
    static String normalize(String raw, String what) {
        if (raw == null) {
            throw new IllegalArgumentException(what + " must not be null");
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(what + " must be at most " + MAX_LENGTH + " characters");
        }
        if (!SHAPE.matcher(value).matches()) {
            throw new IllegalArgumentException(what + " is not a valid code");
        }
        return value;
    }

    /** Whether {@code raw} would normalize, without throwing. Used where a bad code is an ordinary answer. */
    static boolean isValid(String raw) {
        try {
            normalize(raw, "code");
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /**
     * Turns a name into a code prefix: {@code "Nguyễn Văn"} becomes {@code "NGUYENVAN"}.
     *
     * <p>Diacritics are stripped rather than kept. A code with a {@code ễ} in it cannot be typed on
     * most keyboards, dictated over the phone, or matched against what someone actually enters —
     * and this is the one string in the product that has to survive all three.
     *
     * @param fallback used when the name contributes no usable characters at all
     */
    static String toPrefix(String name, String fallback) {
        String source = name == null ? "" : name;
        String decomposed = Normalizer.normalize(source, Normalizer.Form.NFD);
        String unaccented = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        // đ and Đ carry their stroke inside the letter rather than as a combining mark, so NFD
        // leaves them untouched and they have to be spelled out by hand.
        String latinized = unaccented.replace('đ', 'd').replace('Đ', 'D');
        String stripped =
                NOT_CODE_CHARACTER.matcher(latinized.toUpperCase(Locale.ROOT)).replaceAll("");
        String prefix = stripped.isEmpty() ? fallback : stripped;
        return prefix.length() > MAX_PREFIX_LENGTH ? prefix.substring(0, MAX_PREFIX_LENGTH) : prefix;
    }
}
