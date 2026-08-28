package app.giaky.lineage.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Correct a marriage record (doc 05 §4.8).
 *
 * <p>The partners are not editable. Changing who a union is between is not a correction, it is two
 * different marriages, and expressing it as one edit would slip past the two-endpoint permission
 * check that created it.
 */
public record UpdateUnionRequest(
        @Schema(example = "SECONDARY") String unionType,
        @Schema(example = "ENDED") String status,
        @Valid PartialDatePayload startDate,
        @Valid PartialDatePayload endDate,
        @Size(max = 2000) String notes) {}
