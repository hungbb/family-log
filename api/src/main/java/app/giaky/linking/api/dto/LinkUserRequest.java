package app.giaky.linking.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * {@code POST /clans/{clanId}/persons/{personId}/link-user} (FR-N4).
 *
 * <p>An account id rather than an email address: the target must already be an active member of the
 * clan, so the caller is choosing from a list they can see rather than inviting a stranger.
 */
public record LinkUserRequest(
        @NotNull @Schema(description = "An active member of this clan.") UUID userId) {}
