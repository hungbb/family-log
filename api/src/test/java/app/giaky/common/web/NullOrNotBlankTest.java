package app.giaky.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link NullOrNotBlank} — the constraint on a name a {@code PATCH} may omit but may not empty.
 *
 * <p>It has to agree with the domain constructors about what "blank" is, because the whole reason
 * it exists is that their refusal reaches the client as a 500.
 */
class NullOrNotBlankTest {

    private final NullOrNotBlank.Validator validator = new NullOrNotBlank.Validator();

    @Test
    @DisplayName("absent is allowed — a PATCH that does not mention the name leaves it alone")
    void nullIsValid() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", " ", "   ", "\t\n", "　", "   "})
    @DisplayName("sent but empty of anything except whitespace is refused")
    void blankIsInvalid(String value) {
        assertThat(validator.isValid(value, null)).isFalse();
        assertThat(value.isBlank()).as("the same test the aggregates apply").isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"An", " Nguyễn Văn An ", "ấ", "0"})
    @DisplayName("anything with a character in it passes")
    void textIsValid(String value) {
        assertThat(validator.isValid(value, null)).isTrue();
    }
}
