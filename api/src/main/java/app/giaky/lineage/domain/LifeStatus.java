package app.giaky.lineage.domain;

/**
 * Whether the person is living (FR-P2).
 *
 * <p>Load-bearing beyond display in two places. I-P3 refuses a death date on somebody marked
 * {@link #ALIVE}, and doc 04 §5.5 hides a living person's sensitive fields from non-members — so a
 * node wrongly marked {@link #DECEASED} is a privacy decision made by a data-entry mistake.
 *
 * <p>{@link #UNKNOWN} is the honest answer for most of a book: a name from six generations back is
 * certainly dead, but the record does not say it, and the product does not infer facts.
 */
public enum LifeStatus {
    ALIVE,
    DECEASED,
    UNKNOWN
}
