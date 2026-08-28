package app.giaky.authorization.infrastructure;

import app.giaky.authorization.domain.BranchHierarchyPort;
import app.giaky.clanorg.application.ClanDirectory;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * {@link BranchHierarchyPort} over ClanOrg's published branch list.
 *
 * <p>Loads the clan's branches once per request and answers every containment question from that
 * list. A clan has tens of branches — the depth cap and the way families actually divide see to that
 * — so one list is cheaper than the several targeted lookups a single mutation would otherwise make,
 * and far cheaper than the same lookups repeated for every node of a batch.
 */
@Component
public class ClanOrgBranchHierarchyAdapter implements BranchHierarchyPort {

    private final ClanDirectory clans;
    private final AuthorizationRequestCache cache;

    public ClanOrgBranchHierarchyAdapter(ClanDirectory clans, AuthorizationRequestCache cache) {
        this.clans = clans;
        this.cache = cache;
    }

    @Override
    public Optional<String> pathOf(ClanId clan, BranchId branch) {
        return Optional.ofNullable(byId(clan).get(branch)).map(ClanDirectory.BranchRef::path);
    }

    @Override
    public List<BranchPath> allPaths(ClanId clan) {
        return byId(clan).values().stream()
                .map(ref -> new BranchPath(ref.id(), ref.path()))
                .toList();
    }

    @Override
    public Optional<UUID> rootPersonOf(ClanId clan, BranchId branch) {
        return Optional.ofNullable(byId(clan).get(branch))
                .map(ClanDirectory.BranchRef::rootPersonId);
    }

    /**
     * The clan's branches by id, cached for the request.
     *
     * <p>Includes archived branches on purpose. Archiving retires a branch from receiving new
     * members; it does not move the people already in it, and a grant scoped there must keep
     * covering them until somebody revokes it.
     */
    private Map<BranchId, ClanDirectory.BranchRef> byId(ClanId clan) {
        return cache.get(
                AuthorizationRequestCache.CacheKey.branchesOf(clan),
                () ->
                        clans.branches(clan).stream()
                                .collect(
                                        Collectors.toMap(
                                                ClanDirectory.BranchRef::id,
                                                Function.identity(),
                                                (first, second) -> first)));
    }
}
