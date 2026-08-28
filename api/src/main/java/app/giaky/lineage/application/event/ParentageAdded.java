package app.giaky.lineage.application.event;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * A parent-child edge was created.
 *
 * <p>The closure and the generation cascade have already run, inside the transaction this event
 * follows (doc 02 §6). Subscribers are told what happened; they are not what makes it happen.
 *
 * @param generationsRestamped how many people had their <i>đời</i> recomputed as a result
 */
public record ParentageAdded(
        ClanId clanId,
        UUID parentageId,
        PersonId parentPersonId,
        PersonId childPersonId,
        int generationsRestamped,
        UserId addedBy,
        Instant occurredAt) {}
