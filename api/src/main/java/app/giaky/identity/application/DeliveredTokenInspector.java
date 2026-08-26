package app.giaky.identity.application;

import app.giaky.identity.domain.TokenPurpose;
import java.util.Optional;

/**
 * Reads back the last token "delivered" to an address.
 *
 * <p><b>A development affordance, not a feature.</b> Until SMTP exists (Phase 8) there is no inbox
 * to open, and a register → verify → login walkthrough would otherwise mean copying a token out of
 * the log by hand. The {@code dev}-profile endpoint in {@code identity.api} exposes this; the
 * integration tests use it to complete the same flow.
 *
 * <p>It is a separate interface from {@code VerificationTokenDelivery} on purpose: delivery is the
 * production port and is implemented in every profile, while an implementation of <i>this</i> only
 * exists under {@code dev} and {@code test}. A single interface would put a token-disclosure method
 * on the production adapter, which is precisely the thing that must not exist in a deployment.
 */
public interface DeliveredTokenInspector {

    /**
     * The most recent token issued to {@code email} for {@code purpose}, if one was.
     *
     * @param email the address, in any case; implementations normalize
     */
    Optional<String> latestToken(String email, TokenPurpose purpose);
}
