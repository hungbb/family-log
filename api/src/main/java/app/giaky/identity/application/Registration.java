package app.giaky.identity.application;

import app.giaky.common.types.UserId;
import app.giaky.identity.domain.UserStatus;

/**
 * The outcome of registering (doc 05 §4.1).
 *
 * <p>No tokens: registration does not sign anyone in. The client sends the credentials to
 * {@code /auth/login} straight afterwards, which keeps one code path for "becoming signed in" and
 * means a registration that half-succeeded cannot leave a session behind.
 */
public record Registration(UserId userId, UserStatus status) {}
