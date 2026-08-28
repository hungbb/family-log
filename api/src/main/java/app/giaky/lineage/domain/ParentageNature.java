package app.giaky.lineage.domain;

/**
 * How a parent-child relationship came about (FR-P6).
 *
 * <p>Recorded because the books record it. A <i>con nuôi</i> is listed as one, and a product that
 * flattened the distinction would be losing the very thing the family took care to write down —
 * while still, correctly, treating the edge as structural: an adopted child is in the tree, inherits
 * the branch, and counts as a descendant for {@code SUBTREE} scope.
 */
public enum ParentageNature {

    /** The default, and the overwhelming majority of edges. */
    BIOLOGICAL,

    /** <i>Con nuôi</i>. */
    ADOPTED,

    /** A parent by marriage, recorded where the book distinguishes it. */
    STEP,

    /** Raised by, without adoption. */
    FOSTER
}
