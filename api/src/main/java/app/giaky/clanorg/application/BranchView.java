package app.giaky.clanorg.application;

import app.giaky.clanorg.domain.Branch;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A branch, and — when it came from the tree endpoint — everything below it.
 *
 * <p>One type for both the tree and the detail view. A flat list the client assembles would move the
 * same work into every consumer for a structure the depth cap keeps small; nesting it here makes the
 * AntD {@code Tree} on the other side a direct render.
 *
 * @param path the materialized path; sent because the client sorts and indents by it
 * @param memberCount active members whose home branch is this one — not a subtree total
 */
public record BranchView(
        UUID id,
        UUID clanId,
        UUID parentBranchId,
        String branchCode,
        String name,
        String description,
        String path,
        int depth,
        boolean archived,
        Instant archivedAt,
        long memberCount,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<BranchView> children) {

    static BranchView of(Branch branch, long memberCount, List<BranchView> children) {
        return new BranchView(
                branch.id().value(),
                branch.clanId().value(),
                branch.parentBranchId() == null ? null : branch.parentBranchId().value(),
                branch.branchCode().value(),
                branch.name(),
                branch.description(),
                branch.path().value(),
                branch.depth(),
                branch.isArchived(),
                branch.archivedAt(),
                memberCount,
                branch.version(),
                branch.createdAt(),
                branch.updatedAt(),
                List.copyOf(children));
    }
}
