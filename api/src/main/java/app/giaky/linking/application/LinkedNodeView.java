package app.giaky.linking.application;

import java.util.UUID;

/**
 * The account attached to a node, and the node attached to an account — the same fact, read from
 * whichever side the caller is on.
 *
 * <p>What the tree drawer renders as "this is you" (F6.7) and what {@code /me} reports per clan.
 *
 * @param linkedUserId null when nothing is attached, which is the state almost every node is in
 */
public record LinkedNodeView(
        UUID personId,
        String personName,
        UUID clanId,
        UUID linkedUserId,
        String linkedUserName,
        boolean self) {}
