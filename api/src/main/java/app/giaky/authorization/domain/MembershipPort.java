package app.giaky.authorization.domain;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;

/**
 * "Is this person still in this clan?" — step 2 of the algorithm (doc 04 §4).
 *
 * <p>Membership gates everything (doc 04 §1.6). A grant belonging to somebody who has left is not a
 * grant that has been forgotten about; it is a grant that confers nothing, and asking this question
 * first is what makes that true without a cascade of deletes when a membership ends.
 *
 * <p>The implementation goes through ClanOrg's published {@code ClanDirectory}, not its repository:
 * memberships belong to that context (doc 02 §7 rule 1).
 */
public interface MembershipPort {

    /** Whether {@code user} holds an {@code ACTIVE} membership in {@code clan}. */
    boolean isActiveMember(ClanId clan, UserId user);
}
