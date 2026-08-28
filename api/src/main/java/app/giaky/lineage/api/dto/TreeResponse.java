package app.giaky.lineage.api.dto;

import app.giaky.lineage.application.TreeView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The tree, flat (doc 05 §4.9).
 *
 * <p>Four lists the client indexes by id, not a nested structure. A family graph has no single
 * spanning tree — a child has two parents — so any nesting duplicates somebody, and the duplication
 * grows with every second marriage. Assembling it client-side is smaller on the wire and is the only
 * shape that survives the data.
 *
 * @param rootPersonIds where the render starts: everybody with no parent inside this payload
 * @param treeVersion the counter the {@code ETag} is built from; a client holding it may send
 *     {@code If-None-Match} and get a {@code 304}
 */
@Schema(description = "A normalized tree payload the client assembles.")
public record TreeResponse(
        UUID clanId,
        List<UUID> rootPersonIds,
        List<PersonSummaryResponse> persons,
        List<ParentageResponse> parentages,
        List<UnionResponse> unions,
        List<TreeBranchResponse> branches,
        MetaResponse meta,
        long treeVersion) {

    /** A branch, reduced to what a tree render labels a node with. */
    public record TreeBranchResponse(
            UUID id, String name, String path, int depth, UUID rootPersonId) {}

    /**
     * @param truncated a depth bound cut the subtree short, so the client can offer to load more
     *     rather than showing a family that appears to end
     */
    public record MetaResponse(
            int personCount, Integer maxGeneration, boolean truncated, Instant generatedAt) {}

    public static TreeResponse of(TreeView view) {
        return new TreeResponse(
                view.clanId(),
                view.rootPersonIds(),
                view.persons().stream().map(PersonSummaryResponse::of).toList(),
                view.parentages().stream().map(ParentageResponse::of).toList(),
                view.unions().stream().map(UnionResponse::of).toList(),
                view.branches().stream()
                        .map(
                                branch ->
                                        new TreeBranchResponse(
                                                branch.id(),
                                                branch.name(),
                                                branch.path(),
                                                branch.depth(),
                                                branch.rootPersonId()))
                        .toList(),
                new MetaResponse(
                        view.meta().personCount(),
                        view.meta().maxGeneration(),
                        view.meta().truncated(),
                        view.meta().generatedAt()),
                view.treeVersion());
    }
}
