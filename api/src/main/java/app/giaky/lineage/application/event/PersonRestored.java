package app.giaky.lineage.application.event;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;

/** A soft-deleted node was brought back, within the recovery window. */
public record PersonRestored(
        ClanId clanId, PersonId personId, UserId restoredBy, Instant occurredAt) {}
