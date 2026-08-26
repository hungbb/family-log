package app.giaky.identity.domain;

/**
 * The account lifecycle of doc 02 §2.1.
 *
 * <p>{@link #canAuthenticate()} is the whole of invariant I-U2, in one place. Scattering that
 * judgement across the login path, the token filter and the grant resolver is how an account ends
 * up suspended everywhere except the one code path nobody remembered.
 */
public enum UserStatus {

    /** Verified, or at least past verification. Full access. */
    ACTIVE,

    /**
     * Registered, email not confirmed yet.
     *
     * <p>May sign in. Phase 1 decision (phase doc §8): blocking login until verification strands
     * anyone whose message goes astray, so the restriction goes where it actually protects
     * something — clan creation, from Phase 2 — and the UI shows a banner in the meantime.
     */
    PENDING_VERIFICATION,

    /** Barred by an operator. Cannot authenticate, and holds no effective grants (I-U2). */
    SUSPENDED,

    /** Closed, by the account holder or by an operator. Same effect as suspension. */
    DEACTIVATED;

    /** I-U2: whether an account in this state may hold a session. */
    public boolean canAuthenticate() {
        return this == ACTIVE || this == PENDING_VERIFICATION;
    }
}
