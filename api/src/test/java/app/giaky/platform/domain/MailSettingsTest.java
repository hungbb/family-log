package app.giaky.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MailSettingsTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    @DisplayName("disabled settings may be incomplete: half a configuration can be saved")
    void disabledMayBeIncomplete() {
        MailSettings settings = settings(false, null, null, null);

        assertThat(settings.violations()).isEmpty();
    }

    @Test
    @DisplayName("enabling needs a host, a port and a sender")
    void enablingNeedsTheEssentials() {
        MailSettings settings = settings(true, " ", null, "");

        assertThat(settings.violations())
                .extracting(MailSettings.Violation::field)
                .containsExactlyInAnyOrder("host", "port", "fromAddress");
    }

    @Test
    @DisplayName("shape is checked even while disabled")
    void shapeAlwaysChecked() {
        MailSettings settings = settings(false, "smtp.example.com", 70_000, "not-an-address");

        assertThat(settings.violations())
                .extracting(MailSettings.Violation::field)
                .containsExactlyInAnyOrder("port", "fromAddress");
    }

    @Test
    @DisplayName("blank text is stored as absent, and text is trimmed")
    void normalizesBlanks() {
        MailSettings settings = settings(true, "  smtp.example.com ", 587, " noreply@example.com ");

        assertThat(settings.host()).isEqualTo("smtp.example.com");
        assertThat(settings.fromAddress()).isEqualTo("noreply@example.com");
        assertThat(settings.violations()).isEmpty();
    }

    private static MailSettings settings(boolean enabled, String host, Integer port, String from) {
        return new MailSettings(
                enabled, host, port, null, null, MailSecurity.STARTTLS, from, null, 0, NOW, null);
    }
}
