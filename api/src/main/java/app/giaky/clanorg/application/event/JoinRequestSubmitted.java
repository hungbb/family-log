package app.giaky.clanorg.application.event;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.UUID;

/** Somebody asked to join. Notification tells whoever can answer it. */
public record JoinRequestSubmitted(
        ClanId clanId,
        UUID requestId,
        BranchId targetBranchId,
        UserId requesterUserId,
        Instant occurredAt) {}
