package app.giaky.identity.infrastructure;

import app.giaky.identity.domain.TokenPurpose;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The link a verification or reset message carries: the web client's page for that purpose, with
 * the token in the query string.
 *
 * <p>The routes belong to the frontend, not to the domain: {@code TokenPurpose} must not know that a
 * web client exists, so the mapping lives here, at the edge that composes the link. They mirror
 * {@code web/src/router/routes.tsx} and move with it. Shared by every delivery adapter, so the link
 * a developer clicks in the log is the link a user gets by mail.
 */
final class RedemptionLinks {

    private static final Map<TokenPurpose, String> PATHS =
            Map.of(
                    TokenPurpose.EMAIL_VERIFY, "/verify-email",
                    TokenPurpose.PASSWORD_RESET, "/reset-password");

    private final String webBaseUrl;

    RedemptionLinks(String webBaseUrl) {
        // Trailing slashes are the classic source of "//verify-email" in a pasted link.
        this.webBaseUrl = webBaseUrl.replaceAll("/+$", "");
    }

    /**
     * The token is base64url already, so encoding it changes nothing today — it is done anyway
     * because "the alphabet happens to be safe" is not something a future change to
     * {@code OpaqueTokens} would remind anyone about.
     */
    String link(TokenPurpose purpose, String rawToken) {
        String path = PATHS.getOrDefault(purpose, "/");
        return webBaseUrl + path + "?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }
}
