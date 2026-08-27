package app.giaky.clanorg.domain;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.util.List;
import java.util.Optional;

/** Storage for {@link ClanMembership}. */
public interface ClanMembershipRepository {

    /** The membership that decides whether this caller may see this clan at all. */
    Optional<ClanMembership> findActive(ClanId clanId, UserId userId);

    /**
     * Every clan this user is currently in — the clan switcher (FR-C8).
     *
     * <p>The one read here without a {@link ClanId}, because the question is "which tenants?" and
     * the answer cannot presuppose one. The adapter carries a {@code @TenantExempt} justification
     * rather than smuggling it past the rule.
     */
    List<ClanMembership> findActiveByUser(UserId userId);

    /**
     * Every active member of a clan, newest first.
     *
     * <p>Not paged in SQL, and that is a considered choice. The member list filters by a name search
     * (doc 05 §4.5), and names live in Identity — pushing the filter into this query would mean
     * joining another context's table, which doc 02 §7 forbids. So the service loads the clan's
     * memberships, intersects them with what Identity's own search returned, and pages the result in
     * memory. The cost is proportional to the number of <i>accounts</i> in one clan — tens,
     * occasionally hundreds — which, unlike the tree, does not grow.
     */
    List<ClanMembership> findAllActive(ClanId clanId);

    /**
     * How many active members sit in each branch, for the counts the branch tree shows.
     *
     * @return one entry per branch that has members; branches with none are absent
     */
    List<BranchMemberCount> countActiveMembersPerBranch(ClanId clanId);

    ClanMembership save(ClanMembership membership);

    /** One row of {@link #countActiveMembersPerBranch}. */
    record BranchMemberCount(BranchId branchId, long memberCount) {}
}
