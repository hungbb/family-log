package app.giaky.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.TokenPurpose;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The SMTP adapter over a real connection, against Mailpit: what the unit test's mocked sender
 * cannot say is that the session properties, the MIME assembly and the server agree.
 */
@Testcontainers
class SmtpDeliveryIT {

    @Container
    private static final GenericContainer<?> MAILPIT =
            new GenericContainer<>("axllent/mailpit:v1.21")
                    .withExposedPorts(1025, 8025)
                    .waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025));

    @Test
    @DisplayName("a verification email reaches the mail server with its link")
    void deliversOverSmtp() throws Exception {
        SmtpVerificationTokenDelivery delivery =
                new SmtpVerificationTokenDelivery(
                        MAILPIT.getHost(),
                        MAILPIT.getMappedPort(1025),
                        "", // no username: no auth, as with a local catcher
                        "",
                        false, // Mailpit offers no TLS by default
                        "Gia Ký <no-reply@giaky.test>",
                        "https://giaky.example.com");
        try {
            delivery.deliver(
                    EmailAddress.of("an@example.com"),
                    TokenPurpose.EMAIL_VERIFY,
                    "tok-it",
                    Instant.now().plusSeconds(3600));
        } finally {
            delivery.destroy(); // waits for the worker, so the message has been sent
        }

        String api = "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025);
        HttpClient http = HttpClient.newHttpClient();
        String list = get(http, api + "/api/v1/messages");
        assertThat(list).contains("an@example.com").contains("no-reply@giaky.test");

        String id = list.replaceAll("(?s).*?\"ID\":\"([^\"]+)\".*", "$1");
        String message = get(http, api + "/api/v1/message/" + id);
        assertThat(message).contains("https://giaky.example.com/verify-email?token=tok-it");
    }

    private static String get(HttpClient http, String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
                .body();
    }
}
