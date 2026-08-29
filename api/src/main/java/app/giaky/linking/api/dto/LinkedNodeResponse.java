package app.giaky.linking.api.dto;

import app.giaky.linking.application.LinkedNodeView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Who is linked to a node (FR-N3 to FR-N5).
 *
 * @param self whether the caller <i>is</i> this node — what the tree renders as "this is you" (F6.7)
 */
public record LinkedNodeResponse(
        UUID personId,
        String personName,
        UUID clanId,
        @Schema(description = "null when nobody is linked, which is most nodes") UUID linkedUserId,
        String linkedUserName,
        boolean self) {

    public static LinkedNodeResponse of(LinkedNodeView view) {
        return new LinkedNodeResponse(
                view.personId(),
                view.personName(),
                view.clanId(),
                view.linkedUserId(),
                view.linkedUserName(),
                view.self());
    }
}
