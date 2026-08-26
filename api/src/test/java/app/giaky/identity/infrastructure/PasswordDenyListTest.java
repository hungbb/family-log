package app.giaky.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import app.giaky.identity.domain.PasswordPolicy;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

/**
 * The deny list that actually ships, rather than the three-entry set the domain test uses.
 *
 * <p>It matters more than it looks. FR-A2 puts the floor at eight characters, which makes every
 * eight- and nine-character password legal on length alone — and that is precisely the length at
 * which {@code password} and {@code 12345678} live. Length is not what stops those; this file is.
 */
class PasswordDenyListTest {

    private final PasswordPolicy policy = new IdentityConfig().passwordPolicy();

    @Test
    @DisplayName("the shipped list loads and is not empty")
    void listLoads() {
        // A resource that failed to load would leave an empty deny list and a policy that quietly
        // accepts everything long enough. IdentityConfig fails fast instead; this proves it found
        // the file at all.
        assertThat(entries()).hasSizeGreaterThan(50);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "password",
                "12345678",
                "iloveyou",
                "qwerty123",
                "p@ssw0rd",
                "matkhau1",
                "vietnam1",
                "PassWord",
                "IloveYou"
            })
    @DisplayName("the passwords everyone picks are refused, whatever their case")
    void commonPasswordsAreRefused(String candidate) {
        assertThat(policy.isAcceptable(candidate))
                .as("%s is eight characters, so only the deny list can refuse it", candidate)
                .isFalse();
    }

    @Test
    @DisplayName("no entry is shorter than the floor, so none of them is dead weight")
    void noEntryIsUnreachable() {
        // An entry below MIN_LENGTH can never match: the length rule rejects the candidate first.
        // It would look like protection and provide none, which is worse than an absence.
        assertThat(entries())
                .allSatisfy(
                        entry ->
                                assertThat(entry.length())
                                        .as("%s is unreachable below the length floor", entry)
                                        .isGreaterThanOrEqualTo(PasswordPolicy.MIN_LENGTH));
    }

    @Test
    @DisplayName("every entry is already lowercase, which is how the comparison finds it")
    void everyEntryIsNormalized() {
        assertThat(entries())
                .allSatisfy(entry -> assertThat(entry).isEqualTo(entry.toLowerCase(Locale.ROOT)));
    }

    @Test
    @DisplayName("an ordinary passphrase still gets through")
    void goodPasswordsAreAccepted() {
        assertThat(policy.validate("con-ngua-dung-pin")).isEmpty();
        assertThat(policy.validate("nha tho ho Nguyen 1897")).isEmpty();
    }

    private static List<String> entries() {
        ClassPathResource resource = new ClassPathResource("identity/common-passwords.txt");
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        } catch (Exception unreadable) {
            throw new IllegalStateException("Could not read the deny list", unreadable);
        }
    }
}
