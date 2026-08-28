package app.giaky.authorization.infrastructure;

import app.giaky.authorization.domain.MembershipPort;
import app.giaky.clanorg.application.ClanDirectory;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import org.springframework.stereotype.Component;

/**
 * {@link MembershipPort} answered by ClanOrg's published {@link ClanDirectory}.
 *
 * <p>Through the directory rather than the membership table, because memberships belong to ClanOrg
 * (doc 02 §7 rule 1). The indirection costs a method call and buys the property that Authorization
 * cannot be broken by a change to how ClanOrg stores who is in a clan.
 */
@Component
public class ClanOrgMembershipAdapter implements MembershipPort {

    private final ClanDirectory clans;

    public ClanOrgMembershipAdapter(ClanDirectory clans) {
        this.clans = clans;
    }

    @Override
    public boolean isActiveMember(ClanId clan, UserId user) {
        return clans.isActiveMember(clan, user);
    }
}
