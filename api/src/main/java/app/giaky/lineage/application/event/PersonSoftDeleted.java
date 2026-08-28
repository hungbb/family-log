package app.giaky.lineage.application.event;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;

/**
 * A node was hidden from the tree (FR-P10).
 *
 * <p>Its edges and its closure rows are untouched, so this is reversible by {@link PersonRestored}
 * rather than by a repair.
 */
public record PersonSoftDeleted(
        ClanId clanId, PersonId personId, UserId deletedBy, Instant occurredAt) {}
