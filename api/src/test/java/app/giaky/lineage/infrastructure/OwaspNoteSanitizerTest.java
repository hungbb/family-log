package app.giaky.lineage.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import app.giaky.lineage.domain.NoteSanitizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link OwaspNoteSanitizer} — B4.13, and the reason {@code notes_html} can be rendered as HTML.
 *
 * <p>Notes are the one free-text field a family pastes into from a word processor, so the policy has
 * to keep enough markup to be worth having and drop everything that could execute.
 */
class OwaspNoteSanitizerTest {

    private final NoteSanitizer sanitizer = new OwaspNoteSanitizer();

    @Test
    @DisplayName("ordinary formatting survives")
    void keepsFormatting() {
        NoteSanitizer.SanitizedNote note =
                sanitizer.sanitize("<p>Di cư vào Nam năm <b>1954</b>.</p><ul><li>Quê: Nam Định</li></ul>");

        assertThat(note.html()).contains("<p>", "<b>", "<li>");
        assertThat(note.html()).contains("Di cư vào Nam năm");
    }

    @Test
    @DisplayName("scripts, handlers and styles are dropped")
    void dropsExecutableMarkup() {
        NoteSanitizer.SanitizedNote note =
                sanitizer.sanitize(
                        "<p onclick=\"steal()\">Ghi chú</p>"
                                + "<script>alert(1)</script>"
                                + "<style>body{display:none}</style>"
                                + "<iframe src=\"//evil\"></iframe>");

        assertThat(note.html())
                .doesNotContain("script", "onclick", "iframe", "style")
                .contains("Ghi chú");
    }

    @Test
    @DisplayName("a javascript: link keeps its text and loses its href")
    void dropsDangerousProtocols() {
        NoteSanitizer.SanitizedNote note =
                sanitizer.sanitize("<a href=\"javascript:alert(1)\">bấm vào đây</a>");

        assertThat(note.html()).doesNotContain("javascript").contains("bấm vào đây");
    }

    @Test
    @DisplayName("an http link survives and is marked nofollow")
    void keepsSafeLinks() {
        NoteSanitizer.SanitizedNote note =
                sanitizer.sanitize("<a href=\"https://example.org/gia-pha\">nguồn</a>");

        assertThat(note.html()).contains("https://example.org/gia-pha").contains("nofollow");
    }

    @Test
    @DisplayName("images are dropped — media has its own table and its own permissions")
    void dropsImages() {
        NoteSanitizer.SanitizedNote note =
                sanitizer.sanitize("<p>Ảnh:</p><img src=\"https://tracker.example/pixel.gif\">");

        assertThat(note.html()).doesNotContain("img");
    }

    @Test
    @DisplayName("the plain projection separates blocks and decodes entities")
    void plainProjection() {
        NoteSanitizer.SanitizedNote note =
                sanitizer.sanitize("<p>Di cư</p><p>năm 1954</p><p>Ông &amp; bà</p>");

        assertThat(note.text())
                .as("without the block boundary this would index the word 'cưnăm'")
                .isEqualTo("Di cư năm 1954 Ông & bà");
    }

    @Test
    @DisplayName("a note of nothing but disallowed markup is a note of nothing")
    void emptyResults() {
        assertThat(sanitizer.sanitize(null).isEmpty()).isTrue();
        assertThat(sanitizer.sanitize("   ").isEmpty()).isTrue();
        assertThat(sanitizer.sanitize("<script>alert(1)</script>").isEmpty()).isTrue();
    }
}
