package app.giaky.lineage.api.dto;

import app.giaky.lineage.application.PersonSummaryView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * A person as a tree node or a search hit (doc 05 §3.3).
 *
 * @param canEdit whether the caller may edit this node. A UI affordance computed by the batch
 *     resolver (doc 04 §4.1); the server asks the real question again on every write (doc 07 §8)
 * @param avatarMediaId the media row rather than a URL — Phase 7 resolves these
 */
@Schema(description = "A person, at the detail a tree node or a search result needs.")
public record PersonSummaryResponse(
        UUID id,
        UUID clanId,
        UUID branchId,
        String fullName,
        String alias,
        String gender,
        String lifeStatus,
        PartialDatePayload birth,
        PartialDatePayload death,
        @Schema(description = "đời; null when unknown — never 0") Integer generation,
        Integer birthOrder,
        UUID avatarMediaId,
        UUID linkedUserId,
        boolean foundingAncestor,
        boolean canEdit) {

    public static PersonSummaryResponse of(PersonSummaryView view) {
        return new PersonSummaryResponse(
                view.id(),
                view.clanId(),
                view.branchId(),
                view.fullName(),
                view.alias(),
                view.gender().name(),
                view.lifeStatus().name(),
                PartialDatePayload.of(view.birth()),
                PartialDatePayload.of(view.death()),
                view.generation(),
                view.birthOrder(),
                view.avatarMediaId(),
                view.linkedUserId(),
                view.foundingAncestor(),
                view.canEdit());
    }
}
