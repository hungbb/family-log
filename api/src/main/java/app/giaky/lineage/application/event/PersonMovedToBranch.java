package app.giaky.lineage.application.event;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;

/**
 * A node changed branch (FR-P8).
 *
 * <p>Consequential for Authorization: placement is what a {@code BRANCH}-scoped grant matches on, so
 * this changes who may edit the node without any grant changing.
 */
public record PersonMovedToBranch(
        ClanId clanId,
        PersonId personId,
        BranchId previousBranchId,
        BranchId newBranchId,
        UserId movedBy,
        Instant occurredAt) {}
