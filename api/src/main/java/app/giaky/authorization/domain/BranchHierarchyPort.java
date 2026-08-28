package app.giaky.authorization.domain;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import java.util.List;
import java.util.Optional;

/**
 * The shape of the branch tree, as authorization needs to see it (doc 04 §3, §5.4).
 *
 * <p>{@code BRANCH} scope means "that branch and everything under it", and the materialized path is
 * what turns that into a string comparison rather than a walk. This port hands the resolver those
 * paths; the implementation reads them from ClanOrg's published {@code ClanDirectory}.
 */
public interface BranchHierarchyPort {

    /**
     * A branch's materialized path.
     *
     * @return empty when the branch does not exist in this clan — which the resolver treats as a
     *     denial, never as "no constraint" (doc 04 §1.5)
     */
    Optional<String> pathOf(ClanId clan, BranchId branch);

    /** Every branch in the clan with its path, for building an {@link EditableSet} in one pass. */
    List<BranchPath> allPaths(ClanId clan);

    /**
     * The person a branch descends from, when one is recorded.
     *
     * <p>Needed by exactly one rule — whether a {@code SUBTREE} grant covers a branch (doc 04 §5.4)
     * — and always empty until Phase 4 sets {@code branches.branch_root_person_id}.
     */
    Optional<java.util.UUID> rootPersonOf(ClanId clan, BranchId branch);

    /** One branch's identity and place in the tree. */
    record BranchPath(BranchId branchId, String path) {}
}
