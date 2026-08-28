package app.giaky.lineage.application.event;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * A marriage record was removed.
 *
 * <p>Nobody left the tree: children hang from parentage edges, which merely referenced this union.
 */
public record UnionRemoved(
        ClanId clanId, UUID unionId, UserId removedBy, Instant occurredAt) {}
