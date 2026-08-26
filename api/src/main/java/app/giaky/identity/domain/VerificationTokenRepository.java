package app.giaky.identity.domain;

import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.Optional;

/** Storage for email-verification and password-reset tokens. */
public interface VerificationTokenRepository {

    /** Lookup by stored hash — the only way in, since the raw token is never persisted. */
    Optional<VerificationToken> findByTokenHash(String tokenHash);

    VerificationToken save(VerificationToken token);

    /**
     * Spends every outstanding token of one purpose for an account.
     *
     * <p>Called before issuing a new one, so requesting a second reset link invalidates the first.
     * Otherwise every reset request would leave another live takeover credential in the inbox.
     *
     * @return how many were consumed
     */
    int consumeOutstanding(UserId userId, TokenPurpose purpose, Instant now);
}
