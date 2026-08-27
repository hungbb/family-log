package app.giaky.clanorg.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link CodeGenerator}: the shape of a code, and what happens when one is already taken. */
class CodeGeneratorTest {

    private static final Predicate<String> NOTHING_TAKEN = candidate -> false;

    private final CodeGenerator generator = new CodeGenerator(new Random(42L));

    @Test
    @DisplayName("a clan code is SURNAME-XXXX with the diacritics stripped")
    void clanCodeShape() {
        ClanCode code = generator.newClanCode("Nguyễn", NOTHING_TAKEN);

        assertThat(code.value()).startsWith("NGUYEN-");
        assertThat(code.value()).matches("^NGUYEN-[0-9A-HJKMNP-TV-Z]{4}$");
    }

    @Test
    @DisplayName("đ becomes d — NFD leaves its stroke inside the letter")
    void strokedDIsLatinized() {
        assertThat(generator.newClanCode("Đặng", NOTHING_TAKEN).value()).startsWith("DANG-");
    }

    @Test
    @DisplayName("spaces and punctuation are dropped from the prefix; the letters survive")
    void prefixIsAlphanumericOnly() {
        // Everything a keyboard would struggle with goes; nothing a person would recognize does.
        assertThat(generator.newBranchCode("Chi Trưởng (cũ)", NOTHING_TAKEN).value())
                .startsWith("CHITRUONGCU-");
    }

    @Test
    @DisplayName("a name that contributes nothing usable falls back")
    void fallbackPrefix() {
        assertThat(generator.newClanCode("…", NOTHING_TAKEN).value()).startsWith("CLAN-");
        assertThat(generator.newBranchCode("", NOTHING_TAKEN).value()).startsWith("CHI-");
    }

    @Test
    @DisplayName("a long name is truncated to fit the column")
    void prefixIsTruncated() {
        String code = generator.newClanCode("A".repeat(40), NOTHING_TAKEN).value();

        assertThat(code).hasSize(CodeFormat.MAX_PREFIX_LENGTH + 1 + CodeFormat.SUFFIX_LENGTH);
        assertThat(code.length()).isLessThanOrEqualTo(CodeFormat.MAX_LENGTH);
    }

    @Test
    @DisplayName("the suffix never uses I, L, O or U")
    void suffixAvoidsAmbiguousLetters() {
        // Those four are exactly the characters a person reading a code aloud gets wrong.
        CodeGenerator many = new CodeGenerator(new Random(7L));
        for (int i = 0; i < 500; i++) {
            String suffix = many.newClanCode("LE", NOTHING_TAKEN).value().substring(3);
            assertThat(suffix).doesNotContain("I").doesNotContain("L");
            assertThat(suffix).doesNotContain("O").doesNotContain("U");
        }
    }

    @Test
    @DisplayName("a taken code is redrawn rather than reissued")
    void collisionsAreRetried() {
        Set<String> taken = new HashSet<>();
        // Whatever the first two draws are, they are already in use.
        CodeGenerator seeded = new CodeGenerator(new Random(42L));
        taken.add(seeded.newClanCode("TRAN", NOTHING_TAKEN).value());
        taken.add(seeded.newClanCode("TRAN", NOTHING_TAKEN).value());

        CodeGenerator replay = new CodeGenerator(new Random(42L));
        ClanCode issued = replay.newClanCode("TRAN", taken::contains);

        assertThat(taken).doesNotContain(issued.value());
    }

    @Test
    @DisplayName("a space with no room left fails loudly rather than looping")
    void exhaustionThrows() {
        assertThatThrownBy(() -> generator.newClanCode("HO", candidate -> true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.valueOf(CodeGenerator.MAX_ATTEMPTS));
    }

    @Test
    @DisplayName("a code is normalized on the way in, so lookup is an equality check")
    void codesNormalize() {
        assertThat(ClanCode.of("  nguyen-7f3k  ").value()).isEqualTo("NGUYEN-7F3K");
        assertThat(BranchCode.of("chihai-9q2m")).isEqualTo(BranchCode.of("CHIHAI-9Q2M"));
    }

    @Test
    @DisplayName("something that is not a code is rejected rather than accepted and never matched")
    void malformedCodesAreRejected() {
        assertThat(ClanCode.isValid("NGUYEN-7F3K")).isTrue();
        // I, L, O and U are not in the alphabet, so a code containing one was mistyped.
        assertThat(ClanCode.isValid("NGUYEN-7I3K")).isFalse();
        assertThat(ClanCode.isValid("NGUYEN")).isFalse();
        assertThat(ClanCode.isValid("-7F3K")).isFalse();
        assertThat(ClanCode.isValid(null)).isFalse();
    }
}
