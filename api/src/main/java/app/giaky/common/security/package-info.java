/**
 * The Spring Security filter chain, the authenticated principal and the current-user resolver.
 *
 * <p>Access tokens are minted and verified here rather than inside {@code identity} because the
 * filter chain is not part of any bounded context and may not import one. Identity supplies the
 * account behind a token through {@link app.giaky.common.security.AuthenticatedUserPort}; the
 * dependency runs that way and never the other.
 *
 * <p><b>Rule:</b> this package decides <i>who</i> the caller is. It never decides what they may do —
 * that lives in {@code authorization} and is checked inside application services (doc 04 §5.1).
 */
package app.giaky.common.security;
