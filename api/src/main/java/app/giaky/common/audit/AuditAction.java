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
    PROFILE_UPDATED
}
