package app.giaky.authorization.domain;

import app.giaky.authorization.domain.Target.BranchTarget;
import app.giaky.authorization.domain.Target.ClanTarget;
import app.giaky.authorization.domain.Target.PersonTarget;
import app.giaky.authorization.domain.Target.RelationshipTarget;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * "Does this scope contain that?" — asked two ways, answered in one place (doc 04 §5.4, B3.5).
 *
 * <p>Authorization needs the containment question twice over. Resolving a request asks whether a
 * grant's scope covers the <i>target</i> being acted on; refusing an escalation asks whether the
 * actor's scope covers the <i>scope</i> they are trying to hand out. Two implementations of "is X
 * inside Y" would eventually disagree, and the direction they would disagree in is the one that
 * lets somebody grant themselves out of their own branch. So both live here, over the same three
 * primitives: path prefixes, the ancestry closure, and branch root people.
 *
 * <p>Every method fails closed. An unknown branch, an unplaced person, an ancestry table that does
 * not exist yet — all answer false, because the alternative to "I cannot tell" is not "probably
 * fine" (doc 04 §1.5).
 */
public final class ScopeCoverage {

    private final BranchHierarchyPort branches;
    private final LineageAncestryPort ancestry;

    public ScopeCoverage(BranchHierarchyPort branches, LineageAncestryPort ancestry) {
        this.branches = Objects.requireNonNull(branches, "branches must not be null");
        this.ancestry = Objects.requireNonNull(ancestry, "ancestry must not be null");
    }

    /**
     * Whether a grant held at {@code scope} reaches {@code target} — step 5 of doc 04 §4.
     *
     * @param clan the clan the request addresses; a target from anywhere else was already refused
     *     by the tenancy check, and is refused again here
     */
    public boolean covers(ClanId clan, PermissionScope scope, Target target) {
        if (scope == null || target == null || !clan.equals(target.clanId())) {
            return false;
        }
        return switch (scope.type()) {
            case CLAN -> true;
            case BRANCH -> branchScopeCovers(clan, BranchId.of(scope.refId()), target);
            case SUBTREE -> subtreeScopeCovers(clan, scope.refId(), target);
        };
    }

    /**
     * The scope-covering relation of doc 04 §5.4: may somebody holding {@code outer} act over all of
     * {@code inner}?
     *
     * <p>Used by {@code GrantService} for both escalation conditions — the actor's {@code
     * ROLE_MANAGE} must cover the scope being granted, and so must their own hold on the permission
     * itself. Reflexive: a scope covers itself, which is what makes "grant what you already have,
     * where you already have it" the allowed case.
     */
    public boolean covers(ClanId clan, PermissionScope outer, PermissionScope inner) {
        if (outer == null || inner == null) {
            return false;
        }
        return switch (outer.type()) {
            case CLAN -> true;
            case BRANCH -> branchScopeCoversScope(clan, BranchId.of(outer.refId()), inner);
            case SUBTREE -> subtreeScopeCoversScope(clan, outer.refId(), inner);
        };
    }

    // --- branch scope --------------------------------------------------------------------------

    private boolean branchScopeCovers(ClanId clan, BranchId scopeBranch, Target target) {
        return switch (target) {
            // The clan as a whole is above every branch. A chi's editor does not administer the
            // family, however large their chi is.
            case ClanTarget ignored -> false;
            case BranchTarget branch -> isWithin(clan, branch.branchId(), scopeBranch);
            case PersonTarget person ->
                    person.branchId() != null && isWithin(clan, person.branchId(), scopeBranch);
            // Doc 04 §5.2: all of them, or none. This is the rule that stops a branch editor
            // reaching outside their scope by linking to a node in someone else's.
            case RelationshipTarget relationship ->
                    relationship.endpoints().stream()
                            .allMatch(endpoint -> branchScopeCovers(clan, scopeBranch, endpoint));
        };
    }

    private boolean branchScopeCoversScope(ClanId clan, BranchId scopeBranch, PermissionScope inner) {
        return switch (inner.type()) {
            case CLAN -> false;
            case BRANCH -> isWithin(clan, BranchId.of(inner.refId()), scopeBranch);
            // "…covers SUBTREE(p) if p is placed in b₁ or a descendant branch." Empty placement
            // means the person is not in the branch, which is a no rather than a maybe — and until
            // Phase 4 there is nowhere to ask, so the answer is no for everybody.
            case SUBTREE ->
                    ancestry.isAvailable()
                            && ancestry.placementOf(clan, inner.refId())
                                    .map(placement -> isWithin(clan, placement, scopeBranch))
                                    .orElse(false);
        };
    }

    /** Path-prefix containment, reflexive: a branch is inside itself. */
    private boolean isWithin(ClanId clan, BranchId candidate, BranchId ancestorBranch) {
        Optional<String> candidatePath = branches.pathOf(clan, candidate);
        Optional<String> ancestorPath = branches.pathOf(clan, ancestorBranch);
        if (candidatePath.isEmpty() || ancestorPath.isEmpty()) {
            // One of them is not a branch of this clan. Nothing to compare, so nothing is covered.
            return false;
        }
        // The trailing slash on every path is what keeps /a1b2c3d4/ from prefixing /a1b2c3d40.../;
        // see MaterializedPath.
        return candidatePath.get().startsWith(ancestorPath.get());
    }

    // --- subtree scope -------------------------------------------------------------------------

    private boolean subtreeScopeCovers(ClanId clan, UUID scopePerson, Target target) {
        if (!ancestry.isAvailable()) {
            // Phase 4 turns this on. Until then a subtree grant cannot be created, and one that
            // somehow existed would confer nothing rather than everything.
            return false;
        }
        return switch (target) {
            case ClanTarget ignored -> false;
            // "For a BranchTarget under a SUBTREE grant: covers only if the branch's root person is
            // in the grant's descendant set."
            case BranchTarget branch ->
                    branches.rootPersonOf(clan, branch.branchId())
                            .map(rootPerson -> ancestry.covers(clan, scopePerson, rootPerson))
                            .orElse(false);
            case PersonTarget person -> ancestry.covers(clan, scopePerson, person.personId());
            case RelationshipTarget relationship ->
                    relationship.endpoints().stream()
                            .allMatch(endpoint -> subtreeScopeCovers(clan, scopePerson, endpoint));
        };
    }

    private boolean subtreeScopeCoversScope(ClanId clan, UUID scopePerson, PermissionScope inner) {
        if (!ancestry.isAvailable()) {
            return false;
        }
        return switch (inner.type()) {
            case CLAN -> false;
            case BRANCH ->
                    branches.rootPersonOf(clan, BranchId.of(inner.refId()))
                            .map(rootPerson -> ancestry.covers(clan, scopePerson, rootPerson))
                            .orElse(false);
            case SUBTREE -> ancestry.covers(clan, scopePerson, inner.refId());
        };
    }
}
