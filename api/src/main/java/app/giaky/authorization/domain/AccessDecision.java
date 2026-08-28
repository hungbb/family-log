package app.giaky.authorization.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The answer to one authorization question, and <i>why</i>.
 *
 * <p>The why is not decoration. FR-Z8 wants every mutation's audit entry to carry the grant that
 * authorized it, and doc 04 §7 wants a refusal that names what the caller does hold instead of a
 * bare 403 — both of which need the decision to survive the call that produced it.
 *
 * @param authorizingGrantId the grant that allowed this, for the audit trail; null on a denial
 * @param reason why it was refused; null on an allow
 * @param heldScopeSummary what the caller does hold for this permission, in a form safe to show
 *     them; null when they hold nothing at all
 */
public record AccessDecision(
        boolean allowed,
        Permission permission,
        Target target,
        UUID authorizingGrantId,
        DenialReason reason,
        String heldScopeSummary) {

    /** Why a request was refused. Each maps to one of the error codes in doc 04 §7. */
    public enum DenialReason {

        /** No active membership in the clan. Answered as a 404, never a 403 (doc 04 §7). */
        NOT_A_MEMBER,

        /** A member, but holding nothing that confers the permission anywhere in the clan. */
        NO_SUCH_PERMISSION,

        /** A member holding the permission, but somewhere else in the tree. */
        OUT_OF_SCOPE,

        /**
         * The target could not be classified — an unplaced person under a branch grant, a subtree
         * grant before the closure table exists. Deny by default (doc 04 §1.5).
         */
        UNRESOLVABLE_TARGET
    }

    public AccessDecision {
        Objects.requireNonNull(permission, "permission must not be null");
        Objects.requireNonNull(target, "target must not be null");
        if (allowed == (reason != null)) {
            throw new IllegalArgumentException("an allow has no reason, and a denial must have one");
        }
        if (allowed && authorizingGrantId == null) {
            throw new IllegalArgumentException("an allow must name the grant that authorized it");
        }
    }

    static AccessDecision allow(Permission permission, Target target, UUID grantId) {
        return new AccessDecision(true, permission, target, grantId, null, null);
    }

    static AccessDecision deny(
            Permission permission, Target target, DenialReason reason, String heldScopeSummary) {
        return new AccessDecision(false, permission, target, null, reason, heldScopeSummary);
    }

    public boolean denied() {
        return !allowed;
    }

    /** The authorizing grant, for the audit writer. */
    public Optional<UUID> grantId() {
        return Optional.ofNullable(authorizingGrantId);
    }
}
