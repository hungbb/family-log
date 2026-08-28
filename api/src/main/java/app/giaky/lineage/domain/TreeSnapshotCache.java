package app.giaky.lineage.domain;

import app.giaky.common.types.ClanId;
import app.giaky.lineage.domain.TreeSnapshotRepository.TreeSnapshot;
import java.util.function.Supplier;

/**
 * The assembled tree, remembered per (clan, tree version) — B5.3.
 *
 * <p><b>What makes this cache correct is the key.</b> The clan's tree version is bumped inside every
 * lineage mutation's own transaction (B4.12), so a snapshot cached under version 41 can never be
 * served after the tree becomes version 42 — the reader asks for a key that does not exist yet and
 * loads it. There is no invalidation to get wrong, and no window in which a stale tree is served
 * under a fresh {@code ETag}.
 *
 * <p><b>What is cached is user-independent.</b> The three scans return the same rows for everybody
 * who may read the clan (doc 04 §5.5 — reads are clan-wide), so the per-user part of the payload —
 * the {@code canEdit} flag on each node — is stamped on <i>after</i> the cache, from the batch
 * resolver. Caching the finished payload instead would key on the user as well and would hold one
 * copy of the clan per member.
 *
 * <p>An in-process cache, which is the right size for v1: one instance, and a miss costs three
 * indexed scans rather than a request to another service. A second instance would keep its own copy
 * and reach the same answer, because the key already carries the version that makes them agree.
 */
public interface TreeSnapshotCache {

    /**
     * The snapshot for this clan at this version, loading it if it is not held.
     *
     * @param loader run only on a miss, and expected to return the snapshot at exactly {@code
     *     treeVersion} — the caller reads the version first, inside the same read-only transaction,
     *     so the two cannot disagree
     */
    TreeSnapshot get(ClanId clanId, long treeVersion, Supplier<TreeSnapshot> loader);

    /** How many entries are currently held. For the benchmark harness and the health of the cache. */
    long size();
}
