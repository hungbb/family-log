package app.giaky.authorization.domain;

import app.giaky.authorization.domain.AccessDecision.DenialReason;
import app.giaky.authorization.domain.Target.PersonTarget;
import app.giaky.authorization.domain.Target.RelationshipTarget;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.error.PermissionDeniedException;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * <b>The single decision point.</b> Every authorization question in the product is answered here
 * (doc 04 §1.4, doc 06 §4.1).
 *
 * <p>A class rather than an interface, deliberately. Doc 04's first principle is that there is one
 * policy; an interface invites a second implementation, and a second implementation is how a product
 * ends up with two answers to "may they edit this" that agree in testing and differ in production.
 * The ports are where the seams belong — they are what make this unit-testable with no Spring and no
 * database, which is the property doc 04 §8 asks for.
 *
 * <p><b>The algorithm</b> (doc 04 §4), in order, and every step matters:
 *
 * <ol>
 *   <li><b>Tenancy.</b> A target in another clan is refused before anything is loaded (NFR-4).
 *   <li><b>Membership.</b> No active membership, no decision — and the caller is told the clan does
 *       not exist rather than that they lack a permission (doc 04 §7).
 *   <li><b>Grants.</b> One cached query for everything this user holds in this clan.
 *   <li><b>Implications.</b> {@code CLAN_ADMIN} confers everything; everything confers {@code
 *       CLAN_VIEW} (FR-Z6). Expanded here, never stored.
 *   <li><b>Match.</b> The first grant that both confers the permission and covers the target wins,
 *       and its id goes on the audit entry (FR-Z8). A target that names several people is covered
 *       when each of them is, by whichever grant — the union, not one grant spanning them all.
 *   <li><b>Deny.</b> No match is a denial. There is no fallthrough (doc 04 §1.5).
 * </ol>
 *
 * <p>Additive and order-independent: there are no negative grants, so the union of what somebody
 * holds is the whole answer, and which of several matching grants is reported affects only the
 * explanation in the audit trail.
 *
 * <p><b>On throwing from the domain.</b> {@link #require} raises the API's own exceptions, which is
 * the one dependency this package takes beyond {@code common.types}. Doc 06 §4.1 specifies it that
 * way, and the reason is worth keeping: the mapping from "no membership" to a 404 and from "wrong
 * branch" to a 403 that names the branch the caller <i>does</i> hold is part of the policy, not of
 * the transport. A service left to translate a boolean would eventually translate it differently.
 */
public final class PermissionResolver {

    private final MembershipPort memberships;
    private final GrantPort grants;
    private final BranchHierarchyPort branches;
    private final LineageAncestryPort ancestry;
    private final ScopeCoverage coverage;
    private final Clock clock;

    public PermissionResolver(
            MembershipPort memberships,
            GrantPort grants,
            BranchHierarchyPort branches,
            LineageAncestryPort ancestry,
            Clock clock) {

        this.memberships = Objects.requireNonNull(memberships, "memberships must not be null");
        this.grants = Objects.requireNonNull(grants, "grants must not be null");
        this.branches = Objects.requireNonNull(branches, "branches must not be null");
        this.ancestry = Objects.requireNonNull(ancestry, "ancestry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.coverage = new ScopeCoverage(branches, ancestry);
    }

    /**
     * The grants that confer something on this user right now.
     *
     * <p>{@code GrantPort} already filters revoked and expired rows at the source, and this filters
     * them again. That duplication is deliberate and is the one place in the codebase where it is
     * the right call: phase 3 §8 says <i>prefer duplicated checks over clever abstractions here</i>,
     * because a bug in the port's {@code WHERE} clause would otherwise hand out access somebody has
     * already taken back, and nothing downstream would notice. Two independent filters have to fail
     * together for that to happen.
     */
    private List<PermissionGrant> effectiveGrants(ClanId clan, UserId user) {
        Instant now = clock.instant();
        return grants.effectiveGrants(clan, user).stream()
                .filter(grant -> grant.isEffectiveAt(now))
                .toList();
    }

    /**
     * Answers the question without acting on the answer.
     *
     * <p>For the places where a denial is information rather than an error: filtering an approver's
     * inbox to the requests they may decide, stamping {@code canEdit} on a node, building the list
     * of scopes a grant dialog is allowed to offer.
     */
    public AccessDecision decide(UserId user, Permission permission, Target target) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(permission, "permission must not be null");
        // Doc 04 §1.5 — a target the resolver cannot classify is a denial. A null one is not a
        // target at all, and answering "denied" would hide the bug that produced it.
        Objects.requireNonNull(target, "target must not be null");

        ClanId clan = target.clanId();
        if (!memberships.isActiveMember(clan, user)) {
            return AccessDecision.deny(permission, target, DenialReason.NOT_A_MEMBER, null);
        }

        List<PermissionGrant> held = effectiveGrants(clan, user);
        List<PermissionGrant> conferring =
                held.stream().filter(grant -> grant.confers(permission)).toList();
        for (PermissionGrant grant : conferring) {
            if (coverage.covers(clan, grant.scope(), target)) {
                return AccessDecision.allow(permission, target, grant.id());
            }
        }
        if (target instanceof RelationshipTarget relationship) {
            UUID authorizing = grantCoveringEachEndpoint(clan, conferring, relationship);
            if (authorizing != null) {
                return AccessDecision.allow(permission, target, authorizing);
            }
        }

        // Telling "you do not have this at all" apart from "you have it somewhere else" is what
        // lets the API answer OUT_OF_SCOPE naming the branch the caller does hold (doc 04 §7) —
        // the difference between a support conversation and a refusal that explains itself.
        DenialReason reason =
                conferring.isEmpty() ? DenialReason.NO_SUCH_PERMISSION : DenialReason.OUT_OF_SCOPE;
        return AccessDecision.deny(permission, target, reason, summarize(held, permission));
    }

    /**
     * The union of doc 04 §1.3, applied to a target that names several people (§5.2).
     *
     * <p>§5.2 asks for the permission on <i>each</i> endpoint, and access is additive: somebody who
     * edits Chi Hai through one grant and Chi Ba through another may edit a person in either, so
     * they may record the marriage between the two. Asking a single grant to span the whole edge —
     * which is all the loop above can see — refused exactly that, with a message naming both
     * branches as held and the edge as uncovered, on a pair of cards the tree had marked editable.
     *
     * <p>Nothing is widened. Every endpoint still has to be inside a scope the user holds this
     * permission on; an edge reaching into somebody else's branch has an endpoint no grant covers
     * and is refused here as it was before, which is the escape hatch §5.2 exists to close.
     *
     * @return the grant covering the first endpoint — one id is what the audit entry carries
     *     (FR-Z8), and which of several is reported changes the explanation, not the decision — or
     *     null when some endpoint is covered by none
     */
    private UUID grantCoveringEachEndpoint(
            ClanId clan, List<PermissionGrant> conferring, RelationshipTarget relationship) {

        UUID first = null;
        for (PersonTarget endpoint : relationship.endpoints()) {
            UUID covering = null;
            for (PermissionGrant grant : conferring) {
                if (coverage.covers(clan, grant.scope(), endpoint)) {
                    covering = grant.id();
                    break;
                }
            }
            if (covering == null) {
                return null;
            }
            if (first == null) {
                first = covering;
            }
        }
        return first;
    }

    /**
     * The authoritative check. Every mutating application-service method calls this, and {@code
     * architecture.AuthorizationCoverageTest} fails the build for one that does not (B3.15).
     *
     * @return the decision, so the caller can put {@code authorizingGrantId} on its audit entry
     *     (FR-Z8)
     * @throws NotFoundException the caller is not a member; a private clan's existence is not
     *     disclosed
     * @throws PermissionDeniedException the caller is a member, without the permission here
     */
    public AccessDecision require(UserId user, Permission permission, Target target) {
        AccessDecision decision = decide(user, permission, target);
        if (decision.allowed()) {
            return decision;
        }
        throw switch (decision.reason()) {
            case NOT_A_MEMBER ->
                    new NotFoundException(
                            ErrorCode.CLAN_NOT_FOUND,
                            "That clan does not exist, or you are not a member of it.");
            case OUT_OF_SCOPE ->
                    new PermissionDeniedException(
                            ErrorCode.OUT_OF_SCOPE,
                            "You hold "
                                    + permission
                                    + " on "
                                    + decision.heldScopeSummary()
                                    + ", which does not cover "
                                    + target.describe()
                                    + ".");
            case NO_SUCH_PERMISSION, UNRESOLVABLE_TARGET ->
                    new PermissionDeniedException(
                            ErrorCode.PERMISSION_DENIED,
                            "You do not hold " + permission + " over " + target.describe() + ".");
        };
    }

    /** {@link #decide} reduced to a boolean, for read paths that need only a yes or a no. */
    public boolean can(UserId user, Permission permission, Target target) {
        return decide(user, permission, target).allowed();
    }

    /**
     * Whether the user is in the clan at all — step 2, on its own.
     *
     * <p>Exposed because doc 04 §7's 404-versus-403 split is a question about membership rather than
     * about any particular permission, and a caller that had to infer it from a denial would infer
     * it differently each time.
     */
    public boolean isMember(UserId user, ClanId clan) {
        return memberships.isActiveMember(clan, user);
    }

    /**
     * The read gate of doc 04 §5.5: membership, and nothing finer.
     *
     * <p>Reads in v1 are clan-wide. A <i>gia phả</i> is meaningless in fragments (FR-T2), so a member
     * who can open the clan sees all of it; branch and subtree scopes constrain <b>writes</b>. That
     * is a product decision, not a gap — and the reason this is a method on the resolver rather than
     * a membership check scattered through six services is that when a future requirement asks for
     * read partitioning, there is one place to change and one place to test.
     *
     * <p>Deliberately not expressed as {@code require(user, CLAN_VIEW, clanTarget)}: that would
     * demand a <i>clan-scoped</i> grant, and would lock every branch-scoped member out of the tree
     * they were admitted to read.
     *
     * @throws NotFoundException the caller is not a member; a private clan's existence is not
     *     disclosed (doc 04 §7)
     */
    public void requireMembership(UserId user, ClanId clan) {
        if (!memberships.isActiveMember(clan, user)) {
            throw new NotFoundException(
                    ErrorCode.CLAN_NOT_FOUND,
                    "That clan does not exist, or you are not a member of it.");
        }
    }

    /**
     * Whether one scope contains another (doc 04 §5.4).
     *
     * <p>The escalation half of {@link ScopeCoverage}, exposed here so that grant management asks
     * the same object every other check asks. Two containment implementations would eventually
     * disagree, and the direction they would disagree in is the one that lets somebody grant
     * themselves out of their own branch.
     */
    public boolean scopeCovers(ClanId clan, PermissionScope outer, PermissionScope inner) {
        return coverage.covers(clan, outer, inner);
    }

    /**
     * Every permission the user effectively holds <i>somewhere</i> in the clan — {@code
     * myPermissions[]} and the menus that gate on it (doc 05 §4.3).
     *
     * <p>Deliberately scope-blind, because that is the question a menu asks: could this person ever
     * do X here? Whether they may do it to a <i>particular</i> node is {@link #decide}, and the
     * server asks that again on every request regardless of what the menu showed (doc 07 §8).
     */
    public Set<Permission> permissionsOf(UserId user, ClanId clan) {
        if (!memberships.isActiveMember(clan, user)) {
            return Set.of();
        }
        Set<Permission> effective = EnumSet.noneOf(Permission.class);
        for (PermissionGrant grant : effectiveGrants(clan, user)) {
            for (Permission permission : Permission.values()) {
                if (grant.confers(permission)) {
                    effective.add(permission);
                }
            }
        }
        return effective;
    }

    /**
     * The scopes at which the user effectively holds {@code permission} (doc 04 §5.4, condition 2).
     *
     * <p>Expanded, so a {@code CLAN_ADMIN} grant reports clan scope for every permission and a
     * {@code TREE_EDIT@Chi Hai} grant reports {@code CLAN_VIEW} there too. {@code GrantService}
     * needs the scopes rather than the bare permissions, because "may you grant this" is a question
     * about where, not only about what.
     */
    public Set<PermissionScope> scopesHolding(UserId user, ClanId clan, Permission permission) {
        if (!memberships.isActiveMember(clan, user)) {
            return Set.of();
        }
        Set<PermissionScope> scopes = new HashSet<>();
        for (PermissionGrant grant : effectiveGrants(clan, user)) {
            if (grant.confers(permission)) {
                scopes.add(grant.scope());
            }
        }
        return scopes;
    }

    /**
     * The batch answer of doc 04 §4.1: which nodes may this user edit in this clan?
     *
     * <p>Costs queries in proportion to the number of grants held — typically one or three — and not
     * to the size of the tree, which is what makes a per-node {@code canEdit} flag affordable.
     * Phase 4's tree read is the consumer; it is built here because the thing it has to agree with
     * is {@link #decide}, and the two are tested against each other.
     */
    public EditableSet editableSet(UserId user, ClanId clan) {
        if (!memberships.isActiveMember(clan, user)) {
            return EditableSet.none();
        }
        List<PermissionGrant> editing =
                effectiveGrants(clan, user).stream()
                        .filter(grant -> grant.confers(Permission.TREE_EDIT))
                        .toList();

        Set<BranchId> editableBranches = new HashSet<>();
        Set<UUID> editablePersons = new HashSet<>();
        List<BranchHierarchyPort.BranchPath> allPaths = null;

        // An if-chain rather than a switch, for one unglamorous reason: an exhaustive arrow switch
        // over an enum still compiles to a synthetic default branch, and this class is held to
        // 100 % branch coverage (phase 3 §7). A branch nobody can reach is a branch nobody can
        // test, and an exception for it here would be an exception that quietly grows.
        for (PermissionGrant grant : editing) {
            ScopeType scopeType = grant.scope().type();
            if (scopeType == ScopeType.CLAN) {
                // Nothing further can widen this, and nothing narrows it.
                return EditableSet.everything();
            }
            if (scopeType == ScopeType.BRANCH) {
                if (allPaths == null) {
                    // At most one load per call, however many branch grants are held.
                    allPaths = branches.allPaths(clan);
                }
                collectDescendantBranches(
                        allPaths,
                        branches.pathOf(clan, BranchId.of(grant.scope().refId())).orElse(null),
                        editableBranches);
            } else if (ancestry.isAvailable()) {
                editablePersons.addAll(ancestry.descendantsOf(clan, grant.scope().refId()));
            }
            // A subtree grant with no closure table contributes nothing, which is the same answer
            // decide() gives for it. The two must never diverge.
        }
        return new EditableSet(false, editableBranches, editablePersons);
    }

    // --- helpers -------------------------------------------------------------------------------

    /**
     * Every branch at or below {@code prefix}, by path comparison.
     *
     * @param prefix the scope branch's materialized path, or null when the grant names a branch
     *     this clan does not have — which covers nothing, the same answer {@code ScopeCoverage} gives
     */
    private static void collectDescendantBranches(
            List<BranchHierarchyPort.BranchPath> allPaths, String prefix, Set<BranchId> sink) {

        if (prefix == null) {
            return;
        }
        for (BranchHierarchyPort.BranchPath candidate : allPaths) {
            if (candidate.path().startsWith(prefix)) {
                sink.add(candidate.branchId());
            }
        }
    }

    /**
     * What the caller holds this permission on, in a form safe to put in a message.
     *
     * <p>Branch ids rather than names: the resolver has no clan vocabulary and will not acquire one
     * in order to write a sentence. The API layer substitutes names where it has them.
     */
    private static String summarize(List<PermissionGrant> held, Permission permission) {
        Set<String> scopes = new TreeSet<>();
        for (PermissionGrant grant : held) {
            if (grant.confers(permission)) {
                scopes.add(grant.scope().toString());
            }
        }
        return scopes.isEmpty() ? null : String.join(", ", scopes);
    }
}
