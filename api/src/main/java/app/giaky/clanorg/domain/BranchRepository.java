package app.giaky.clanorg.domain;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Storage for {@link Branch} entities.
 *
 * <p>Every read takes a {@link ClanId} (doc 02 §7 rule 4). It is not ceremony: a branch id is a
 * UUID a caller supplies, and the difference between a 404 and another clan's structure is exactly
 * this parameter. {@code TenancyBoundaryIT} exists to prove it.
 */
public interface BranchRepository {

    /** One branch, or empty when it is deleted, or belongs to another clan. */
    Optional<Branch> findById(ClanId clanId, BranchId id);

    /** Every live branch of a clan, root first. Bounded by the depth cap and by how families divide. */
    List<Branch> findAllByClan(ClanId clanId);

    /**
     * The join-by-branch-code lookup. Unscoped, like {@link ClanRepository#findByClanCode} and for
     * the same reason: the person presenting the code is not a member of anything yet.
     */
    Optional<Branch> findByBranchCode(BranchCode code);

    boolean isBranchCodeTaken(String code);

    /** Whether a path is already in use in this clan — the guard behind {@code ux_branches_path}. */
    boolean isPathTaken(ClanId clanId, MaterializedPath path);

    /** How many live children a branch has. I-C6 refuses to archive while this is non-zero. */
    long countLiveChildren(ClanId clanId, BranchId parentId);

    Branch save(Branch branch);

    /**
     * Rewrites {@code path} and {@code depth} for every branch below {@code root}, in one statement.
     *
     * <p>The other half of a move (I-C4). {@code root} itself is expected to have been saved already
     * with its new parent, path and depth; this recomputes its descendants from the parent links, so
     * the result is derived from the tree rather than from string surgery on the old paths — a
     * subtree whose paths had drifted comes out correct rather than consistently wrong.
     *
     * @return how many descendant rows were rewritten
     */
    int rewriteSubtreePaths(ClanId clanId, BranchId root, Instant now, UserId editor);
}
