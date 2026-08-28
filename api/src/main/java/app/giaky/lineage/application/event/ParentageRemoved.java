package app.giaky.lineage.application.event;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.UUID;

/** A parent-child edge was removed, and the subtree below the child was recomputed. */
public record ParentageRemoved(
        ClanId clanId,
        UUID parentageId,
        PersonId parentPersonId,
        PersonId childPersonId,
        int generationsRestamped,
        UserId removedBy,
        Instant occurredAt) {}
