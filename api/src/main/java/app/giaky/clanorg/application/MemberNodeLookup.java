package app.giaky.clanorg.application;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.util.Collection;
import java.util.Map;

/**
 * Which node of the family tree each member is recorded as (doc 05 §4.5).
 *
 * <p>A port for the reason {@link LastAdminProtection} is one: the answer lives in another context.
 * ClanOrg knows who the members are and nothing about the tree; Linking knows which account sits on
 * which node. The member list needs both, and the picker an editor uses to assign a member to a
 * node (F6.6) needs it most — it is how somebody who already holds a node is shown as taken instead
 * of being offered and then refused.
 *
 * <p>Phase 2 shipped {@code linkedPersonId} in the contract and answered it with null; this is the
 * question that fills it in.
 */
public interface MemberNodeLookup {

    /**
     * The node each of {@code members} holds in {@code clanId}.
     *
     * @return one entry per member who holds a node; members who hold none are absent
     */
    Map<UserId, PersonId> nodesOf(ClanId clanId, Collection<UserId> members);
}
