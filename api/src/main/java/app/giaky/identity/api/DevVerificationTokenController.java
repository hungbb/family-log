package app.giaky.identity.api;

import app.giaky.identity.api.dto.DevTokenResponse;
import app.giaky.identity.application.DeliveredTokenInspector;
import app.giaky.identity.domain.TokenPurpose;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reads back the last verification or reset token issued to an address.
 *
 * <p><b>{@code dev} only, and that is enforced three ways.</b> The class is {@code @Profile("dev")},
 * so it is not a bean anywhere else; the {@link DeliveredTokenInspector} it depends on only exists
 * under {@code dev} and {@code test}, so the context would fail to start if the profile guard were
 * ever removed by accident; and {@code DevEndpointsAbsentInProdTest} asserts both.
 *
 * <p>It exists because there is no mail transport until Phase 8, and a walkthrough of register →
 * verify → login should not require anyone to read a log file. The route is left public by the
 * {@code dev}-only security chain, because a password reset has to be completed while signed out.
 */
@RestController
@RequestMapping("/dev/verification-tokens")
@Profile("dev")
@Validated
@Tag(name = "Development", description = "dev-profile helpers. Not present in any deployment.")
public class DevVerificationTokenController {

    private final DeliveredTokenInspector inspector;

    public DevVerificationTokenController(DeliveredTokenInspector inspector) {
        this.inspector = inspector;
    }

    @GetMapping
    @Operation(
            summary = "The last token issued to an address",
            description = "Stands in for an inbox until SMTP arrives in Phase 8.")
    public DevTokenResponse latest(
            @RequestParam @NotBlank String email,
            @RequestParam(defaultValue = "EMAIL_VERIFY") TokenPurpose purpose) {

        return new DevTokenResponse(inspector.latestToken(email, purpose).orElse(null));
    }
}
