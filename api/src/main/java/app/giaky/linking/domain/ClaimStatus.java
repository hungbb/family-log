package app.giaky.linking.domain;

/**
 * The state machine of a {@link NodeClaimRequest} (I-K5).
 *
 * <p>One-way, and deliberately shorter than {@code JoinRequestStatus}: there is no {@code EXPIRED}
 * here. A join request left unanswered blocks nothing and goes stale; a claim left unanswered is a
 * relative still waiting to be recognised, and closing it on a timer would tell them the family said
 * no when nobody said anything at all.
 */
public enum ClaimStatus {

    PENDING,
    APPROVED,
    REJECTED,
    /** Withdrawn by the requester. */
    CANCELLED;

    public boolean isPending() {
        return this == PENDING;
    }

    /** Whether a claim in this state may still be decided — true for exactly one of them. */
    public boolean isDecidable() {
        return this == PENDING;
    }
}
