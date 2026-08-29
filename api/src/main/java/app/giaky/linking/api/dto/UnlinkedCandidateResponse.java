package app.giaky.linking.api.dto;

import app.giaky.lineage.api.dto.PersonSummaryResponse;
import app.giaky.linking.application.UnlinkedCandidateView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * One candidate in the unlinked-node browser (B6.6, F6.2).
 *
 * <p>Reuses Lineage {@link PersonSummaryResponse} rather than declaring a fourth shape for a
 * person. A candidate <i>is</i> a person summary; the only thing this adds is the family around
 * them, which is what lets somebody tell two same-named relatives apart.
 */
public record UnlinkedCandidateResponse(
        PersonSummaryResponse person,
        @Schema(description = "The node recorded parents, for recognition.")
                List<String> parentNames,
        List<String> childNames) {

    public static UnlinkedCandidateResponse of(UnlinkedCandidateView view) {
        return new UnlinkedCandidateResponse(
                PersonSummaryResponse.of(view.person()), view.parentNames(), view.childNames());
    }
}
