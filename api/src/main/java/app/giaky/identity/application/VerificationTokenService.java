package app.giaky.identity.application;

import app.giaky.common.error.AuthenticationRequiredException;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.security.SecurityProperties;
import app.giaky.common.types.IdGenerator;
import app.giaky.common.types.UserId;
import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.TokenPurpose;
import app.giaky.identity.domain.VerificationToken;
import app.giaky.identity.domain.VerificationTokenDelivery;
import app.giaky.identity.domain.VerificationTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and spends the single-use tokens behind email verification and password reset (task B1.9).
 *
 * <p>Issuing invalidates any outstanding token of the same purpose for that account. Without that,
 * asking for three reset links would leave three live account-takeover credentials sitting in an
 * inbox, and the oldest would be the one an attacker with historical mailbox access already has.
 */
@Service
public class VerificationTokenService {

    private final VerificationTokenRepository tokens;
    private final VerificationTokenDelivery delivery;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final Duration emailVerifyTtl;
    private final Duration passwordResetTtl;

    public VerificationTokenService(
            VerificationTokenRepository tokens,
            VerificationTokenDelivery delivery,
            IdGenerator idGenerator,
            Clock clock,
            SecurityProperties properties) {
        this.tokens = tokens;
        this.delivery = delivery;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.emailVerifyTtl = properties.verification().emailVerifyTtl();
        this.passwordResetTtl = properties.verification().passwordResetTtl();
    }

    /**
     * Issues a token and hands it to delivery.
     *
     * <p>Called from inside the caller's transaction, so a registration that fails after this point
     * leaves no token behind. Delivery itself does not throw (see the port), which is why a mail
     * outage cannot roll back an account change.
     */
    @Transactional
    public void issue(UserId userId, EmailAddress recipient, TokenPurpose purpose) {
        Instant now = clock.instant();
        tokens.consumeOutstanding(userId, purpose, now);

        String raw = OpaqueTokens.newToken();
        Instant expiresAt = now.plus(purpose.lifetime(emailVerifyTtl, passwordResetTtl));

        tokens.save(
                VerificationToken.issue(
                        idGenerator.newId(),
                        userId,
                        purpose,
                        OpaqueTokens.hash(raw),
                        now,
                        expiresAt));

        delivery.deliver(recipient, purpose, raw, expiresAt);
    }

    /**
     * Spends a token and returns whose account it acts on.
     *
     * @throws AuthenticationRequiredException {@code TOKEN_INVALID} when the token is unknown,
     *     expired, already used, or was issued for a different purpose. A verification link must
     *     not double as a password reset, so the purpose is part of what is checked, not an
     *     assumption about who is calling.
     */
    @Transactional
    public UserId consume(String rawToken, TokenPurpose purpose) {
        Instant now = clock.instant();
        VerificationToken token =
                tokens.findByTokenHash(OpaqueTokens.hash(rawToken))
                        .filter(candidate -> candidate.purpose() == purpose)
                        .filter(candidate -> candidate.isUsableAt(now))
                        .orElseThrow(
                                () ->
                                        new AuthenticationRequiredException(
                                                ErrorCode.TOKEN_INVALID,
                                                "This link is not valid or has expired. Request a new one."));

        tokens.save(token.consume(now));
        return token.userId();
    }
}
