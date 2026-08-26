package app.giaky.identity.api.dto;

import app.giaky.identity.application.UserProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * The signed-in account, as returned alongside a token pair.
 *
 * <p>Enough for the shell to render an account menu without a second request, and nothing more.
 * There is no field here that could hold a credential, which is the point (I-U3).
 */
@Schema(description = "The signed-in account.")
public record UserSummary(
        UUID id,
        String email,
        String displayName,
        String locale,
        String status,
        boolean emailVerified,
        UUID avatarMediaId) {

    public static UserSummary of(UserProfile profile) {
        return new UserSummary(
                profile.id().value(),
                profile.email(),
                profile.displayName(),
                profile.locale().tag(),
                profile.status().name(),
                profile.emailVerified(),
                profile.avatarMediaId());
    }
}
