package app.giaky.clanorg.application.event;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;

/**
 * A branch was re-parented, and every path beneath it was rewritten.
 *
 * <p>The event Authorization most needs: a branch-scoped grant is resolved through paths, so a move
 * changes what an existing grant covers without the grant itself changing.
 *
 * @param descendantsRewritten how many branches moved with it — zero for a leaf
 */
public record BranchMoved(
        ClanId clanId,
        BranchId branchId,
        BranchId previousParentBranchId,
        BranchId newParentBranchId,
        int descendantsRewritten,
        UserId movedBy,
        Instant occurredAt) {}
