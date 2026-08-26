package app.giaky.identity.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * The languages the product ships in (NFR-8).
 *
 * <p>A closed set rather than a free {@code String}: the interface only exists in these two, so a
 * profile storing {@code fr-FR} would render Vietnamese anyway and only confuse the person who set
 * it. Vietnamese is the default and the reference locale — English is the translation, not the
 * other way round.
 *
 * <p>Persisted as the tag ({@code vi-VN}), not the constant name, matching the {@code CHECK} on
 * {@code users.locale}.
 */
public enum LocaleTag {
    VI_VN("vi-VN"),
    EN_US("en-US");

    /** What a new account gets, and what an unrecognised stored value falls back to. */
    public static final LocaleTag DEFAULT = VI_VN;

    private final String tag;

    LocaleTag(String tag) {
        this.tag = tag;
    }

    /** The BCP 47 tag, as stored and as sent to the client. */
    public String tag() {
        return tag;
    }

    public static Optional<LocaleTag> fromTag(String value) {
        return Arrays.stream(values()).filter(locale -> locale.tag.equals(value)).findFirst();
    }

    /**
     * As {@link #fromTag}, falling back to {@link #DEFAULT}.
     *
     * <p>For reading rows written before a tag was retired. A stored value nobody recognises should
     * render the product in its default language, not fail the request.
     */
    public static LocaleTag fromTagOrDefault(String value) {
        return fromTag(value).orElse(DEFAULT);
    }
}
