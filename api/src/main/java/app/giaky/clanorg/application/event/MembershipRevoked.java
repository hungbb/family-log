package app.giaky.clanorg.application.event;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;

/**
 * Somebody stopped being a member — by leaving, or by being removed.
 *
 * <p>I-M2 hangs on this: membership is the prerequisite for every grant in a clan, so Phase 3
 * deactivates the departing member's grants when it fires.
 *
 * @param selfInitiated true when the member left, false when somebody removed them
 */
public record MembershipRevoked(
        ClanId clanId,
        UserId userId,
        UserId actorUserId,
        boolean selfInitiated,
        Instant occurredAt) {}
