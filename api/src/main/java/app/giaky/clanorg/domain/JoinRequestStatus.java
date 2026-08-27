package app.giaky.clanorg.domain;

/**
 * The state machine of a {@link ClanJoinRequest} (I-J4).
 *
 * <p>One-way: {@code PENDING} is the only state anything leaves, and nothing ever returns to it. A
 * rejected applicant submits a new request rather than reopening the old one, so the record of what
 * was decided stays exactly as it was decided.
 */
public enum JoinRequestStatus {

    PENDING,
    APPROVED,
    REJECTED,
    /** Withdrawn by the requester. */
    CANCELLED,
    /** Closed unanswered after 90 days by the scheduled sweep. */
    EXPIRED;

    public boolean isPending() {
        return this == PENDING;
    }

    /** Whether a request in this state may still be decided — true for exactly one of them. */
    public boolean isDecidable() {
        return this == PENDING;
    }
}
