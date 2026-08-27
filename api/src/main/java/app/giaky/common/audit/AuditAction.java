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
    JOIN_REQUEST_EXPIRED
}
