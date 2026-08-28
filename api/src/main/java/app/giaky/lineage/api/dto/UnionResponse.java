package app.giaky.lineage.api.dto;

import app.giaky.lineage.application.UnionView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * A marriage (doc 05 §4.8).
 *
 * <p>{@code partnerAId} and {@code partnerBId} are in canonical storage order and carry no meaning:
 * the client decides who to draw first, from gender and dates.
 */
@Schema(description = "A marriage or partnership between two people.")
public record UnionResponse(
        UUID id,
        UUID partnerAId,
        UUID partnerBId,
        @Schema(example = "PRIMARY") String unionType,
        @Schema(example = "ENDED") String status,
        PartialDatePayload startDate,
        PartialDatePayload endDate,
        String notes) {

    public static UnionResponse of(UnionView view) {
        return new UnionResponse(
                view.id(),
                view.partnerAId(),
                view.partnerBId(),
                view.unionType().name(),
                view.status().name(),
                PartialDatePayload.of(view.startDate()),
                PartialDatePayload.of(view.endDate()),
                view.notes());
    }
}
