package app.giaky.clanorg.application.event;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;

/**
 * A clan came into existence, with its root branch and its founder's membership.
 *
 * <p>Phase 3 consumes this to bootstrap the founder's {@code CLAN_ADMIN} grant (doc 04 §6). Until
 * then nothing subscribes, and publishing it anyway is what makes that phase a listener rather than
 * a change to {@code ClanService}.
 */
public record ClanCreated(
        ClanId clanId, BranchId rootBranchId, UserId createdBy, Instant occurredAt) {}
