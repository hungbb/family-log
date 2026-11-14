package app.giaky.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import app.giaky.config.DevSecurityConfig;
import app.giaky.identity.application.DeliveredTokenInspector;
import app.giaky.identity.infrastructure.InMemoryVerificationTokenDelivery;
import app.giaky.identity.infrastructure.LoggedVerificationTokenDelivery;
import app.giaky.identity.infrastructure.SmtpVerificationTokenDelivery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.StandardEnvironment;

/**
 * The phase doc's §9 risk, made into a test: the endpoint that reveals verification tokens must not
 * exist in a deployment.
 *
 * <p>Asserted by evaluating the real profile expressions against real environments rather than by
 * booting a {@code prod} context, which would need a database and a JWT secret to prove something
 * that is decided entirely by annotations. The expressions are the mechanism; this checks the
 * mechanism says what it is meant to under every profile the product ships with.
 */
class DevEndpointsAbsentInProdTest {

    @Test
    @DisplayName("the dev token endpoint is @Profile(\"dev\")")
    void devControllerIsProfileScoped() {
        Profile profile = DevVerificationTokenController.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("dev");
        assertThat(matches(profile, "prod")).isFalse();
        assertThat(matches(profile, "test")).isFalse();
        assertThat(matches(profile, "dev")).isTrue();
    }

    @Test
    @DisplayName("the security exemption for /api/v1/dev/** is @Profile(\"dev\") too")
    void devSecurityChainIsProfileScoped() {
        Profile profile = DevSecurityConfig.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(matches(profile, "prod")).isFalse();
        assertThat(matches(profile, "dev")).isTrue();
    }

    @Test
    @DisplayName("the adapter that keeps raw tokens in memory exists only under dev and test")
    void tokenRecordingAdapterIsProfileScoped() {
        Profile profile = InMemoryVerificationTokenDelivery.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(matches(profile, "prod")).isFalse();
        assertThat(matches(profile, "dev")).isTrue();
        assertThat(matches(profile, "test")).isTrue();
    }

    @Test
    @DisplayName("exactly one delivery adapter is active in every profile")
    void deliveryAdaptersAreMutuallyExclusive() {
        Profile recording = InMemoryVerificationTokenDelivery.class.getAnnotation(Profile.class);
        Profile logging = LoggedVerificationTokenDelivery.class.getAnnotation(Profile.class);

        // Two adapters that could both be active would fail the context with a duplicate-bean
        // error; two that could both be inactive would fail it with a missing one. Neither is a
        // failure anyone wants to discover at deployment time.
        for (String profile : new String[] {"dev", "test", "prod", "staging"}) {
            assertThat(matches(recording, profile))
                    .as("recording delivery under %s", profile)
                    .isNotEqualTo(matches(logging, profile));
        }
    }

    @Test
    @DisplayName("the only implementation of the inspector port is the dev/test adapter")
    void inspectorHasNoProductionImplementation() {
        assertThat(DeliveredTokenInspector.class.isAssignableFrom(InMemoryVerificationTokenDelivery.class))
                .isTrue();
        assertThat(DeliveredTokenInspector.class.isAssignableFrom(LoggedVerificationTokenDelivery.class))
                .as("a production adapter would put a token-disclosure method in a deployment")
                .isFalse();
    }

    @Test
    @DisplayName("the SMTP adapter lives where the logging one does, and discloses nothing")
    void smtpAdapterIsAProductionAdapter() {
        Profile smtp = SmtpVerificationTokenDelivery.class.getAnnotation(Profile.class);
        Profile logging = LoggedVerificationTokenDelivery.class.getAnnotation(Profile.class);

        // Same profile expression: the choice between the two is app.mail.host, and that choice is
        // pinned by SmtpVerificationTokenDeliveryTest. Never alongside the recording adapter.
        assertThat(smtp.value()).containsExactly(logging.value());
        assertThat(DeliveredTokenInspector.class.isAssignableFrom(SmtpVerificationTokenDelivery.class))
                .isFalse();
    }

    private static boolean matches(Profile profile, String activeProfile) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(activeProfile);
        return environment.matchesProfiles(profile.value());
    }
}
