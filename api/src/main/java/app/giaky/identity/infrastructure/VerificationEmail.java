package app.giaky.identity.infrastructure;

import app.giaky.identity.domain.TokenPurpose;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.web.util.HtmlUtils;

/**
 * The message for a verification or reset link: subject, plain text and HTML, in Vietnamese with
 * English below it.
 *
 * <p>Both languages in one message because the delivery port carries no locale, and a reader who
 * cannot read the first half can still read the second. The expiry is shown in Vietnam time, where
 * the families are; the link itself carries no time zone.
 */
record VerificationEmail(String subject, String text, String html) {

    static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    static VerificationEmail of(TokenPurpose purpose, String link, Instant expiresAt) {
        String expiry = EXPIRY.format(expiresAt.atZone(VIETNAM));
        Copy copy = purpose == TokenPurpose.PASSWORD_RESET ? Copy.RESET : Copy.VERIFY;

        String text =
                """
                Xin chào,

                %s
                %s

                Liên kết hết hạn lúc %s (giờ Việt Nam). %s

                ---

                Hello,

                %s
                %s

                The link expires at %s (Vietnam time). %s
                """
                        .formatted(
                                copy.viIntro, link, expiry, copy.viIgnore,
                                copy.enIntro, link, expiry, copy.enIgnore);

        String href = HtmlUtils.htmlEscape(link);
        String html =
                """
                <!doctype html>
                <html><body style="margin:0;padding:24px;background:#f6f4ef;font-family:Arial,Helvetica,sans-serif;color:#2b2722">
                <div style="max-width:520px;margin:0 auto;background:#ffffff;border-radius:8px;padding:28px">
                <p style="font-size:18px;font-weight:bold;margin:0 0 16px">Gia Ký</p>
                <p>Xin chào,</p>
                <p>%s</p>
                <p style="margin:24px 0"><a href="%s" style="background:#8a3b12;color:#ffffff;text-decoration:none;padding:12px 20px;border-radius:6px;display:inline-block">%s</a></p>
                <p style="font-size:13px;color:#6b635a">Liên kết hết hạn lúc %s (giờ Việt Nam). %s</p>
                <hr style="border:none;border-top:1px solid #e6e1d8;margin:24px 0">
                <p>Hello,</p>
                <p>%s</p>
                <p style="font-size:13px;color:#6b635a">The link expires at %s (Vietnam time). %s</p>
                <p style="font-size:12px;color:#6b635a;word-break:break-all">%s</p>
                </div></body></html>
                """
                        .formatted(
                                copy.viIntro, href, copy.button, expiry, copy.viIgnore,
                                copy.enIntro, expiry, copy.enIgnore, href);

        return new VerificationEmail(copy.subject, text, html);
    }

    private enum Copy {
        VERIFY(
                "Xác nhận địa chỉ email — Gia Ký",
                "Xác nhận email",
                "Bấm vào liên kết dưới đây để xác nhận địa chỉ email cho tài khoản Gia Ký của bạn:",
                "Nếu bạn không tạo tài khoản này, hãy bỏ qua email này.",
                "Open the link below to confirm the email address on your Gia Ký account:",
                "If you did not create this account, you can ignore this email."),
        RESET(
                "Đặt lại mật khẩu — Gia Ký",
                "Đặt lại mật khẩu",
                "Bấm vào liên kết dưới đây để đặt mật khẩu mới cho tài khoản Gia Ký của bạn:",
                "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này; mật khẩu hiện tại vẫn giữ nguyên.",
                "Open the link below to choose a new password for your Gia Ký account:",
                "If you did not ask to reset your password, ignore this email; your current password still works.");

        final String subject;
        final String button;
        final String viIntro;
        final String viIgnore;
        final String enIntro;
        final String enIgnore;

        Copy(String subject, String button, String viIntro, String viIgnore, String enIntro, String enIgnore) {
            this.subject = subject;
            this.button = button;
            this.viIntro = viIntro;
            this.viIgnore = viIgnore;
            this.enIntro = enIntro;
            this.enIgnore = enIgnore;
        }
    }
}
