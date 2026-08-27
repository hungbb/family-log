package app.giaky.clanorg.application;

import java.time.Instant;
import java.util.UUID;

/**
 * One member of a clan (doc 05 §4.5).
 *
 * <p>The name and address come from Identity through {@code UserDirectory}; ClanOrg holds only the
 * id (doc 02 §1.1). {@code linkedPersonId} is in the contract from doc 05 §4.5 and stays null until
 * Phase 6 links accounts to nodes — shipping the field now means the UI does not change shape then.
 *
 * @param isCreator whether this member founded the clan. Until Phase 3 that is the same thing as
 *     administrator, and the member list is where somebody looks to find out who to ask
 */
public record MemberView(
        UUID userId,
        String displayName,
        String email,
        UUID avatarMediaId,
        UUID homeBranchId,
        String homeBranchName,
        Instant joinedAt,
        boolean isCreator,
        UUID linkedPersonId) {}
