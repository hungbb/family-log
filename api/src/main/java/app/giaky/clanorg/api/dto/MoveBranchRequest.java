package app.giaky.clanorg.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * {@code POST /clans/{clanId}/branches/{branchId}/move} (FR-C6).
 *
 * <p>Its own endpoint rather than a field on the PATCH, because it is not the same kind of change:
 * a rename touches one row, a move rewrites a subtree.
 */
public record MoveBranchRequest(
        @Schema(description = "The branch it should hang under from now on") @NotNull
                UUID newParentBranchId) {}
