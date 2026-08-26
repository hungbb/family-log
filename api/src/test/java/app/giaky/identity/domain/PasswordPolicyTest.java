package app.giaky.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FR-A2 and task B1.2: length, and not one of the passwords everybody uses. */
class PasswordPolicyTest {

    private final PasswordPolicy policy =
            new PasswordPolicy(Set.of("password123", "qwertyuiop", "matkhau123"));

    @Test
    @DisplayName("accepts a long, unremarkable password")
    void acceptsAcceptablePassword() {
        assertThat(policy.validate("con-ngua-dung-pin-2026")).isEmpty();
        assertThat(policy.isAcceptable("con-ngua-dung-pin-2026")).isTrue();
    }

    @Test
    @DisplayName("accepts exactly the minimum length")
    void acceptsMinimumLength() {
        assertThat(policy.validate("a".repeat(PasswordPolicy.MIN_LENGTH))).isEmpty();
    }

    @Test
    @DisplayName("rejects one character below the minimum")
    void rejectsShortPassword() {
        assertThat(policy.validate("a".repeat(PasswordPolicy.MIN_LENGTH - 1)))
                .singleElement()
                .asString()
                .contains(String.valueOf(PasswordPolicy.MIN_LENGTH));
    }

    @Test
    @DisplayName("rejects anything past BCrypt's 72-byte ceiling rather than silently truncating")
    void rejectsOverlongPassword() {
        assertThat(policy.validate("a".repeat(PasswordPolicy.MAX_LENGTH + 1)))
                .singleElement()
                .asString()
                .contains("at most");
    }

    @Test
    @DisplayName("rejects a denied password whatever its case")
    void rejectsDeniedPasswordCaseInsensitively() {
        assertThat(policy.validate("PassWord123")).singleElement().asString().contains("too common");
        assertThat(policy.isAcceptable("qwertyuiop")).isFalse();
        assertThat(policy.isAcceptable("MatKhau123")).isFalse();
    }

    @Test
    @DisplayName("a password that merely contains a denied one is fine")
    void deniedEntriesAreNotSubstrings() {
        // The list blocks whole passwords. Matching substrings would reject
        // "password123-and-then-some", which is a perfectly good passphrase.
        assertThat(policy.validate("password123-and-then-some")).isEmpty();
    }

    @Test
    @DisplayName("reports every violation at once, so a form can show them together")
    void reportsAllViolations() {
        PasswordPolicy strict = new PasswordPolicy(Set.of("short"));

        assertThat(strict.validate("short")).hasSize(2);
    }

    @Test
    @DisplayName("an empty or null password is a single, unambiguous violation")
    void rejectsEmptyAndNull() {
        assertThat(policy.validate("")).singleElement().asString().contains("must not be empty");
        assertThat(policy.validate(null)).singleElement().asString().contains("must not be empty");
    }
}
