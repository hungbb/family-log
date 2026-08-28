package app.giaky.lineage.infrastructure;

import app.giaky.lineage.domain.NoteSanitizer;
import java.util.regex.Pattern;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * {@link NoteSanitizer} over the OWASP Java HTML Sanitizer (B4.13).
 *
 * <p><b>An allow-list, not a block-list.</b> The policy names the elements and attributes that may
 * survive; everything else is dropped, including anything invented after this was written. A
 * block-list is a list of the attacks somebody had thought of.
 *
 * <p><b>What the policy allows, and why.</b> Notes are what a family pastes out of a Word document —
 * paragraphs, line breaks, bold, italic, lists, the occasional link and heading. That is the whole
 * list. No images (Phase 7 owns media, through {@code media_objects}, so an arbitrary {@code <img
 * src>} would be both an XSS surface and a tracking pixel), no tables, no styles, no classes, no
 * ids. A link keeps only {@code href}, restricted to {@code http}/{@code https}/{@code mailto}, and
 * gets {@code rel="nofollow noopener noreferrer"} added on the way out.
 *
 * <p>Phase 7 replaces the plain textarea in front of this with a rich-text editor. The policy is
 * what decides what that editor is allowed to produce, so it lands here first rather than with the
 * editor.
 */
@Component
public class OwaspNoteSanitizer implements NoteSanitizer {

    /**
     * The allow-list.
     *
     * <p>{@code PolicyFactory} is immutable and thread-safe, so it is built once. Composing the
     * blocks with {@code and} keeps each concern legible on its own line.
     */
    private static final PolicyFactory POLICY =
            new HtmlPolicyBuilder()
                    .allowCommonInlineFormattingElements()
                    .allowElements(
                            "p", "br", "ul", "ol", "li", "blockquote", "h3", "h4", "hr", "span")
                    .allowElements("a")
                    .allowAttributes("href")
                    .onElements("a")
                    .allowUrlProtocols("http", "https", "mailto")
                    .requireRelNofollowOnLinks()
                    .toFactory();

    /**
     * Tags after which a word boundary must appear in the plain-text projection.
     *
     * <p>Without this, {@code <p>Di cư</p><p>năm 1954</p>} flattens to {@code Di cưnăm 1954} and the
     * search index learns a word nobody will ever type.
     */
    private static final Pattern BLOCK_BOUNDARY =
            Pattern.compile("(?i)</(p|div|li|ul|ol|blockquote|h3|h4)>|<br\\s*/?>|<hr\\s*/?>");

    /** Any remaining tag. Applied only to output this class produced, never to raw input. */
    private static final Pattern ANY_TAG = Pattern.compile("<[^>]*>");

    private static final Pattern REPEATED_WHITESPACE = Pattern.compile("\\s{2,}");

    @Override
    public SanitizedNote sanitize(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return SanitizedNote.empty();
        }
        String html = POLICY.sanitize(rawHtml);
        if (html.isBlank()) {
            // Everything the caller sent was disallowed markup — a note of nothing but a script tag
            // is a note of nothing.
            return SanitizedNote.empty();
        }
        String text = toPlainText(html);
        return new SanitizedNote(html, text.isBlank() ? null : text);
    }

    /**
     * The plain-text projection of already-sanitized HTML.
     *
     * <p>A regex, which would be indefensible against untrusted input and is fine here: the input is
     * this class's own output, produced by a parser, with every stray angle bracket already encoded.
     * The alternative — a second HTML parser — would be a second parser's worth of bugs for a string
     * that only ever feeds search and an excerpt.
     */
    private static String toPlainText(String sanitizedHtml) {
        String spaced = BLOCK_BOUNDARY.matcher(sanitizedHtml).replaceAll(" ");
        String stripped = ANY_TAG.matcher(spaced).replaceAll("");
        String decoded =
                stripped
                        .replace("&nbsp;", " ")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .replace("&#34;", "\"")
                        .replace("&#39;", "'")
                        // Last, so a literal "&amp;lt;" in the source does not become "<".
                        .replace("&amp;", "&");
        return REPEATED_WHITESPACE.matcher(decoded).replaceAll(" ").trim();
    }
}
