package app.giaky.clanorg.api.dto;

import app.giaky.clanorg.domain.Branch;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code POST /clans/{clanId}/branches} (FR-C4). */
public record CreateBranchRequest(
        @Schema(description = "Where it hangs. Omit for directly under the clan root branch.")
                UUID parentBranchId,
        @NotBlank @Size(max = Branch.NAME_MAX_LENGTH) String name,
        @Size(max = Branch.DESCRIPTION_MAX_LENGTH) String description) {}
