package app.giaky.lineage.api.dto;

import app.giaky.lineage.application.AncestorView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One step of an ancestor chain (doc 05 §4.9).
 *
 * @param depth generations up from the person asked about; 0 is that person
 */
@Schema(description = "An ancestor and how far up the tree they sit.")
public record AncestorResponse(PersonSummaryResponse person, int depth) {

    public static AncestorResponse of(AncestorView view) {
        return new AncestorResponse(PersonSummaryResponse.of(view.person()), view.depth());
    }
}
