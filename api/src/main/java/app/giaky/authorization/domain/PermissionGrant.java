package app.giaky.authorization.domain;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One person may do one thing in one place, until somebody takes it back (doc 03 §3.11).
 *
 * <p>The whole authorization model is a bag of these. There is no negative rule and no precedence
 * order (doc 04 §1.3): effective access is the union of a user's grants, which is what makes
 * resolution total, order-independent, and explainable in the UI — "you can edit this because you
 * hold TREE_EDIT on Chi Hai" is a sentence the resolver can produce because a single row authorized
 * the decision.
 *
 * <p>Revocation is a timestamp rather than a delete. Who could do what last March is a question
 * families ask, and a deleted row cannot answer it.
 *
 * @param expiresAt when the grant lapses on its own; null for one that does not
 * @param revokedAt when somebody took it back; null while it stands
 */
public record PermissionGrant(
        UUID id,
        ClanId clanId,
        UserId granteeUserId,
        Permission permission,
        PermissionScope scope,
        UserId grantedBy,
        Instant grantedAt,
        Instant expiresAt,
        Instant revokedAt,
        UserId revokedBy) {

    public PermissionGrant {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(clanId, "clanId must not be null");
        Objects.requireNonNull(granteeUserId, "granteeUserId must not be null");
        Objects.requireNonNull(permission, "permission must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(grantedAt, "grantedAt must not be null");
        // I-G2, and ck_grant_admin_is_clan_wide in V6. Checked here as well as there because the
        // resolver treats CLAN_ADMIN as implying everything at the grant's own scope, and an admin
        // grant scoped to a branch would quietly mean "admin of the clan, from inside one chi".
        if (!permission.isValidAt(scope.type())) {
            throw new IllegalArgumentException(
                    permission + " cannot be granted at " + scope.type() + " scope");
        }
        // One direction only. A live grant cannot name a revoker; a revoked one may leave the
        // revoker null, which is how the nightly expiry sweep records that the calendar did it
        // rather than a person (doc 04 §6).
        if (revokedAt == null && revokedBy != null) {
            throw new IllegalArgumentException("a live grant cannot name who revoked it");
        }
        if (expiresAt != null && !expiresAt.isAfter(grantedAt)) {
            throw new IllegalArgumentException("expiresAt must be after grantedAt");
        }
    }

    /** A new, live grant. */
    public static PermissionGrant issue(
            UUID id,
            ClanId clanId,
            UserId grantee,
            Permission permission,
            PermissionScope scope,
            UserId grantedBy,
            Instant expiresAt,
            Instant now) {

        return new PermissionGrant(
                id, clanId, grantee, permission, scope, grantedBy, now, expiresAt, null, null);
    }

    /**
     * Whether this grant confers anything at {@code now}.
     *
     * <p>Expiry is evaluated rather than stored: the nightly sweep (B3.14) marks lapsed grants
     * revoked for hygiene, but a deployment whose sweep has not run must not hand out an hour of
     * extra access, so every read filters on the clock as well.
     */
    public boolean isEffectiveAt(Instant now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean hasExpiredBy(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    /** Whether holding this grant confers {@code requested} — the implication rule of doc 04 §2. */
    public boolean confers(Permission requested) {
        return permission.implies(requested);
    }

    /**
     * Takes the grant back. Idempotent: a second revocation keeps the first one's time and actor.
     *
     * @param actor who revoked it; null for the expiry sweep, which has no human behind it
     */
    public PermissionGrant revoke(UserId actor, Instant now) {
        if (isRevoked()) {
            return this;
        }
        return new PermissionGrant(
                id, clanId, granteeUserId, permission, scope, grantedBy, grantedAt, expiresAt, now,
                actor);
    }
}
