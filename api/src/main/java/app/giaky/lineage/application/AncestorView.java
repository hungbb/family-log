package app.giaky.lineage.application;

/**
 * One step of an ancestor chain (doc 05 §4.9).
 *
 * <p>The breadcrumb above a person. Not a single line, because parentage is a DAG: somebody has a
 * father's line and a mother's line, and both are their ancestry. The list is ordered by {@code
 * depth}, so the client can render the nearest generations first and let the rest fold away.
 *
 * @param depth generations up from the person asked about; 0 is the person themselves
 */
public record AncestorView(PersonSummaryView person, int depth) {}
