package app.giaky.lineage.application.event;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;

/**
 * A node was added to the tree.
 *
 * @param branchId where it was placed; null for an unplaced node (doc 05 §4.7)
 */
public record PersonCreated(
        ClanId clanId,
        PersonId personId,
        BranchId branchId,
        String fullName,
        UserId createdBy,
        Instant occurredAt) {}
