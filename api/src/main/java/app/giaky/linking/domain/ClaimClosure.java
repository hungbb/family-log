package app.giaky.linking.domain;

/**
 * Why the system, rather than an approver, declined a claim.
 *
 * <p>A link answers the claims it makes impossible ({@code ClaimService#closeClaimsSettledBy}).
 * Those claims are declined with one of these codes and no free-text reason, so the client can word
 * the explanation in the reader's language. A reason an approver types stays free text: it was
 * written by a relative, in the language they chose.
 */
public enum ClaimClosure {
    /** Somebody else has been linked to the node this claim asked for (I-K1). */
    NODE_TAKEN,
    /** The requester has been linked to a different node in the clan (I-P4). */
    REQUESTER_PLACED
}
