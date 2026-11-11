package app.giaky.clanorg.application;

import java.time.Instant;
import java.util.UUID;

/**
 * One member of a clan (doc 05 §4.5).
 *
 * <p>The name and address come from Identity through {@code UserDirectory}; ClanOrg holds only the
 * id (doc 02 §1.1). {@code linkedPersonId} is the node the member is recorded as in this clan's tree,
 * asked of Linking through {@link MemberNodeLookup}, and null for a member who holds none.
 *
 * @param isAdministrator whether this member holds CLAN_ADMIN. Phase 2 answered this with "did they
 *     found the clan?", which was the only definition it had; now any number of people can, and the
 *     member list is where somebody looks to find out who to ask
 */
public record MemberView(
        UUID userId,
        String displayName,
        String email,
        UUID avatarMediaId,
        UUID homeBranchId,
        String homeBranchName,
        Instant joinedAt,
        boolean isAdministrator,
        UUID linkedPersonId) {}
