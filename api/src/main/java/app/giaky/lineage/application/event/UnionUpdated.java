package app.giaky.lineage.application.event;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.UUID;

/** A marriage record was corrected — its type, its status, or its dates. */
public record UnionUpdated(
        ClanId clanId, UUID unionId, UserId updatedBy, Instant occurredAt) {}
