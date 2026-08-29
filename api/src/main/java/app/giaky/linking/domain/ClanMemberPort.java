package app.giaky.linking.domain;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;

/**
 * Membership, as I-P5 and I-K2 need it.
 *
 * <p>Its own port rather than a call into {@code PermissionResolver}, because the question is not
 * about permission: a linked user needs an <i>active membership</i>, not a grant, and asking the
 * resolver would tie the invariant to a class that could later answer it for a different reason.
 */
public interface ClanMemberPort {

    /** Whether the account holds an {@code ACTIVE} membership in the clan. */
    boolean isActiveMember(ClanId clanId, UserId userId);
}
