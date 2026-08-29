package app.giaky.linking.application.event;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;

/**
 * An account was attached to a node (FR-N3, FR-N4).
 *
 * <p>Raised for both routes into the link — an approved claim and a direct assignment — because the
 * person it concerns wants to know either way, and "you were added to the tree" reads the same
 * whether they asked or somebody did it for them.
 *
 * @param viaClaim whether this came from the person's own claim; a direct assignment is the case
 *     where the notification is genuinely news
 */
public record PersonLinked(
        ClanId clanId,
        PersonId personId,
        String personName,
        UserId linkedUserId,
        UserId actorUserId,
        boolean viaClaim,
        Instant occurredAt) {}
