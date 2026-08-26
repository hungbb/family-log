package app.giaky.identity.application;

import app.giaky.identity.domain.LocaleTag;

/**
 * A partial profile update (FR-A4).
 *
 * <p>A null field means "leave it alone", which is what an absent field in a {@code PATCH} body
 * means. Neither field has a meaningful empty value, so nothing here can be cleared.
 */
public record UpdateProfileCommand(String displayName, LocaleTag locale) {}
