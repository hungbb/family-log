package app.giaky.authorization.domain;

import app.giaky.common.types.BranchId;
import java.util.Objects;
import java.util.UUID;

/**
 * <i>Where</i> one grant applies: a scope type and, for the two that need one, the thing it names
 * (doc 04 §3).
 *
 * <p>I-G1 lives in the compact constructor — {@code CLAN} has no reference and the other two must
 * have one — so a scope that could not exist in the database cannot be built in memory either. That
 * matters more than it sounds: the resolver's coverage rules branch on the scope type and read the
 * reference without re-checking it, and a {@code BRANCH} scope pointing at nothing would silently
 * cover nothing rather than loudly refuse to exist.
 *
 * @param refId the branch or person this scope names; null for, and only for, {@link ScopeType#CLAN}
 */
public record PermissionScope(ScopeType type, UUID refId) {

    public PermissionScope {
        Objects.requireNonNull(type, "scope type must not be null");
        if (type.requiresReference() == (refId == null)) {
            throw new IllegalArgumentException(
                    "I-G1: a " + type + " scope " + (refId == null ? "needs" : "must not carry")
                            + " a scope reference");
        }
    }

    /** The whole clan. */
    public static PermissionScope clan() {
        return new PermissionScope(ScopeType.CLAN, null);
    }

    /** A branch and everything below it. */
    public static PermissionScope branch(BranchId branchId) {
        Objects.requireNonNull(branchId, "branchId must not be null");
        return new PermissionScope(ScopeType.BRANCH, branchId.value());
    }

    /** A person and their descendants. Not grantable until Phase 4 — see {@link ScopeType#SUBTREE}. */
    public static PermissionScope subtree(UUID personId) {
        Objects.requireNonNull(personId, "personId must not be null");
        return new PermissionScope(ScopeType.SUBTREE, personId);
    }

    /** Builds a scope from the wire form, applying I-G1 as a validation rather than a crash. */
    public static PermissionScope of(ScopeType type, UUID refId) {
        return new PermissionScope(type, refId);
    }

    public boolean isClanWide() {
        return type == ScopeType.CLAN;
    }

    /** The scope's reference as a {@link BranchId}. Only meaningful for {@link ScopeType#BRANCH}. */
    public BranchId branchId() {
        if (type != ScopeType.BRANCH) {
            throw new IllegalStateException("not a branch scope: " + type);
        }
        return BranchId.of(refId);
    }

    @Override
    public String toString() {
        return refId == null ? type.name() : type.name() + "(" + refId + ")";
    }
}
