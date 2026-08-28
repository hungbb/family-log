package app.giaky.lineage.domain;

/**
 * Which parent an edge records (doc 02 §2.6).
 *
 * <p>Two values, and I-R3 allows one edge of each per child. That is a modelling choice with teeth:
 * an adopted child does not get a third edge, they get a second edge with {@link
 * ParentageNature#ADOPTED} — because <i>con nuôi</i> in a family book is a nature, not an extra
 * parent, and the book usually still names the birth father.
 */
public enum ParentRole {
    FATHER,
    MOTHER
}
