package app.giaky.identity.infrastructure;

import app.giaky.identity.application.DeliveredTokenInspector;
import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.TokenPurpose;
import app.giaky.identity.domain.VerificationTokenDelivery;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Delivery under {@code dev} and {@code test}: logs the link a real message would carry, and keeps
 * the raw token per address so the dev endpoint and the integration tests can read it back.
 *
 * <p>This is what makes a register → verify → login walkthrough possible without a mail server, and
 * what lets the integration tests exercise the real flow instead of reaching into the database for
 * a hash they cannot reverse.
 *
 * <p><b>It exists in exactly two profiles and must never exist in a third.</b> The profile
 * expression on {@link LoggedVerificationTokenDelivery} is its exact complement, so precisely one
 * of the two is a bean in any profile — and {@code DevEndpointsAbsentInProdTest} pins that this one
 * is not the one under {@code prod}.
 */
@Component
@Profile("dev | test")
public class InMemoryVerificationTokenDelivery
        implements VerificationTokenDelivery, DeliveredTokenInspector {

    private static final Logger log =
            LoggerFactory.getLogger(InMemoryVerificationTokenDelivery.class);

    /** A hard cap, so a long-running dev session cannot grow this without bound. */
    private static final int MAX_TRACKED = 1_000;

    /**
     * Where each purpose is redeemed in the web client.
     *
     * <p>The routes belong to the frontend, not to the domain: {@code TokenPurpose} must not know
     * that a web client exists, so the mapping lives here, at the edge that composes the link.
     * They mirror {@code web/src/router/routes.tsx} and move with it.
     */
    private static final Map<TokenPurpose, String> REDEMPTION_PATHS =
            Map.of(
                    TokenPurpose.EMAIL_VERIFY, "/verify-email",
                    TokenPurpose.PASSWORD_RESET, "/reset-password");

    private final Map<String, String> lastTokens = new ConcurrentHashMap<>();
    private final String webBaseUrl;

    public InMemoryVerificationTokenDelivery(@Value("${app.web.base-url}") String webBaseUrl) {
        // Trailing slashes are the classic source of "//verify-email" in a pasted link.
        this.webBaseUrl = webBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public void deliver(
            EmailAddress recipient, TokenPurpose purpose, String rawToken, Instant expiresAt) {

        if (lastTokens.size() >= MAX_TRACKED) {
            lastTokens.clear();
        }
        lastTokens.put(key(recipient.value(), purpose), rawToken);

        // The whole point of this adapter: the link a real message would contain, ready to click,
        // so a local walkthrough never involves assembling a URL by hand. Deliberately at INFO and
        // deliberately unavailable outside dev and test.
        log.info(
                "[dev delivery] {} link for {}: {} (expires {})",
                purpose,
                recipient.value(),
                redemptionLink(purpose, rawToken),
                expiresAt);
    }

    /**
     * The link a real message would carry.
     *
     * <p>The token is base64url already, so encoding it changes nothing today — it is done anyway
     * because "the alphabet happens to be safe" is not something a future change to
     * {@code OpaqueTokens} would remind anyone about.
     */
    private String redemptionLink(TokenPurpose purpose, String rawToken) {
        String path = REDEMPTION_PATHS.getOrDefault(purpose, "/");
        return webBaseUrl + path + "?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    @Override
    public Optional<String> latestToken(String email, TokenPurpose purpose) {
        if (email == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(lastTokens.get(key(email.trim().toLowerCase(Locale.ROOT), purpose)));
    }

    /** Forgets everything. Keeps one test's tokens out of the next one's assertions. */
    public void clear() {
        lastTokens.clear();
    }

    private static String key(String email, TokenPurpose purpose) {
        return purpose.name() + '|' + email;
    }
}
