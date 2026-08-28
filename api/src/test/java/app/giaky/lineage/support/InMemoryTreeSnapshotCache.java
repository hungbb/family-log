package app.giaky.lineage.support;

import app.giaky.common.types.ClanId;
import app.giaky.lineage.domain.TreeSnapshotCache;
import app.giaky.lineage.domain.TreeSnapshotRepository.TreeSnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@link TreeSnapshotCache} as a plain map, with a count of how often the loader actually ran.
 *
 * <p>Unbounded and never expiring, which is exactly wrong for production and exactly right here: a
 * test that asserts "the second read did not scan again" must not race an eviction policy. The
 * count is the assertion — the cache's whole contract is that a repeat read at the same tree version
 * does not reach the database, and that a bumped version does.
 */
public final class InMemoryTreeSnapshotCache implements TreeSnapshotCache {

    private final Map<Key, TreeSnapshot> entries = new HashMap<>();

    /** How many times a miss fell through to the loader. */
    public int loads;

    @Override
    public TreeSnapshot get(ClanId clanId, long treeVersion, Supplier<TreeSnapshot> loader) {
        return entries.computeIfAbsent(
                new Key(clanId.value(), treeVersion),
                key -> {
                    loads++;
                    return loader.get();
                });
    }

    @Override
    public long size() {
        return entries.size();
    }

    private record Key(UUID clanId, long treeVersion) {}
}
