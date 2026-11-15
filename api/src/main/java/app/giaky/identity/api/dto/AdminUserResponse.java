package app.giaky.identity.api.dto;

import app.giaky.identity.application.AdminUserView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** One account in the super-admin user list. Carries nothing that could hold a credential. */
@Schema(description = "An account, as a super admin sees it.")
public record AdminUserResponse(
        UUID id,
        String email,
        String displayName,
        String locale,
        String status,
        boolean emailVerified,
        Instant lastLoginAt,
        Instant createdAt,
        @Schema(description = "A super admin cannot be suspended from the UI.") boolean superAdmin) {

    public static AdminUserResponse of(AdminUserView view) {
        return new AdminUserResponse(
                view.id().value(),
                view.email(),
                view.displayName(),
                view.locale().tag(),
                view.status().name(),
                view.emailVerified(),
                view.lastLoginAt(),
                view.createdAt(),
                view.superAdmin());
    }
}
