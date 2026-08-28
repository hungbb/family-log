package app.giaky.authorization.domain;

/**
 * <i>Where</i> a grant applies (doc 04 §3).
 *
 * <p>Three kinds, and the difference between them is what {@code scope_ref_id} points at: nothing, a
 * branch, or a person. That is I-G1, and {@code ck_grant_scope_ref} in V6 holds it in the database.
 */
public enum ScopeType {

    /** The whole clan. {@code scope_ref_id} is null. */
    CLAN(false),

    /** A branch and every branch below it, matched by materialized-path prefix. */
    BRANCH(true),

    /**
     * A person and their descendants, matched through {@code person_ancestry}.
     *
     * <p>The resolver understands this scope; nothing can be granted at it until Phase 4 creates the
     * closure table. See {@code LineageAncestryPort} and phase 3 §1.
     */
    SUBTREE(true);

    private final boolean requiresReference;

    ScopeType(boolean requiresReference) {
        this.requiresReference = requiresReference;
    }

    /** Whether this scope names something — a branch or a person — rather than the clan itself. */
    public boolean requiresReference() {
        return requiresReference;
    }
}
