package app.giaky.identity.api;

import app.giaky.common.security.AuthenticatedUser;
import app.giaky.common.security.CurrentUser;
import app.giaky.identity.api.dto.MeResponse;
import app.giaky.identity.api.dto.SessionSummary;
import app.giaky.identity.api.dto.UpdateProfileRequest;
import app.giaky.identity.application.AccountService;
import app.giaky.identity.application.SuperAdminGuard;
import app.giaky.identity.application.UpdateProfileCommand;
import app.giaky.identity.application.UserProfile;
import app.giaky.identity.domain.LocaleTag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The current-user endpoints of doc 05 §4.2.
 *
 * <p>Every method acts on {@code caller} and never on an id from the path. That is what makes this
 * controller safe without an authorization check: there is no way to name someone else's account,
 * so there is nothing for a permission to protect. The endpoints doc 05 §4.2 lists for
 * notifications, join requests and claims arrive with the phases that create those things.
 */
@RestController
@RequestMapping("/me")
@Tag(name = "Current user", description = "The signed-in account's own profile and sessions (FR-A4)")
@SecurityRequirement(name = "bearerAuth")
public class MeController {

    private final AccountService accountService;
    private final SuperAdminGuard superAdminGuard;

    public MeController(AccountService accountService, SuperAdminGuard superAdminGuard) {
        this.accountService = accountService;
        this.superAdminGuard = superAdminGuard;
    }

    @GetMapping
    @Operation(summary = "The signed-in account's profile")
    public MeResponse me(@CurrentUser AuthenticatedUser caller) {
        return MeResponse.of(
                accountService.profile(caller.id()), superAdminGuard.isSuperAdmin(caller.id()));
    }

    @PatchMapping
    @Operation(
            summary = "Update the profile",
            description = "Absent fields are left unchanged. Returns the stored profile.")
    public MeResponse updateProfile(
            @CurrentUser AuthenticatedUser caller, @Valid @RequestBody UpdateProfileRequest request) {

        UserProfile updated =
                accountService.updateProfile(
                        caller.id(),
                        new UpdateProfileCommand(
                                request.displayName(),
                                request.locale() == null
                                        ? null
                                        : LocaleTag.fromTagOrDefault(request.locale())));
        return MeResponse.of(updated, superAdminGuard.isSuperAdmin(caller.id()));
    }

    @GetMapping("/sessions")
    @Operation(
            summary = "Active sessions",
            description =
                    "Live refresh-token sessions, newest first. End all of them with"
                        + " POST /auth/logout-all.")
    public List<SessionSummary> sessions(@CurrentUser AuthenticatedUser caller) {
        return accountService.sessions(caller.id()).stream().map(SessionSummary::of).toList();
    }
}
