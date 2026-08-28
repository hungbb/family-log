package app.giaky.lineage.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The whole tree, flat (doc 05 §4.9, FR-T2).
 *
 * <p><b>Normalized, not nested.</b> A nested payload has to choose a spanning tree, and a family
 * graph has no single one: a child has two parents, so nesting under fathers duplicates every mother
 * and nesting under both duplicates every child. Sending four flat lists and letting the client
 * index them by id is smaller on the wire and is the only shape that survives a second marriage.
 *
 * <p>Three indexed scans for a clan of any size (doc 03 §4) — persons, parentages, unions — plus the
 * branch list the client labels with.
 *
 * @param rootPersonIds the people with no recorded parent inside the returned set. The render starts
 *     here, and there is usually more than one: a founding ancestor, and every wife who married in
 * @param treeVersion the counter the {@code ETag} is built from (B4.12)
 */
public record TreeView(
        UUID clanId,
        List<UUID> rootPersonIds,
        List<PersonSummaryView> persons,
        List<ParentageView> parentages,
        List<UnionView> unions,
        List<TreeBranch> branches,
        Meta meta,
        long treeVersion) {

    /** A branch, reduced to what a tree render labels a node with. */
    public record TreeBranch(UUID id, String name, String path, int depth, UUID rootPersonId) {}

    /**
     * Counts and provenance for the payload.
     *
     * @param truncated true when a {@code depth} bound cut the subtree short, so the client can
     *     offer to load more rather than silently showing a partial family
     */
    public record Meta(
            int personCount, Integer maxGeneration, boolean truncated, Instant generatedAt) {}
}
