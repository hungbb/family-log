package app.giaky.lineage.domain;

/**
 * What kind of marriage a union records (doc 02 §2.7).
 *
 * <p>The three values exist because real <i>gia phả</i> contain all three, and a model with only one
 * would force whoever is transcribing to choose which wife to omit. R4 in the roadmap names this as
 * a modelling risk worth validating against a real book before Phase 5.
 */
public enum UnionType {

    /** <i>Vợ cả</i> — the principal marriage. At most one live one per person (I-N3). */
    PRIMARY,

    /** <i>Vợ lẽ</i> — concurrent with a primary union, and normal in the books this digitizes. */
    SECONDARY,

    /** A remarriage after an earlier union ended. Concurrent with nothing. */
    SEQUENTIAL
}
