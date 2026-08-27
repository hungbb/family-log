package app.giaky.clanorg.api.dto;

import app.giaky.clanorg.application.BranchView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A branch, nested (doc 05 §4.4).
 *
 * <p>{@code children} is populated by the tree endpoint and empty everywhere else — a detail read
 * answers about one branch, and a caller who wanted the shape of the tree asked the tree.
 */
public record BranchResponse(
        UUID id,
        UUID clanId,
        UUID parentBranchId,
        @Schema(description = "Shareable code. Branch-scoped joining arrives in the next release.")
                String branchCode,
        String name,
        String description,
        @Schema(description = "Materialized ancestry, e.g. /a1b2c3d4/9f8e7d6c/") String path,
        int depth,
        boolean archived,
        Instant archivedAt,
        @Schema(description = "Active members whose home branch is this one, not a subtree total")
                long memberCount,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<BranchResponse> children) {

    public static BranchResponse of(BranchView view) {
        return new BranchResponse(
                view.id(),
                view.clanId(),
                view.parentBranchId(),
                view.branchCode(),
                view.name(),
                view.description(),
                view.path(),
                view.depth(),
                view.archived(),
                view.archivedAt(),
                view.memberCount(),
                view.version(),
                view.createdAt(),
                view.updatedAt(),
                view.children().stream().map(BranchResponse::of).toList());
    }
}
