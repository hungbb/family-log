package app.giaky.identity.domain;

import java.time.Duration;

/**
 * What a {@link VerificationToken} authorises, and for how long (FR-A6).
 *
 * <p>The two are one enum because a token is only ever valid for the purpose it was issued for: a
 * verification link that could also reset a password would turn a forwarded welcome email into an
 * account takeover.
 */
public enum TokenPurpose {

    /** Confirms the address on the account. 24 hours. */
    EMAIL_VERIFY,

    /** Authorises setting a new password without the old one. One hour — it is a bearer credential. */
    PASSWORD_RESET;

    /** The lifetime for this purpose, resolved from configuration by the application service. */
    public Duration lifetime(Duration emailVerifyTtl, Duration passwordResetTtl) {
        return this == EMAIL_VERIFY ? emailVerifyTtl : passwordResetTtl;
    }
}
