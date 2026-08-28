package app.giaky.lineage.application.event;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;

/** A node's attributes changed. Structural changes have their own events. */
public record PersonUpdated(
        ClanId clanId, PersonId personId, UserId updatedBy, Instant occurredAt) {}
