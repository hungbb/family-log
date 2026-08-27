package app.giaky.clanorg.application.event;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;

/**
 * Somebody joined a clan.
 *
 * <p>Phase 3 turns this into the {@code CLAN_VIEW} grant doc 04 §6 promises on join approval;
 * Notification turns it into a welcome. Both are listeners, neither is ClanOrg's business.
 */
public record MembershipGranted(
        ClanId clanId,
        UserId userId,
        BranchId homeBranchId,
        UserId approvedBy,
        Instant occurredAt) {}
