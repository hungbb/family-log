package app.giaky.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @Nested
    @DisplayName("a tag for a representation that varies by more than its version (#49)")
    class Variants {

        @Test
        @DisplayName("names the version, then a fixed-width fingerprint of the rest")
        void shape() {
            assertThat(ETags.ofVariant(42, "CLAN:null")).matches("\"v42\\.[0-9a-f]{16}\"");
        }

        @Test
        @DisplayName("the same discriminators always give the same tag")
        void stable() {
            assertThat(ETags.ofVariant(42, "a", "b")).isEqualTo(ETags.ofVariant(42, "a", "b"));
        }

        @Test
        @DisplayName("a different caller at the same version gets a different tag")
        void differentDiscriminatorsDiffer() {
            // The defect: one clan, one version, two representations. A plain member must not
            // revalidate into an editor's canEdit flags.
            assertThat(ETags.ofVariant(42, "CLAN:null")).isNotEqualTo(ETags.ofVariant(42, ""));
        }

        @Test
        @DisplayName("a different version with the same caller gets a different tag")
        void versionStillCounts() {
            assertThat(ETags.ofVariant(42, "a")).isNotEqualTo(ETags.ofVariant(43, "a"));
        }

        @Test
        @DisplayName("the discriminators are separated, so a boundary cannot be moved")
        void separatorPreventsSmuggling() {
            assertThat(ETags.ofVariant(1, "ab", "c")).isNotEqualTo(ETags.ofVariant(1, "a", "bc"));
        }

        @Test
        @DisplayName("an absent discriminator is distinct from an empty one")
        void nullIsNotEmpty() {
            assertThat(ETags.ofVariant(1, (String) null)).isNotEqualTo(ETags.ofVariant(1, ""));
        }

        @Test
        @DisplayName("a variant tag never collides with the bare version it is built on")
        void doesNotCollideWithTheBareVersion() {
            assertThat(ETags.ofVariant(42, "a")).isNotEqualTo(ETags.of(42));
            assertThat(ETags.matches(ETags.of(42), ETags.ofVariant(42, "a"))).isFalse();
        }

        @Test
        @DisplayName("matching compares against the whole tag, list and weak forms included")
        void matchesAgainstATag() {
            String tag = ETags.ofVariant(42, "a");

            assertThat(ETags.matches(tag, tag)).isTrue();
            assertThat(ETags.matches("W/" + tag, tag)).isTrue();
            assertThat(ETags.matches("\"v1\", " + tag, tag)).isTrue();
            assertThat(ETags.matches("*", tag)).isTrue();
            assertThat(ETags.matches(ETags.ofVariant(42, "b"), tag)).isFalse();
            assertThat(ETags.matches(null, tag)).isFalse();
            assertThat(ETags.matches("  ", tag)).isFalse();
        }
    }
}
