package app.giaky.linking.infrastructure;

import app.giaky.clanorg.application.ClanDirectory;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import app.giaky.linking.domain.ClanMemberPort;
import org.springframework.stereotype.Component;

/** {@link ClanMemberPort} over ClanOrg's published {@link ClanDirectory} (I-P5, I-K2). */
@Component
public class ClanOrgClanMemberAdapter implements ClanMemberPort {

    private final ClanDirectory clans;

    public ClanOrgClanMemberAdapter(ClanDirectory clans) {
        this.clans = clans;
    }

    @Override
    public boolean isActiveMember(ClanId clanId, UserId userId) {
        return clans.isActiveMember(clanId, userId);
    }
}
