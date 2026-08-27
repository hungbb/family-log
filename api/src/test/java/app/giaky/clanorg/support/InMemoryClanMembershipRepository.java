package app.giaky.clanorg.support;

import app.giaky.clanorg.domain.ClanMembership;
import app.giaky.clanorg.domain.ClanMembershipRepository;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A {@link ClanMembershipRepository} backed by a map.
 *
 * <p>Enforces I-M1 the way the database does — a second active membership for the same (clan, user)
 * is refused rather than quietly stored — because the tests that matter here are about what happens
 * when something tries to create one.
 */
public final class InMemoryClanMembershipRepository implements ClanMembershipRepository {

    private final Map<UUID, ClanMembership> byId = new LinkedHashMap<>();

    @Override
    public Optional<ClanMembership> findActive(ClanId clanId, UserId userId) {
        return byId.values().stream()
                .filter(ClanMembership::isActive)
                .filter(m -> m.clanId().equals(clanId) && m.userId().equals(userId))
                .findFirst();
    }

    @Override
    public List<ClanMembership> findActiveByUser(UserId userId) {
        return byId.values().stream()
                .filter(ClanMembership::isActive)
                .filter(m -> m.userId().equals(userId))
                .sorted(Comparator.comparing(ClanMembership::joinedAt).reversed())
                .toList();
    }

    @Override
    public List<ClanMembership> findAllActive(ClanId clanId) {
        return byId.values().stream()
                .filter(ClanMembership::isActive)
                .filter(m -> m.clanId().equals(clanId))
                .sorted(Comparator.comparing(ClanMembership::joinedAt).reversed())
                .toList();
    }

    @Override
    public List<BranchMemberCount> countActiveMembersPerBranch(ClanId clanId) {
        Map<BranchId, Long> counts = new LinkedHashMap<>();
        for (ClanMembership membership : findAllActive(clanId)) {
            if (membership.homeBranchId() != null) {
                counts.merge(membership.homeBranchId(), 1L, Long::sum);
            }
        }
        List<BranchMemberCount> rows = new ArrayList<>(counts.size());
        counts.forEach((branchId, count) -> rows.add(new BranchMemberCount(branchId, count)));
        return rows;
    }

    @Override
    public ClanMembership save(ClanMembership membership) {
        if (membership.isActive()) {
            // `ux_membership_active`, in a map. Without it a test could create the very state the
            // index exists to make impossible and then assert against a world that cannot happen.
            findActive(membership.clanId(), membership.userId())
                    .filter(existing -> !existing.id().equals(membership.id()))
                    .ifPresent(
                            existing -> {
                                throw new IllegalStateException(
                                        "I-M1: "
                                                + membership.userId()
                                                + " already has an active membership in clan "
                                                + membership.clanId());
                            });
        }
        byId.put(membership.id(), membership);
        return membership;
    }

    /** Including ended ones: leaving must keep the row rather than delete it. */
    public List<ClanMembership> all() {
        return List.copyOf(byId.values());
    }
}
