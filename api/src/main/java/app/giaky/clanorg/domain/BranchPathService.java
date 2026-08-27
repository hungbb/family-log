package app.giaky.clanorg.domain;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * Keeps {@code path} in step with {@code parentBranchId} (doc 02 §5, I-C4).
 *
 * <p>Re-parenting is the risky operation of this phase (phase 2 doc §8): it invalidates the path of
 * every branch below the one that moved. Doing it in two places — the moved branch here, its
 * descendants in a statement somewhere else — is how the two representations of the tree drift
 * apart. So it happens in exactly one method, and that method is the only writer of a moved path.
 *
 * <p>A domain service rather than a static helper because it needs a port: the descendant rewrite is
 * one recursive SQL statement (doc 06 §7), bounded by the depth cap, and it belongs in the database
 * rather than in a loop that reads and saves ten thousand rows.
 */
public final class BranchPathService {

    private final BranchRepository branches;

    public BranchPathService(BranchRepository branches) {
        this.branches = Objects.requireNonNull(branches, "branches must not be null");
    }

    /**
     * Moves {@code branch} under {@code newParent}, rewriting the whole subtree.
     *
     * <p>Two writes, one transaction — the caller's. The moved branch is saved with its new parent,
     * path and depth, and then every descendant has its path and depth recomputed from the parent
     * links. Between those two statements the tree is inconsistent, which is exactly why they may
     * never be separated by a transaction boundary.
     *
     * <p>Validation (no cycle, same clan, no archived parent, depth cap) lives in {@link
     * Branch#reparentedTo}, which this calls first: an invalid move throws before anything is
     * written.
     *
     * @return the moved branch as stored
     * @throws IllegalArgumentException the move is not legal (I-C3, I-C7)
     */
    public MoveResult move(Branch branch, Branch newParent, UserId editor, Instant now) {
        Branch moved = branch.reparentedTo(newParent, editor, now);
        // The subtree's deepest branch rises or falls by the same amount the moved branch does, so
        // one comparison covers I-C7 for the entire subtree without walking it.
        requireSubtreeWithinDepthLimit(branch, moved);

        Branch saved = branches.save(moved);
        int rewritten = branches.rewriteSubtreePaths(saved.clanId(), saved.id(), now, editor);
        return new MoveResult(saved, rewritten);
    }

    /**
     * Refuses a move that would push some descendant past {@link Branch#MAX_DEPTH}.
     *
     * <p>The moved branch alone passing the cap is not enough: moving a two-deep subtree from depth
     * 3 to depth 9 puts its leaves at 11. Without this, the database's {@code ck_branches_depth}
     * would catch it — halfway through the rewrite, as a constraint violation nobody can read.
     */
    private void requireSubtreeWithinDepthLimit(Branch before, Branch after) {
        int shift = after.depth() - before.depth();
        if (shift <= 0) {
            return;
        }
        int deepest = deepestDescendantDepth(before);
        if (deepest + shift > Branch.MAX_DEPTH) {
            throw new IllegalArgumentException(
                    "the move would put a descendant branch at depth "
                            + (deepest + shift)
                            + ", past the limit of "
                            + Branch.MAX_DEPTH
                            + " (I-C7)");
        }
    }

    private int deepestDescendantDepth(Branch root) {
        return branches.findAllByClan(root.clanId()).stream()
                .filter(candidate -> candidate.path().isWithin(root.path()))
                .mapToInt(Branch::depth)
                .max()
                .orElse(root.depth());
    }

    /** The path a new child of {@code parent} would take. */
    public static MaterializedPath childPathOf(Branch parent, BranchId childId) {
        return parent.path().child(childId);
    }

    /**
     * The outcome of a move.
     *
     * @param branch the moved branch, as stored
     * @param descendantsRewritten how many rows the subtree rewrite touched; logged, and asserted on
     *     by the move tests
     */
    public record MoveResult(Branch branch, int descendantsRewritten) {}
}
