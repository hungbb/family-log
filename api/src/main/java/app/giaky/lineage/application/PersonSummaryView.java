package app.giaky.lineage.application;

import app.giaky.common.types.PartialDate;
import app.giaky.lineage.domain.Gender;
import app.giaky.lineage.domain.LifeStatus;
import app.giaky.lineage.domain.Person;
import java.util.UUID;

/**
 * A person as a tree node or a search hit (doc 05 §3.3).
 *
 * <p>Everything a node needs to render and nothing more. The tree read returns thousands of these,
 * so each field is a field multiplied by the size of the clan — notes and audit columns are in
 * {@link PersonDetailView} for that reason alone.
 *
 * @param canEdit stamped per requesting user from the batch resolver, not from a per-node check
 *     (doc 04 §4.1). It is a UI affordance; the server asks the real question again on every
 *     mutation (doc 07 §8)
 * @param avatarMediaId the media row, not a URL. Phase 7 resolves these; until then it is null
 */
public record PersonSummaryView(
        UUID id,
        UUID clanId,
        UUID branchId,
        String fullName,
        String alias,
        Gender gender,
        LifeStatus lifeStatus,
        PartialDate birth,
        PartialDate death,
        Integer generation,
        Integer birthOrder,
        UUID avatarMediaId,
        UUID linkedUserId,
        boolean foundingAncestor,
        boolean canEdit) {

    static PersonSummaryView of(Person person, boolean canEdit) {
        return new PersonSummaryView(
                person.id().value(),
                person.clanId().value(),
                person.branchId() == null ? null : person.branchId().value(),
                person.fullName(),
                person.name().alias(),
                person.gender(),
                person.lifeStatus(),
                person.birth(),
                person.death(),
                person.generation(),
                person.birthOrder(),
                person.avatarMediaId(),
                person.linkedUserId() == null ? null : person.linkedUserId().value(),
                person.foundingAncestor(),
                canEdit);
    }
}
