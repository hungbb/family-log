package app.giaky.identity.api;

import app.giaky.common.security.AuthenticatedUser;
import app.giaky.common.security.CurrentUser;
import app.giaky.common.types.UserId;
import app.giaky.common.web.PageResponse;
import app.giaky.identity.api.dto.AdminUserResponse;
import app.giaky.identity.application.UserAdministrationService;
import app.giaky.identity.domain.UserStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform user administration, for super admins only.
 *
 * <p>The check is {@code SuperAdminGuard} inside the service, not anything here. A caller who is
 * not a super admin gets {@code 403 PERMISSION_DENIED} from every endpoint.
 */
@RestController
@RequestMapping("/admin/users")
@Tag(name = "Administration: users", description = "Every account on the platform (super admin)")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class AdminUserController {

    private final UserAdministrationService service;

    public AdminUserController(UserAdministrationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Every account, newest first",
            description = "Filter by name or address with `search`, and by state with `status`.")
    public PageResponse<AdminUserResponse> list(
            @CurrentUser AuthenticatedUser caller,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20")
                    @Positive
                    @Max(UserAdministrationService.MAX_PAGE_SIZE)
                    int size) {

        var result = service.list(caller.id(), search, status, page, size);
        return new PageResponse<>(
                result.content().stream().map(AdminUserResponse::of).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    @PostMapping("/{userId}/suspend")
    @Operation(
            summary = "Suspend an account",
            description =
                    "The account can no longer sign in, and every session it holds ends at once."
                            + " A super admin cannot be suspended.")
    public AdminUserResponse suspend(
            @CurrentUser AuthenticatedUser caller, @PathVariable UUID userId) {
        return AdminUserResponse.of(service.suspend(caller.id(), UserId.of(userId)));
    }

    @PostMapping("/{userId}/reactivate")
    @Operation(summary = "Lift a suspension")
    public AdminUserResponse reactivate(
            @CurrentUser AuthenticatedUser caller, @PathVariable UUID userId) {
        return AdminUserResponse.of(service.reactivate(caller.id(), UserId.of(userId)));
    }

    @PostMapping("/{userId}/verify-email")
    @Operation(
            summary = "Mark the address verified",
            description = "For a person whose verification mail never arrives. Idempotent.")
    public AdminUserResponse verifyEmail(
            @CurrentUser AuthenticatedUser caller, @PathVariable UUID userId) {
        return AdminUserResponse.of(service.markEmailVerified(caller.id(), UserId.of(userId)));
    }

    @PostMapping("/{userId}/resend-verification")
    @Operation(summary = "Send a fresh verification link")
    public ResponseEntity<Void> resendVerification(
            @CurrentUser AuthenticatedUser caller, @PathVariable UUID userId) {
        service.resendVerification(caller.id(), UserId.of(userId));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{userId}/send-password-reset")
    @Operation(
            summary = "Send a password-reset link",
            description = "The holder chooses the new password; nobody else ever sees it.")
    public ResponseEntity<Void> sendPasswordReset(
            @CurrentUser AuthenticatedUser caller, @PathVariable UUID userId) {
        service.sendPasswordReset(caller.id(), UserId.of(userId));
        return ResponseEntity.accepted().build();
    }
}
