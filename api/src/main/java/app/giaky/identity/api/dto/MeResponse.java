package app.giaky.identity.api.dto;

import app.giaky.identity.application.UserProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * The full profile behind {@code GET /me} (FR-A4).
 *
 * <p>Doc 05 §4.2 also lists {@code clans[]}. It is absent until Phase 2: there are no clans and no
 * memberships yet, and an always-empty array of an element type nobody has designed would be a
 * contract we would then have to break. Adding it later is an additive change, which doc 05 §6
 * allows inside {@code v1}.
 */
@Schema(description = "The signed-in account's profile.")
public record MeResponse(
        UUID id,
        String email,
        String displayName,
        String locale,
        String status,
        boolean emailVerified,
        Instant emailVerifiedAt,
        UUID avatarMediaId,
        Instant lastLoginAt,
        Instant createdAt) {

    public static MeResponse of(UserProfile profile) {
        return new MeResponse(
                profile.id().value(),
                profile.email(),
                profile.displayName(),
                profile.locale().tag(),
                profile.status().name(),
                profile.emailVerified(),
                profile.emailVerifiedAt(),
                profile.avatarMediaId(),
                profile.lastLoginAt(),
                profile.createdAt());
    }
}
