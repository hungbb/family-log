package app.giaky.identity.infrastructure;

import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.TokenPurpose;
import app.giaky.identity.domain.VerificationTokenDelivery;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Delivery outside {@code dev} and {@code test}: records that a token was issued, and drops it.
 *
 * <p>Email delivery is out of scope until Phase 8. Until then a deployment can send nothing, and
 * the honest behaviour is to say so in the log rather than to write the token there — a verification
 * token in a log file is a credential in a log file, readable by anyone with log access and
 * retained for as long as the logs are.
 *
 * <p><b>Consequence, deliberately visible:</b> in any profile that is not {@code dev} or
 * {@code test}, nobody can complete verification or a password reset. That is the correct state for
 * a phase with no mail transport, and the {@code WARN} makes it impossible to deploy without
 * noticing.
 */
@Component
@Profile("!(dev | test)")
public class LoggedVerificationTokenDelivery implements VerificationTokenDelivery {

    private static final Logger log =
            LoggerFactory.getLogger(LoggedVerificationTokenDelivery.class);

    @Override
    public void deliver(
            EmailAddress recipient, TokenPurpose purpose, String rawToken, Instant expiresAt) {
        log.warn(
                "No mail transport configured: a {} token for an account was issued and discarded."
                        + " It expires at {}. SMTP arrives in Phase 8.",
                purpose,
                expiresAt);
    }
}
