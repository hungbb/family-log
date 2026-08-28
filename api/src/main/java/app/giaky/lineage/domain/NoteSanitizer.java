package app.giaky.lineage.domain;

/**
 * Cleans the rich text a person's notes may contain (B4.13, doc 02 §3).
 *
 * <p>A port, so the domain can state <i>that</i> notes are sanitized on write without depending on
 * the library that does it. The adapter lives in infrastructure over the OWASP Java HTML Sanitizer.
 *
 * <p><b>On write, never on read.</b> Sanitizing on the way out means every read pays for it, every
 * cache holds unsanitized text, and one path that forgets is a stored XSS. Sanitizing on the way in
 * means the column contains only what the allow-list permits, and the worst a bug downstream can do
 * is render safe markup.
 *
 * <p>The plain-text projection travels with the HTML for a related reason: search and excerpts must
 * never strip tags themselves, because a stripper is a parser, and a second parser is a second set
 * of parsing bugs.
 */
public interface NoteSanitizer {

    /**
     * @param rawHtml what the client sent; null or blank yields an empty result
     * @return the allow-listed HTML and its plain-text projection
     */
    SanitizedNote sanitize(String rawHtml);

    /**
     * The two forms of one note.
     *
     * @param html what is stored in {@code notes_html} and rendered
     * @param text what is stored in {@code notes_text}, for search and excerpts
     */
    record SanitizedNote(String html, String text) {

        /** No note at all — what a null or empty input produces, and what a cleared field writes. */
        public static SanitizedNote empty() {
            return new SanitizedNote(null, null);
        }

        public boolean isEmpty() {
            return html == null || html.isEmpty();
        }
    }
}
