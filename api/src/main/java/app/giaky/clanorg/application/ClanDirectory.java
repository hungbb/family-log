package app.giaky.clanorg.application;

import app.giaky.clanorg.domain.Branch;
import app.giaky.clanorg.domain.BranchRepository;
import app.giaky.clanorg.domain.Clan;
import app.giaky.clanorg.domain.ClanMembership;
import app.giaky.clanorg.domain.ClanMembershipRepository;
import app.giaky.clanorg.domain.ClanRepository;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ClanOrg's published window onto memberships and branch structure, for the contexts that need to
 * ask about them without reaching into this one.
 *
 * <p>The same shape, and the same reason, as Identity's {@code UserDirectory}. Authorization has to
 * answer two questions on every check — "is this person still a member?" and "is that branch inside
 * this one?" — and both are facts ClanOrg owns. Without a service like this, the resolver's adapters
 * would import {@code clanorg.domain} and doc 02 §7 rule 1 would be gone by the second thing that
 * wanted it.
 *
 * <p>It answers in primitives and in {@link BranchRef}, which carries the path and the root person
 * and nothing else: enough to decide containment, not enough to reconstruct a {@code Branch}.
 *
 * <p>Every method reads. Nothing here decides anything about access — the caller is the one holding
 * the policy, and a directory that started refusing questions would become a second one.
 */
@Service
public class ClanDirectory {

    private final ClanMembershipRepository memberships;
    private final BranchRepository branches;
    private final ClanRepository clans;

    public ClanDirectory(
            ClanMembershipRepository memberships,
            BranchRepository branches,
            ClanRepository clans) {

        this.memberships = memberships;
        this.branches = branches;
        this.clans = clans;
    }

    /**
     * Clan names by id, for lists that span clans.
     *
     * <p>"My claims" on {@code /me} (F6.4) is the caller: it shows what somebody asked for across
     * every family they belong to, and a row that named only a UUID would be unreadable. Names only
     * — a label is not a licence to read the clan.
     *
     * @return the names by id; ids with no clan are simply absent, which is the right answer for a
     *     request whose clan has since been deleted
     */
    @Transactional(readOnly = true)
    public Map<ClanId, String> namesOf(Collection<ClanId> clanIds) {
        if (clanIds.isEmpty()) {
            return Map.of();
        }
        Map<ClanId, String> names = new LinkedHashMap<>();
        for (Clan clan : clans.findAllById(List.copyOf(clanIds))) {
            names.put(clan.id(), clan.name());
        }
        return names;
    }

    /** Whether the user holds an {@code ACTIVE} membership — the gate on every grant (doc 04 §1.6). */
    @Transactional(readOnly = true)
    public boolean isActiveMember(ClanId clanId, UserId userId) {
        return memberships.findActive(clanId, userId).isPresent();
    }

    /**
     * The branch a member sits in, when they have one.
     *
     * <p>Used by join approval to decide the scope of the {@code CLAN_VIEW} grant it issues (doc 04
     * §6): a member admitted into a chi can see the clan from inside that chi.
     */
    @Transactional(readOnly = true)
    public Optional<BranchId> homeBranchOf(ClanId clanId, UserId userId) {
        return memberships.findActive(clanId, userId).map(ClanMembership::homeBranchId);
    }

    /** Every live branch of the clan, as much of each as another context is entitled to see. */
    @Transactional(readOnly = true)
    public List<BranchRef> branches(ClanId clanId) {
        return branches.findAllByClan(clanId).stream().map(BranchRef::of).toList();
    }

    /** One branch, or empty when it is deleted or belongs to another clan. */
    @Transactional(readOnly = true)
    public Optional<BranchRef> branch(ClanId clanId, BranchId branchId) {
        return branches.findById(clanId, branchId).map(BranchRef::of);
    }

    /**
     * A branch reduced to what containment questions need.
     *
     * @param path the materialized ancestry; prefix comparison against it is what {@code BRANCH}
     *     scope means (doc 04 §3)
     * @param rootPersonId the person the branch descends from; null until Phase 4 records it
     */
    public record BranchRef(
            BranchId id,
            BranchId parentBranchId,
            String name,
            String path,
            int depth,
            java.util.UUID rootPersonId,
            boolean archived) {

        static BranchRef of(Branch branch) {
            return new BranchRef(
                    branch.id(),
                    branch.parentBranchId(),
                    branch.name(),
                    branch.path().value(),
                    branch.depth(),
                    branch.branchRootPersonId(),
                    branch.isArchived());
        }
    }
}
