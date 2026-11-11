package app.giaky.clanorg.api.dto;

import app.giaky.clanorg.domain.Branch;
import app.giaky.common.web.NullOrNotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** {@code PATCH /clans/{clanId}/branches/{branchId}} (FR-C5). Structure moves through its own endpoint. */
public record UpdateBranchRequest(
        @NullOrNotBlank @Size(max = Branch.NAME_MAX_LENGTH) String name,
        @Size(max = Branch.DESCRIPTION_MAX_LENGTH) String description,
        @PositiveOrZero long version) {}
