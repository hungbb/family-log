package app.giaky.identity.infrastructure;

import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.TokenPurpose;
import app.giaky.identity.domain.VerificationTokenDelivery;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Delivery outside {@code dev} and {@code test} when no mail server is configured
 * ({@code app.mail.host} empty): records that a token was issued, and drops it. With a host set,
 * {@link SmtpVerificationTokenDelivery} takes its place.
 *
 * <p>A deployment without a mail server can send nothing, and the honest behaviour is to say so in
 * the log rather than to write the token there — a verification
 * token in a log file is a credential in a log file, readable by anyone with log access and
 * retained for as long as the logs are.
 *
 * <p><b>Consequence, deliberately visible:</b> without a mail server, nobody can complete
 * verification or a password reset. The {@code WARN} makes it impossible to deploy that way without
 * noticing.
 */
@Component
@Profile("!(dev | test)")
@ConditionalOnExpression("'${app.mail.host:}'.isBlank()")
public class LoggedVerificationTokenDelivery implements VerificationTokenDelivery {

    private static final Logger log =
            LoggerFactory.getLogger(LoggedVerificationTokenDelivery.class);

    @Override
    public void deliver(
            EmailAddress recipient, TokenPurpose purpose, String rawToken, Instant expiresAt) {
        log.warn(
                "No mail transport configured: a {} token for an account was issued and discarded."
                        + " It expires at {}. Set SMTP_HOST to send it by mail.",
                purpose,
                expiresAt);
    }
}
