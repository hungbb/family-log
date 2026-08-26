package app.giaky.identity.application;

import app.giaky.identity.domain.LocaleTag;

/**
 * @param email raw from the request; normalized by {@code EmailAddress}
 * @param password raw; validated against the policy, hashed, and never stored or logged
 * @param displayName what other members will see
 * @param locale the interface language; null falls back to Vietnamese
 * @param client where the request came from, for the audit entry
 */
public record RegisterCommand(
        String email, String password, String displayName, LocaleTag locale, ClientContext client) {}
