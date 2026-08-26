package app.giaky.common.security;

import app.giaky.common.types.UserId;
import java.util.Optional;

/**
 * Looks up the account behind a presented access token.
 *
 * <p>Declared here and implemented in {@code identity.infrastructure}, so the filter chain does not
 * import a bounded context and Identity keeps ownership of what "may authenticate" means (I-U2).
 *
 * <p>This is a read per authenticated request. That is the price of {@code tokenVersion} being
 * authoritative: without it, "log out everywhere" and a password reset would leave every
 * outstanding access token valid until it expired on its own. Phase 8 revisits it with a
 * short-lived cache if the profile says it matters.
 */
public interface AuthenticatedUserPort {

    /**
     * The account, if it exists and its status permits authenticating (I-U2).
     *
     * <p>A suspended or deactivated account returns empty, which the filter reports as an invalid
     * token — the same answer a forged token gets, because telling the two apart is information the
     * caller has not earned.
     */
    Optional<AuthenticatedUser> findAuthenticatable(UserId userId);
}
