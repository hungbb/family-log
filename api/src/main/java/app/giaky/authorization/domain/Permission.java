package app.giaky.authorization.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The permission catalogue of doc 04 §2 — the six things a person can be given in a clan.
 *
 * <p>Permissions, not roles (doc 04 §1.1). There is no patriarch flag and no {@code role} column
 * anywhere in the schema: every capability is an explicit, scoped grant, and any number of people
 * may hold it. A named bundle like "branch editor" is a Phase 8 convenience over these, never a
 * replacement for them.
 *
 * <p>The constant names are the API contract — they appear in {@code myPermissions[]}, in grant
 * requests and in the audit trail. Renaming one is a breaking change; adding one is not.
 */
public enum Permission {

    /** Read the clan tree, person details, the member list. Implied by every other permission. */
    CLAN_VIEW(Set.of(ScopeType.CLAN, ScopeType.BRANCH, ScopeType.SUBTREE)),

    /** Create, update and soft-delete persons, parentages and unions. */
    TREE_EDIT(Set.of(ScopeType.CLAN, ScopeType.BRANCH, ScopeType.SUBTREE)),

    /**
     * Decide join requests; remove members.
     *
     * <p>No subtree scope: membership is about accounts and branches, and a person node is not a
     * place you can be admitted into.
     */
    MEMBER_APPROVE(Set.of(ScopeType.CLAN, ScopeType.BRANCH)),

    /** Decide node claims; assign a member to a node directly. */
    NODE_CLAIM_APPROVE(Set.of(ScopeType.CLAN, ScopeType.BRANCH, ScopeType.SUBTREE)),

    /** Grant and revoke permissions to others, within scope. */
    ROLE_MANAGE(Set.of(ScopeType.CLAN, ScopeType.BRANCH, ScopeType.SUBTREE)),

    /**
     * Clan settings, branch structure, hard delete — and, by implication, everything above.
     *
     * <p>Clan scope only (I-G2). An administrator of half a clan is a contradiction: the settings
     * and the branch tree they would administer belong to the clan as a whole.
     */
    CLAN_ADMIN(Set.of(ScopeType.CLAN));

    private final Set<ScopeType> validScopes;

    Permission(Set<ScopeType> validScopes) {
        this.validScopes = validScopes;
    }

    /** The scopes this permission may be granted at (doc 04 §2). */
    public Set<ScopeType> validScopes() {
        return validScopes;
    }

    public boolean isValidAt(ScopeType scopeType) {
        return validScopes.contains(scopeType);
    }

    /**
     * Whether holding {@code this} at some scope also confers {@code requested} at that same scope
     * (doc 04 §2, FR-Z6).
     *
     * <p>Two rules and no more:
     *
     * <ul>
     *   <li>{@code CLAN_ADMIN} implies everything — but only from clan scope, which is the only
     *       scope it can be held at anyway, so the caller does not have to re-check.
     *   <li>Every permission implies {@code CLAN_VIEW}. You can always see what you can act on.
     * </ul>
     *
     * <p>Expanded during resolution and never stored: a grant table holding the closure would drift
     * from this method the first time somebody revoked one row of it.
     */
    public boolean implies(Permission requested) {
        return this == requested || this == CLAN_ADMIN || requested == CLAN_VIEW;
    }

    /**
     * The permission named by {@code raw}, or empty.
     *
     * <p>Empty rather than an exception because the input is a query parameter or a request body
     * field: an unknown name is a 422 the caller writes, not a crash.
     */
    public static Optional<Permission> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        for (Permission permission : values()) {
            if (permission.name().equals(raw.trim().toUpperCase(Locale.ROOT))) {
                return Optional.of(permission);
            }
        }
        return Optional.empty();
    }
}
