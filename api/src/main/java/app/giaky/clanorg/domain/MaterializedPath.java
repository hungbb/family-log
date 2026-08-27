package app.giaky.clanorg.domain;

import app.giaky.common.types.BranchId;
import java.util.List;

/**
 * A branch's ancestry, flattened into a string: {@code /a1b2c3d4/9f8e7d6c/} (doc 02 §3).
 *
 * <p>Every segment is a {@link BranchId#pathSegment()}, root first, own segment last, with a leading
 * and trailing slash. The trailing slash is not decoration — without it {@code /a1b2c3d4/} would
 * also prefix-match a hypothetical {@code /a1b2c3d40.../}, and "descendant of" would quietly stop
 * meaning what it says.
 *
 * <p>Why a materialized path rather than a recursive query every time: authorization asks "is this
 * branch inside that one?" on every permission check (doc 04 §5.4), and a prefix comparison against
 * an indexed column answers it without touching the rest of the tree. The cost is that re-parenting
 * has to rewrite a subtree — bounded by the depth cap (I-C7), and covered by its own property test.
 */
public record MaterializedPath(String value) {

    /** {@code branches.path} is {@code VARCHAR(512)}; a depth-10 path needs 99 characters. */
    public static final int MAX_LENGTH = 512;

    private static final char SEPARATOR = '/';

    public MaterializedPath {
        if (value == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (value.length() < 3
                || value.charAt(0) != SEPARATOR
                || value.charAt(value.length() - 1) != SEPARATOR) {
            throw new IllegalArgumentException("path must start and end with '/': " + value);
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("path must be at most " + MAX_LENGTH + " characters");
        }
    }

    public static MaterializedPath of(String raw) {
        return new MaterializedPath(raw);
    }

    /** The path of a clan's root branch: one segment, its own. */
    public static MaterializedPath root(BranchId branchId) {
        return new MaterializedPath(SEPARATOR + branchId.pathSegment() + SEPARATOR);
    }

    /** This path extended by one more branch — the path a child of this branch would have. */
    public MaterializedPath child(BranchId childId) {
        return new MaterializedPath(value + childId.pathSegment() + SEPARATOR);
    }

    /** The segments, root first. */
    public List<String> segments() {
        return List.of(value.substring(1, value.length() - 1).split("/"));
    }

    /** Distance from the root: 0 for a root branch. Always equal to the stored {@code depth} (I-C4). */
    public int depth() {
        return segments().size() - 1;
    }

    /**
     * Whether {@code this} lies at or below {@code ancestor}.
     *
     * <p>Reflexive on purpose: a branch is inside itself, which is what every "may I act on this
     * subtree" question means. The strict form is {@link #isStrictDescendantOf}.
     */
    public boolean isWithin(MaterializedPath ancestor) {
        return value.startsWith(ancestor.value);
    }

    /** {@link #isWithin} minus the branch itself — the test a move needs to reject a cycle (I-C3). */
    public boolean isStrictDescendantOf(MaterializedPath ancestor) {
        return !equals(ancestor) && isWithin(ancestor);
    }

    /**
     * The SQL {@code LIKE} pattern matching this branch and everything under it.
     *
     * <p>Safe to interpolate only because a path is built from hex segments and slashes — there is
     * no {@code %} or {@code _} a branch id could contribute. It is still passed as a bound
     * parameter everywhere it is used.
     */
    public String subtreeLikePattern() {
        return value + "%";
    }

    @Override
    public String toString() {
        return value;
    }
}
