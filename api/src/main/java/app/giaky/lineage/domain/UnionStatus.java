package app.giaky.lineage.domain;

/**
 * Where a union stands (doc 02 §2.7).
 *
 * <p>{@link #UNKNOWN} is the default, because a book that lists a wife rarely says how the marriage
 * ended, and {@link #ACTIVE} would be an assertion about people who are almost certainly dead.
 */
public enum UnionStatus {
    ACTIVE,
    ENDED,
    WIDOWED,
    UNKNOWN
}
