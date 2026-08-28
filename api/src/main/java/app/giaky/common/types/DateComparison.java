package app.giaky.common.types;

/**
 * The three-valued result of comparing two {@link PartialDate}s (doc 02 §3).
 *
 * <p>Three values rather than the usual {@code int}, because two partial dates genuinely may not be
 * comparable and a {@code Comparator} has no way to say so. "Born 1901, died 1901" cannot tell us
 * which came first, and neither can "born circa 1900" against "died 1902". Collapsing that into a
 * zero would make {@link PartialDate} look totally ordered, and the first thing built on the lie
 * would be a validation rule that rejects true records.
 *
 * <p>{@link #INDETERMINATE} therefore means <i>the data does not say</i> — never <i>equal</i>. Every
 * invariant that compares dates (I-P2 above all) treats it as "no objection", because refusing a
 * record the family actually has is a worse outcome than storing one whose order is unknown.
 */
public enum DateComparison {

    /** The left date certainly falls before the right one, at every precision they both carry. */
    BEFORE,

    /** The left date certainly falls after the right one. */
    AFTER,

    /**
     * The two ranges overlap, or one of them is unknown. Not an error and not equality: the record
     * simply does not settle the question.
     */
    INDETERMINATE
}
