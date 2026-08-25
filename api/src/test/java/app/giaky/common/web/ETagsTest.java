package app.giaky.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ETagsTest {

    @Test
    @DisplayName("a version renders as a strong entity tag")
    void formatsAVersion() {
        assertThat(ETags.of(42)).isEqualTo("\"v42\"");
    }

    @Test
    @DisplayName("a matching If-None-Match is recognised")
    void matchesTheCurrentVersion() {
        assertThat(ETags.matches("\"v42\"", 42)).isTrue();
        assertThat(ETags.matches("\"v41\"", 42)).isFalse();
    }

    @Test
    @DisplayName("a weak validator still identifies the same representation")
    void matchesWeakValidators() {
        assertThat(ETags.matches("W/\"v42\"", 42)).isTrue();
    }

    @Test
    @DisplayName("any entry of a comma-separated list may match")
    void matchesWithinAList() {
        assertThat(ETags.matches("\"v40\", \"v41\", \"v42\"", 42)).isTrue();
        assertThat(ETags.matches("\"v40\", \"v41\"", 42)).isFalse();
    }

    @Test
    @DisplayName("* matches anything that exists")
    void matchesWildcard() {
        assertThat(ETags.matches("*", 1)).isTrue();
    }

    @Test
    @DisplayName("an absent or blank header never matches")
    void absentHeaderDoesNotMatch() {
        assertThat(ETags.matches(null, 42)).isFalse();
        assertThat(ETags.matches("   ", 42)).isFalse();
    }

    @Test
    @DisplayName("a version is not confused with a prefix of another")
    void doesNotMatchOnPrefix() {
        // "v4" must not satisfy a request holding version 42, and vice versa.
        assertThat(ETags.matches("\"v4\"", 42)).isFalse();
        assertThat(ETags.matches("\"v42\"", 4)).isFalse();
    }
}
