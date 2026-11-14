package app.giaky.identity.infrastructure;

import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.TokenPurpose;
import app.giaky.platform.application.OutgoingMail;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The link and the message behind a verification or reset token.
 *
 * <p>Shared by both delivery adapters, so the link a developer clicks in the dev log is the link a
 * real inbox receives.
 *
 * <p><b>Bilingual on purpose.</b> The delivery port knows the recipient's address and not their
 * locale. The message carries Vietnamese first, as the product's default, and English below it, and
 * that is enough for v1. Putting the locale on the port is an additive change when it is wanted.
 */
@Component
public class VerificationMessages {

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

    private static final DateTimeFormatter EXPIRY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final String webBaseUrl;

    public VerificationMessages(@Value("${app.web.base-url}") String webBaseUrl) {
        // Trailing slashes are the classic source of "//verify-email" in a pasted link.
        this.webBaseUrl = webBaseUrl.replaceAll("/+$", "");
    }

    /**
     * The link a message carries.
     *
     * <p>The token is base64url already, so encoding it changes nothing today. It is done anyway
     * because "the alphabet happens to be safe" is not something a future change to {@code
     * OpaqueTokens} would remind anyone about.
     */
    public String link(TokenPurpose purpose, String rawToken) {
        String path = REDEMPTION_PATHS.getOrDefault(purpose, "/");
        return webBaseUrl + path + "?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    public OutgoingMail compose(
            EmailAddress recipient, TokenPurpose purpose, String rawToken, Instant expiresAt) {
        String link = link(purpose, rawToken);
        String expires = EXPIRY.format(expiresAt);
        return switch (purpose) {
            case EMAIL_VERIFY ->
                    new OutgoingMail(
                            recipient.value(),
                            "Gia Ký — Xác minh địa chỉ email / Confirm your email address",
                            """
                            Xin chào,

                            Mở liên kết dưới đây để xác minh địa chỉ email của bạn trên Gia Ký:
                            %1$s

                            Liên kết hết hạn lúc %2$s. Nếu bạn không đăng ký, hãy bỏ qua thư này.

                            ---

                            Hello,

                            Open the link below to confirm your email address on Gia Ký:
                            %1$s

                            The link expires at %2$s. If you did not sign up, ignore this message.
                            """
                                    .formatted(link, expires));
            case PASSWORD_RESET ->
                    new OutgoingMail(
                            recipient.value(),
                            "Gia Ký — Đặt lại mật khẩu / Reset your password",
                            """
                            Xin chào,

                            Mở liên kết dưới đây để đặt mật khẩu mới cho tài khoản Gia Ký của bạn:
                            %1$s

                            Liên kết hết hạn lúc %2$s và chỉ dùng được một lần. Nếu bạn không yêu \
                            cầu, hãy bỏ qua thư này. Mật khẩu hiện tại vẫn giữ nguyên.

                            ---

                            Hello,

                            Open the link below to choose a new password for your Gia Ký account:
                            %1$s

                            The link expires at %2$s and works once. If you did not ask for this, \
                            ignore this message; your current password still works.
                            """
                                    .formatted(link, expires));
        };
    }
}
