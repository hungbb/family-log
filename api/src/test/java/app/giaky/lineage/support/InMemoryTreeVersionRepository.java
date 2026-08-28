package app.giaky.lineage.support;

import app.giaky.common.types.ClanId;
import app.giaky.lineage.domain.TreeVersionRepository;
import java.util.HashMap;
import java.util.Map;

/** {@link TreeVersionRepository} as a counter per clan. */
public final class InMemoryTreeVersionRepository implements TreeVersionRepository {

    private final Map<ClanId, Long> versions = new HashMap<>();

    @Override
    public long current(ClanId clanId) {
        return versions.getOrDefault(clanId, 0L);
    }

    @Override
    public long bump(ClanId clanId) {
        return versions.merge(clanId, 1L, Long::sum);
    }
}
