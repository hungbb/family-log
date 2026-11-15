package app.giaky.platform.api;

import app.giaky.common.security.AuthenticatedUser;
import app.giaky.common.security.CurrentUser;
import app.giaky.platform.api.dto.MailSettingsResponse;
import app.giaky.platform.api.dto.SendTestMailRequest;
import app.giaky.platform.api.dto.UpdateMailSettingsRequest;
import app.giaky.platform.application.MailSettingsService;
import app.giaky.platform.application.UpdateMailSettingsCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform settings, for super admins only. The outgoing mail transport is the first setting here.
 *
 * <p>The check is {@code SuperAdminGuard} inside the service, not anything here.
 */
@RestController
@RequestMapping("/admin/settings")
@Tag(name = "Administration: settings", description = "Platform-wide settings (super admin)")
@SecurityRequirement(name = "bearerAuth")
public class AdminSettingsController {

    private final MailSettingsService mailSettings;

    public AdminSettingsController(MailSettingsService mailSettings) {
        this.mailSettings = mailSettings;
    }

    @GetMapping("/mail")
    @Operation(summary = "The outgoing mail settings", description = "The password is never returned.")
    public MailSettingsResponse mail(@CurrentUser AuthenticatedUser caller) {
        return MailSettingsResponse.of(mailSettings.get(caller.id()));
    }

    @PutMapping("/mail")
    @Operation(
            summary = "Replace the outgoing mail settings",
            description =
                    "Omit `password` to keep the stored one. Host, port and sender address are"
                            + " required to enable mail.")
    public MailSettingsResponse updateMail(
            @CurrentUser AuthenticatedUser caller,
            @Valid @RequestBody UpdateMailSettingsRequest request) {

        return MailSettingsResponse.of(
                mailSettings.update(
                        caller.id(),
                        new UpdateMailSettingsCommand(
                                request.enabled(),
                                request.host(),
                                request.port(),
                                request.username(),
                                request.password(),
                                request.security(),
                                request.fromAddress(),
                                request.fromName())));
    }

    @PostMapping("/mail/test")
    @Operation(
            summary = "Send a test message",
            description =
                    "Sends synchronously through the stored settings, which need not be enabled"
                            + " yet. A failure answers 422 MAIL_DELIVERY_FAILED with what the"
                            + " server said.")
    public ResponseEntity<Void> sendTest(
            @CurrentUser AuthenticatedUser caller, @Valid @RequestBody SendTestMailRequest request) {
        mailSettings.sendTest(caller.id(), request.to());
        return ResponseEntity.noContent().build();
    }
}
