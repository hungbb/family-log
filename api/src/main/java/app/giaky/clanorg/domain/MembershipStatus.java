package app.giaky.clanorg.domain;

/** The lifecycle of a {@link ClanMembership}. */
public enum MembershipStatus {

    /** In the clan. The only status that grants anything (I-M2). */
    ACTIVE,

    /** The member removed themselves. */
    LEFT,

    /** Somebody else removed them. Distinct from {@link #LEFT} because the audit answer differs. */
    REMOVED;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
