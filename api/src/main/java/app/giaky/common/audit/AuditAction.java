package app.giaky.common.audit;

/**
 * What happened, as a closed vocabulary.
 *
 * <p>Stored as the constant's own name, so these values are as much a contract as {@code ErrorCode}
 * is: an operator filters the audit view by them and a rename orphans every historical row. Add
 * freely, rename never.
 */
public enum AuditAction {

    // --- Identity (Phase 1) --------------------------------------------------------------------

    USER_REGISTERED,
    USER_LOGIN,
    /**
     * A rejected credential. Recorded whether or not the address belongs to an account — the
     * pattern across many failures is the signal, and dropping the ones for unknown addresses would
     * hide exactly the enumeration attempt worth seeing.
     */
    LOGIN_FAILED,
    USER_LOGGED_OUT,
    ALL_SESSIONS_REVOKED,
    EMAIL_VERIFIED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET,
    /**
     * An already-rotated refresh token was presented again, and its whole chain was revoked. Either
     * a stolen token or a client bug; both are worth a look.
     */
    REFRESH_TOKEN_REUSE_DETECTED,
    PROFILE_UPDATED,

    // --- ClanOrg (Phase 2) ---------------------------------------------------------------------

    CLAN_CREATED,
    CLAN_UPDATED,
    /** Soft delete. The row and its branches stay; doc 05 §4.3 gives 30 days to change your mind. */
    CLAN_DELETED,

    BRANCH_CREATED,
    BRANCH_UPDATED,
    /**
     * A branch was re-parented, taking its whole subtree with it. The most consequential structural
     * edit the product has (phase 2 doc §8), and the one an operator will come looking for.
     */
    BRANCH_MOVED,
    BRANCH_ARCHIVED,

    /** A membership became {@code ACTIVE} — today only ever through an approved join request. */
    MEMBERSHIP_GRANTED,
    /** A member was removed by someone else. */
    MEMBERSHIP_REVOKED,
    /** A member removed themselves. Separate from the above because who acted is the whole point. */
    MEMBERSHIP_LEFT,

    JOIN_REQUEST_SUBMITTED,
    JOIN_REQUEST_APPROVED,
    JOIN_REQUEST_REJECTED,
    JOIN_REQUEST_CANCELLED,
    /** Closed by the sweep in {@code JoinRequestService}, with no human actor. */
    JOIN_REQUEST_EXPIRED,

    // --- Authorization (Phase 3) ---------------------------------------------------------------

    /**
     * Somebody was given a permission (FR-Z8).
     *
     * <p>The most consequential entry in the log, and the one an operator reads first when a family
     * argues about who changed what. The entry's {@code authorizingGrantId} names the grant that
     * allowed the granter to do it, so the chain back to the founder's bootstrap is walkable.
     */
    PERMISSION_GRANTED,
    /** A permission was taken back, by a person. */
    PERMISSION_REVOKED,
    /**
     * A permission lapsed and the nightly sweep marked it revoked. No actor: the calendar did it,
     * and reads had already stopped honouring the grant at its expiry.
     */
    PERMISSION_EXPIRED,
    /**
     * The founder's {@code CLAN_ADMIN} at clan creation (doc 04 §6).
     *
     * <p>Its own action rather than a {@code PERMISSION_GRANTED} with a null actor, because "the
     * system issued this" and "somebody issued this" are different facts and the audit view filters
     * on exactly this distinction.
     */
    PERMISSION_BOOTSTRAPPED,

    // --- Lineage (Phase 4) ---------------------------------------------------------------------

    PERSON_CREATED,
    PERSON_UPDATED,
    /** Soft delete. The node, its edges and its closure rows all stay (FR-P10). */
    PERSON_DELETED,
    PERSON_RESTORED,
    /**
     * A node changed branch. Its own action because placement decides which grants reach the node
     * (doc 04 §3) — a move is an access change as much as an organizational one.
     */
    PERSON_MOVED_TO_BRANCH,

    /**
     * A parent-child edge was created.
     *
     * <p>The most consequential lineage entry there is: edges are what the ancestry closure, the
     * generation numbers and every {@code SUBTREE} grant are derived from. The before/after JSON
     * carries the whole edge, so a mis-linked branch can be reconstructed rather than guessed at.
     */
    PARENTAGE_ADDED,
    PARENTAGE_REMOVED,
    /**
     * An edge stopped naming a marriage because the marriage was deleted while the child was
     * soft-deleted. The parent link itself stays, so restoring the child still restores it.
     */
    PARENTAGE_DETACHED_FROM_UNION,

    UNION_CREATED,
    UNION_UPDATED,
    UNION_REMOVED,

    // --- Linking (Phase 6) ---------------------------------------------------------------------

    /** A member asked to be recognised as a node (FR-N1). */
    CLAIM_SUBMITTED,
    /**
     * A claim was answered.
     *
     * <p>One action for approve, reject and withdraw, with the outcome in the after-JSON. An
     * operator reading the log wants "what happened to this claim" as one filter, and three actions
     * would make the common question a three-way OR.
     */
    CLAIM_DECIDED,

    /**
     * An account was attached to a person node (FR-N3, FR-N4).
     *
     * <p>Its own action rather than a {@code PERSON_UPDATED}, because it is the one field on the row
     * that says who somebody <i>is</i> rather than what the book records about them — and because
     * "who was linked to this node, and who allowed it" is a question a family will ask.
     */
    PERSON_LINKED,
    /** A link was removed, by an approver or by the linked person themselves (FR-N5). */
    PERSON_UNLINKED,

    /** A linked member changed which profile fields propagate onto their node (FR-N6). */
    PROFILE_PROPAGATION_UPDATED
}
