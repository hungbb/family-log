package app.giaky.lineage.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.lineage.domain.TreeSnapshotCache;
import app.giaky.lineage.domain.TreeSnapshotRepository.TreeSnapshot;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@link TreeSnapshotCache} on Caffeine (B5.3).
 *
 * <p><b>Bounded by weight, not by count.</b> An entry is one clan's whole tree, and clans differ by
 * three orders of magnitude — ten people in a family somebody is trying the product with, five
 * thousand in the fixture NFR-1 is measured against. A cache of "200 entries" is therefore either
 * nothing or gigabytes depending on who is signed in, so the weight of an entry is its node and edge
 * count and the bound is stated in people.
 *
 * <p><b>Expiry is on idle, not on write.</b> A write-based expiry would evict entries that are still
 * exactly correct — the key already carries the tree version, so an entry is either current or
 * unreachable, and one that nobody has asked for in ten minutes is simply one nobody is looking at.
 * Superseded versions fall out the same way: after an edit, the old key is dead and ages out.
 */
@Component
public class CaffeineTreeSnapshotCache implements TreeSnapshotCache {

    private final Cache<Key, TreeSnapshot> cache;

    public CaffeineTreeSnapshotCache(
            @Value("${app.lineage.tree-cache.max-people:200000}") long maxPeople,
            @Value("${app.lineage.tree-cache.expire-after-access:10m}") Duration expireAfterAccess) {

        this.cache =
                Caffeine.newBuilder()
                        .maximumWeight(maxPeople)
                        .weigher(
                                (Key key, TreeSnapshot snapshot) ->
                                        // Rows, near enough: what an entry costs is dominated by the
                                        // per-person record, and the edges travel with it.
                                        snapshot.persons().size()
                                                + snapshot.parentages().size()
                                                + snapshot.unions().size())
                        .expireAfterAccess(expireAfterAccess)
                        .recordStats()
                        .build();
    }

    @Override
    public TreeSnapshot get(ClanId clanId, long treeVersion, Supplier<TreeSnapshot> loader) {
        return cache.get(new Key(clanId.value(), treeVersion), key -> loader.get());
    }

    @Override
    public long size() {
        cache.cleanUp();
        return cache.estimatedSize();
    }

    /** The clan and the version of its tree. Superseding a version is what invalidates an entry. */
    private record Key(UUID clanId, long treeVersion) {}
}
