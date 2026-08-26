package app.giaky.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One of the mandatory test classes of doc 09 §4.5: no response DTO serializes a credential.
 *
 * <p>I-U3 says the password hash never leaves the aggregate. A test that checked one endpoint would
 * hold for as long as nobody adds a second; this walks <i>every</i> response type in every context's
 * {@code api} package, follows nested records, and fails on a component whose name looks like a
 * secret. It therefore covers DTOs that do not exist yet, which is the only version of this test
 * worth having.
 *
 * <p>Request types are deliberately exempt: {@code LoginRequest} carries a password because that is
 * what a login is. The rule is about what the server <i>sends</i>.
 */
class SensitiveFieldExposureTest {

    /**
     * Component names no response may carry.
     *
     * <p>Matched as substrings against a lowercased name, so {@code passwordHash},
     * {@code refreshToken} and {@code tokenHash} are all caught by the entries below.
     */
    private static final List<String> FORBIDDEN_FRAGMENTS =
            List.of("password", "secret", "credential", "tokenhash", "hash");

    /**
     * The one response that legitimately carries tokens, and the two shapes behind it.
     *
     * <p>{@code TokenResponse} <i>is</i> the token pair — refusing it would be refusing to
     * authenticate anyone. {@code DevTokenResponse} exists only under the {@code dev} profile and is
     * asserted absent from a deployment by {@code DevEndpointsAbsentInProdTest}. Both are named
     * here rather than pattern-matched, so adding a third requires saying so out loud.
     */
    private static final Set<String> ALLOWED_TOKEN_CARRIERS =
            Set.of(
                    "app.giaky.identity.api.dto.TokenResponse",
                    "app.giaky.identity.api.dto.DevTokenResponse");

    private static final List<String> RESPONSE_SUFFIXES =
            List.of("Response", "Summary", "Detail", "Profile", "Info");

    @Test
    @DisplayName("no response DTO carries a password, a hash or a secret")
    void responsesCarryNoCredentials() {
        List<String> offenders = new ArrayList<>();

        for (Class<?> dto : responseTypes()) {
            collectOffenders(dto, dto.getSimpleName(), new HashSet<>(), offenders);
        }

        assertThat(offenders)
                .as("a response type exposing a credential-shaped field (I-U3)")
                .isEmpty();
    }

    @Test
    @DisplayName("the sweep actually found the response types, rather than passing on an empty set")
    void theSweepIsNotVacuous() {
        // Without this, a package rename would turn the test above into a permanent pass.
        assertThat(responseTypes())
                .extracting(Class::getSimpleName)
                .contains("MeResponse", "UserSummary", "TokenResponse", "SessionSummary");
    }

    @Test
    @DisplayName("a response carrying a password field would be caught")
    void theRuleHasTeeth() {
        List<String> offenders = new ArrayList<>();

        collectOffenders(Bad.class, "Bad", new HashSet<>(), offenders);

        assertThat(offenders).singleElement().asString().contains("passwordHash");
    }

    private static void collectOffenders(
            Class<?> type, String path, Set<Class<?>> seen, List<String> offenders) {

        if (!type.isRecord() || !seen.add(type)) {
            return;
        }
        boolean tokensAllowed = ALLOWED_TOKEN_CARRIERS.contains(type.getName());

        for (RecordComponent component : type.getRecordComponents()) {
            String name = component.getName().toLowerCase(Locale.ROOT);
            boolean forbidden = FORBIDDEN_FRAGMENTS.stream().anyMatch(name::contains);
            if (!forbidden && name.contains("token") && !tokensAllowed) {
                forbidden = true;
            }
            if (forbidden) {
                offenders.add(path + '.' + component.getName());
            }
            collectOffenders(
                    component.getType(), path + '.' + component.getName(), seen, offenders);
        }
    }

    /** Every record in an {@code api} package whose name reads like a response. */
    private static List<Class<?>> responseTypes() {
        JavaClasses apiClasses =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("app.giaky");

        return apiClasses.stream()
                .filter(clazz -> clazz.getPackageName().contains(".api"))
                .filter(clazz -> RESPONSE_SUFFIXES.stream().anyMatch(clazz.getSimpleName()::endsWith))
                .map(JavaClass::reflect)
                .filter(Class::isRecord)
                .toList();
    }

    /** A deliberately bad response, so the rule above is proven to fail when it should. */
    private record Bad(String id, String passwordHash) {}
}
