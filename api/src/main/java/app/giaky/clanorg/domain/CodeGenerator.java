package app.giaky.clanorg.domain;

import java.security.SecureRandom;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Mints {@link ClanCode}s and {@link BranchCode}s (doc 02 §5).
 *
 * <p>Shape is {@code SURNAME-XXXX}: a prefix anyone will recognize, and four Crockford base32
 * characters that make it unguessable enough to share. Roughly a million codes per surname, which is
 * far more than any surname will ever need and few enough that a collision must be handled rather
 * than assumed away — hence the retry.
 *
 * <p><b>The suffix is drawn from a {@link SecureRandom}.</b> A clan code is a bearer credential in
 * everything but name: holding one is what lets a stranger ask to join. A predictable suffix would
 * turn "ask to join this family" into "enumerate every family on the platform", which is precisely
 * what doc 04 §7 answers 404s to prevent.
 *
 * <p>Framework-free and takes its randomness by constructor, so a test can hand it a fixed sequence
 * and assert on the exact code, and the collision path can be exercised without waiting for one.
 */
public final class CodeGenerator {

    /**
     * How many times to redraw before giving up.
     *
     * <p>At a million codes per prefix, ten consecutive collisions means either a surname with
     * hundreds of thousands of clans or a broken random source. Both are worth failing loudly on
     * rather than looping.
     */
    public static final int MAX_ATTEMPTS = 10;

    private final RandomGenerator random;

    /** Production wiring: a {@link SecureRandom}. */
    public CodeGenerator() {
        this(new SecureRandom());
    }

    public CodeGenerator(RandomGenerator random) {
        this.random = random;
    }

    /**
     * A code for a clan named after {@code surname}.
     *
     * @param surname the family name; diacritics are stripped, so <i>Nguyễn</i> yields {@code NGUYEN}
     * @param isTaken answers whether a candidate is already in use — including by a deleted clan
     * @throws IllegalStateException {@link #MAX_ATTEMPTS} candidates were all taken
     */
    public ClanCode newClanCode(String surname, Predicate<String> isTaken) {
        return ClanCode.of(generate(CodeFormat.toPrefix(surname, "CLAN"), isTaken));
    }

    /**
     * A code for a branch.
     *
     * @param branchName the branch name; <i>Chi Trưởng</i> yields {@code CHITRUONG}
     */
    public BranchCode newBranchCode(String branchName, Predicate<String> isTaken) {
        return BranchCode.of(generate(CodeFormat.toPrefix(branchName, "CHI"), isTaken));
    }

    private String generate(String prefix, Predicate<String> isTaken) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = prefix + "-" + suffix();
            if (!isTaken.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "could not find a free code for prefix "
                        + prefix
                        + " in "
                        + MAX_ATTEMPTS
                        + " attempts");
    }

    private String suffix() {
        StringBuilder builder = new StringBuilder(CodeFormat.SUFFIX_LENGTH);
        for (int i = 0; i < CodeFormat.SUFFIX_LENGTH; i++) {
            builder.append(CodeFormat.ALPHABET[random.nextInt(CodeFormat.ALPHABET.length)]);
        }
        return builder.toString();
    }
}
