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
    MOTHER;

    /**
     * Which of a couple to record as the father of their child.
     *
     * <p>By gender — of either partner, since knowing one is a wife says the other is the husband —
     * and the canonical storage order when both partners share a gender. That fallback is arbitrary
     * and is meant to be: an edge has to name a role, both roles exist, and a wrong guess is a
     * correction an editor makes in one click.
     *
     * <p>One definition, because there were two. Creating a child under a marriage asked this
     * question; attaching an existing child to the same marriage asked a shorter one — "partner A,
     * unless A is recorded as female" — and so recorded a man as the <i>mother</i> whenever his
     * wife's gender was blank and she happened to be stored first.
     */
    public static Person fatherAmong(Person partnerA, Person partnerB) {
        if (partnerA.gender() == Gender.MALE || partnerB.gender() == Gender.FEMALE) {
            return partnerA;
        }
        if (partnerB.gender() == Gender.MALE || partnerA.gender() == Gender.FEMALE) {
            return partnerB;
        }
        return partnerA;
    }
}
