package app.giaky.identity.infrastructure;

import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.TokenPurpose;
import app.giaky.identity.domain.VerificationTokenDelivery;
import app.giaky.platform.application.Mailer;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Delivery outside {@code dev} and {@code test}: emails the link through the transport a super
 * admin configured (R8.1).
 *
 * <p>When mail is switched off, this says so in the log and drops the token. It does not write the
 * token there: a verification token in a log file is a credential in a log file, readable by anyone
 * with log access and kept for as long as the logs are.
 *
 * <p><b>Consequence, deliberately visible:</b> until a super admin enables outgoing mail, nobody
 * can complete verification or a password reset in a deployment. A super admin can still mark an
 * address verified by hand from the user list. The {@code WARN} makes the state impossible to miss.
 */
@Component
@Profile("!(dev | test)")
public class MailVerificationTokenDelivery implements VerificationTokenDelivery {

    private static final Logger log = LoggerFactory.getLogger(MailVerificationTokenDelivery.class);

    private final VerificationMessages messages;
    private final Mailer mailer;

    public MailVerificationTokenDelivery(VerificationMessages messages, Mailer mailer) {
        this.messages = messages;
        this.mailer = mailer;
    }

    @Override
    public void deliver(
            EmailAddress recipient, TokenPurpose purpose, String rawToken, Instant expiresAt) {
        boolean queued = mailer.send(messages.compose(recipient, purpose, rawToken, expiresAt));
        if (!queued) {
            log.warn(
                    "Outgoing mail is disabled: a {} token for an account was issued and discarded."
                            + " It expires at {}. A super admin can enable mail under"
                            + " Administration → Mail.",
                    purpose,
                    expiresAt);
        }
    }
}
