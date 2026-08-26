package app.giaky.identity.api.dto;

/**
 * The locale tags the API accepts, as a Bean Validation pattern.
 *
 * <p>Request DTOs carry the tag as a {@code String} rather than as {@code LocaleTag} because the
 * domain enum may not depend on Jackson (doc 06 §2.1), and mapping it by constant name would put
 * {@code VI_VN} on the wire where doc 03 §3.1 specifies {@code vi-VN}. Validating the string here
 * turns an unknown tag into a 422 with a field error instead of a deserialization failure the
 * client cannot act on.
 */
public final class LocalePattern {

    /** Kept in step with {@code LocaleTag}; a new language changes both. */
    public static final String REGEX = "vi-VN|en-US";

    public static final String MESSAGE = "must be one of: vi-VN, en-US";

    private LocalePattern() {}
}
