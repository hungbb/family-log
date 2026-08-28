package app.giaky.authorization.domain;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * <i>What</i> a permission is being asked about (doc 04 §4).
 *
 * <p>Sealed, and that is the point of the class. Doc 04 §1.5 says a target the resolver cannot
 * classify is a denial and never a fallthrough allow; sealing turns "cannot classify" from a runtime
 * worry into a compile-time impossibility, and makes the {@code switch} in {@link ScopeCoverage}
 * exhaustive without a {@code default} branch that could one day quietly say yes.
 *
 * <p>Every target names its clan, because step 1 of the algorithm is a tenancy check: a target in a
 * different clan from the one the request addresses is an immediate deny (NFR-4), and a target that
 * could not say which clan it belonged to would make that check impossible to write.
 */
public sealed interface Target {

    /** The clan this target lives in. */
    ClanId clanId();

    /** A short, log-safe description used in denial messages. */
    String describe();

    /** The clan itself: settings, branch structure, the member list as a whole. */
    static ClanTarget clan(ClanId clanId) {
        return new ClanTarget(clanId);
    }

    static BranchTarget branch(ClanId clanId, BranchId branchId) {
        return new BranchTarget(clanId, branchId);
    }

    /**
     * A person, together with the branch they are placed in.
     *
     * <p>The branch travels with the person rather than being looked up: placement is Lineage's
     * fact, the caller has already loaded the person to reach this point, and a resolver that went
     * looking for it would put a query on the hot path of every check.
     */
    static PersonTarget person(ClanId clanId, UUID personId, BranchId branchId) {
        return new PersonTarget(clanId, personId, branchId);
    }

    /** An edge — a parentage, a union, a move — which needs permission on every endpoint (§5.2). */
    static RelationshipTarget relationship(ClanId clanId, List<PersonTarget> endpoints) {
        return new RelationshipTarget(clanId, endpoints);
    }

    /**
     * The narrowest target a grant at {@code scope} would let somebody act on.
     *
     * <p>Grant management asks its questions about scopes, and the resolver answers questions about
     * targets. This is the one conversion between them, so "do I hold enough to hand this out?"
     * routes through exactly the same coverage rules as "may I edit this node?" — which is the
     * property that makes escalation impossible to express rather than merely difficult.
     */
    static Target forScope(ClanId clanId, PermissionScope scope) {
        return switch (scope.type()) {
            case CLAN -> clan(clanId);
            case BRANCH -> branch(clanId, scope.branchId());
            // A subtree scope is a person, and no branch placement is known or needed: only another
            // subtree grant covering that person, or a clan-wide one, reaches it.
            case SUBTREE -> person(clanId, scope.refId(), null);
        };
    }

    /** The clan as a whole. Only a {@code CLAN}-scoped grant ever covers this. */
    record ClanTarget(ClanId clanId) implements Target {

        public ClanTarget {
            Objects.requireNonNull(clanId, "clanId must not be null");
        }

        @Override
        public String describe() {
            return "the clan";
        }
    }

    /** One branch of the tree. */
    record BranchTarget(ClanId clanId, BranchId branchId) implements Target {

        public BranchTarget {
            Objects.requireNonNull(clanId, "clanId must not be null");
            Objects.requireNonNull(branchId, "branchId must not be null");
        }

        @Override
        public String describe() {
            return "branch " + branchId;
        }
    }

    /**
     * One person node.
     *
     * @param branchId where the person sits; null when they are not placed in any branch, which no
     *     {@code BRANCH}-scoped grant can then cover
     */
    record PersonTarget(ClanId clanId, UUID personId, BranchId branchId) implements Target {

        public PersonTarget {
            Objects.requireNonNull(clanId, "clanId must not be null");
            Objects.requireNonNull(personId, "personId must not be null");
        }

        @Override
        public String describe() {
            return "person " + personId;
        }
    }

    /**
     * Several people at once, all of whom must be covered (doc 04 §5.2).
     *
     * <p>This is what closes the obvious escape hatch. A branch editor who could add a parentage
     * from one of their own nodes to somebody else's would be editing the other branch by writing
     * into their own; requiring every endpoint means the edge is refused instead.
     */
    record RelationshipTarget(ClanId clanId, List<PersonTarget> endpoints) implements Target {

        public RelationshipTarget {
            Objects.requireNonNull(clanId, "clanId must not be null");
            endpoints = List.copyOf(endpoints);
            if (endpoints.isEmpty()) {
                throw new IllegalArgumentException("a relationship target needs at least one person");
            }
            for (PersonTarget endpoint : endpoints) {
                if (!endpoint.clanId().equals(clanId)) {
                    // NFR-4. An edge spanning two clans is not a permission question, it is a bug,
                    // and answering "denied" would hide it.
                    throw new IllegalArgumentException("every endpoint must be in the same clan");
                }
            }
        }

        @Override
        public String describe() {
            return endpoints.size() + " linked people";
        }
    }
}
