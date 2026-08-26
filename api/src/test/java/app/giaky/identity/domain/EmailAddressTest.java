package app.giaky.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Normalization is the point of this type (I-U1).
 *
 * <p>If {@code An@Example.com } and {@code an@example.com} could both be stored, uniqueness would
 * mean nothing and the second registration would be an account-takeover route against the first.
 */
class EmailAddressTest {

    @Test
    @DisplayName("trims surrounding whitespace and lowercases")
    void normalizes() {
        EmailAddress email = EmailAddress.of("  An.Nguyen@Example.COM  ");

        assertThat(email.value()).isEqualTo("an.nguyen@example.com");
    }

    @Test
    @DisplayName("two spellings of one address are the same value")
    void differingSpellingsAreEqual() {
        assertThat(EmailAddress.of("AN@EXAMPLE.COM")).isEqualTo(EmailAddress.of(" an@example.com "));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "an@example.com",
                "an.nguyen+giapha@example.co.uk",
                "a@b.io",
                "nguyen_van_a@ten-mien.vn"
            })
    @DisplayName("accepts the shapes real addresses come in")
    void acceptsValidAddresses(String candidate) {
        assertThat(EmailAddress.isValid(candidate)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "no-at-sign", "@example.com", "an@", "an@example", "a b@c.com", "an@@example.com"})
    @DisplayName("rejects the shapes that are certainly not addresses")
    void rejectsInvalidAddresses(String candidate) {
        assertThat(EmailAddress.isValid(candidate)).isFalse();
        assertThatThrownBy(() -> EmailAddress.of(candidate))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects null")
    void rejectsNull() {
        assertThat(EmailAddress.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("rejects anything longer than the column")
    void rejectsOverlongAddresses() {
        String local = "a".repeat(EmailAddress.MAX_LENGTH);

        assertThatThrownBy(() -> EmailAddress.of(local + "@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(EmailAddress.MAX_LENGTH));
    }

    @Test
    @DisplayName("toString is the address, so log lines read naturally")
    void toStringIsTheValue() {
        assertThat(EmailAddress.of("an@example.com")).hasToString("an@example.com");
    }
}
