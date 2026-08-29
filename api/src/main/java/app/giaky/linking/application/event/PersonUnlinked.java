package app.giaky.linking.application.event;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;

/**
 * An account was detached from a node (FR-N5).
 *
 * @param selfService whether the person removed their own link; the subscriber does not tell
 *     somebody about a thing they just did themselves
 */
public record PersonUnlinked(
        ClanId clanId,
        PersonId personId,
        String personName,
        UserId previousUserId,
        UserId actorUserId,
        boolean selfService,
        Instant occurredAt) {}
