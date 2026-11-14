package app.giaky.identity.domain;

import java.time.Instant;

/**
 * Gets a verification or reset token to the person who asked for it.
 *
 * <p>A port, so the transport is a deployment choice: logged in {@code dev} and {@code test}, sent by
 * SMTP where a mail server is configured, and dropped with a warning where none is.
 *
 * <p>Implementations must not throw. A delivery failure cannot be allowed to roll back the account
 * change that preceded it — a user whose registration failed because the mail queue was down would
 * be unable to retry, since the address is now taken.
 */
public interface VerificationTokenDelivery {

    /**
     * @param recipient the address on the account
     * @param purpose what the token authorises, which decides the message
     * @param rawToken the token itself — the only moment it exists outside the client
     * @param expiresAt when it stops working, so the message can say so
     */
    void deliver(EmailAddress recipient, TokenPurpose purpose, String rawToken, Instant expiresAt);
}
