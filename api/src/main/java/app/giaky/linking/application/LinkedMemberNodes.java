package app.giaky.linking.application;

import app.giaky.clanorg.application.MemberNodeLookup;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.application.PersonLinkGateway;
import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@link MemberNodeLookup} answered from the link column (doc 05 §4.5).
 *
 * <p>Lives here rather than in ClanOrg for the reason {@code GrantBackedLastAdminProtection} lives
 * in Authorization: the fact belongs to this context, and the member list should not learn how it
 * is stored. One query for a page of members, through the same gateway every other read of the
 * link goes through.
 */
@Component
public class LinkedMemberNodes implements MemberNodeLookup {

    private final PersonLinkGateway lineage;

    public LinkedMemberNodes(PersonLinkGateway lineage) {
        this.lineage = lineage;
    }

    @Override
    public Map<UserId, PersonId> nodesOf(ClanId clanId, Collection<UserId> members) {
        return lineage.nodesLinkedTo(clanId, members);
    }
}
