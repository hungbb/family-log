package app.giaky.lineage.api.dto;

import app.giaky.lineage.application.ParentageView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** One parent → child edge (doc 05 §4.8). */
@Schema(description = "A parent-child link.")
public record ParentageResponse(
        UUID id,
        UUID parentPersonId,
        UUID childPersonId,
        @Schema(example = "FATHER") String parentRole,
        @Schema(example = "BIOLOGICAL") String nature,
        UUID unionId) {

    public static ParentageResponse of(ParentageView view) {
        return new ParentageResponse(
                view.id(),
                view.parentPersonId(),
                view.childPersonId(),
                view.parentRole().name(),
                view.nature().name(),
                view.unionId());
    }
}
