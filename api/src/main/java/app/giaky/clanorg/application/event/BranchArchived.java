package app.giaky.clanorg.application.event;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;

/** A branch was retired. It keeps its members and its history; it accepts nothing new. */
public record BranchArchived(
        ClanId clanId, BranchId branchId, UserId archivedBy, Instant occurredAt) {}
