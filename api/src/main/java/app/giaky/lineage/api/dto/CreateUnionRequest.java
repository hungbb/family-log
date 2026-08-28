package app.giaky.lineage.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Record a marriage (doc 05 §4.8).
 *
 * @param force record it even though the two are already related as ancestor and descendant (I-N4).
 *     A guard against picking the wrong row in a list of people who share a surname, not a moral
 *     position — and overridable by an administrator, because some books do record it
 */
public record CreateUnionRequest(
        @NotNull UUID partnerAId,
        @NotNull UUID partnerBId,
        @Schema(example = "PRIMARY", description = "PRIMARY | SECONDARY | SEQUENTIAL")
                String unionType,
        @Schema(example = "ACTIVE", description = "ACTIVE | ENDED | WIDOWED | UNKNOWN")
                String status,
        @Valid PartialDatePayload startDate,
        @Valid PartialDatePayload endDate,
        @Size(max = 2000) String notes,
        boolean force) {}
