package app.giaky.authorization.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code POST /clans/{clanId}/permissions} — doc 05 §4.6.
 *
 * <p>Permission and scope arrive as strings rather than as bound enums on purpose. Spring's binder
 * answers an unknown enum value with a 400 about a type mismatch; the service answers it with a 422
 * naming the value and listing what exists, which is the difference between a client author who is
 * stuck and one who is not.
 *
 * @param expiresAt optional; a permission that lapses on its own is how a family lends somebody
 *     edit rights for the month before a giỗ without having to remember to take them back
 */
public record CreateGrantRequest(
        @NotNull UUID granteeUserId,
        @Schema(
                        description = "One of CLAN_VIEW, TREE_EDIT, MEMBER_APPROVE,"
                                + " NODE_CLAIM_APPROVE, ROLE_MANAGE, CLAN_ADMIN",
                        example = "TREE_EDIT")
                @NotBlank
                String permission,
        @Schema(
                        description =
                                "CLAN or BRANCH. SUBTREE is understood by the resolver and"
                                    + " returns 422 SUBTREE_SCOPE_UNAVAILABLE until the family"
                                    + " tree exists.",
                        example = "BRANCH")
                @NotBlank
                String scopeType,
        @Schema(description = "The branch, for a BRANCH scope. Omitted for CLAN.")
                UUID scopeRefId,
        @Future Instant expiresAt) {}
