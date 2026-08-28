package app.giaky.lineage.application;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.PersonId;
import java.util.UUID;

/**
 * Where a new person goes, and therefore who is allowed to put them there (doc 05 §4.7).
 *
 * <p><b>Placement is an authorization decision wearing a data-entry hat.</b> A node created with no
 * anchor has no branch, and a node with no branch is outside every {@code BRANCH}-scoped grant — so
 * "create unplaced" is the shape of a hole in the branch-isolation guarantee (phase 4 §8, goal G5).
 * Sending the placement with the create request is what closes it: the server derives both the
 * branch the node inherits and the target it checks {@code TREE_EDIT} against from the same field,
 * in the same transaction, so a node is never created somewhere the actor could not reach.
 *
 * <p>{@link Type#UNPLACED} still exists, because transcribing a book really does produce people
 * nobody can place yet — a name in a margin, a wife whose family is unrecorded. It requires clan-wide
 * {@code TREE_EDIT}, which is the honest price of a node no branch editor can be held responsible
 * for.
 *
 * @param unionId the marriage the child is born into, for {@link Type#CHILD_OF_UNION}
 * @param personId the parent, spouse, or nothing — depending on the type
 * @param branchId the destination branch, for {@link Type#BRANCH_ROOT}
 */
public record PersonPlacement(Type type, UUID unionId, PersonId personId, BranchId branchId) {

    /** How the new node attaches to what is already there. */
    public enum Type {

        /**
         * A child of a recorded marriage. Two parentage edges are written, one per partner, both
         * carrying the union id — which is what lets the tree show the children of each wife under
         * the marriage they belong to.
         */
        CHILD_OF_UNION,

        /** A child of one parent. The ordinary case in an old book, which often names only a father. */
        CHILD_OF_PARENT,

        /**
         * A spouse. A union is created; no parentage edge is, because a wife is not a descendant.
         *
         * <p>The union is {@code PRIMARY}, so this placement refuses somebody who already has a live
         * principal marriage (I-N3). A second wife is recorded the other way round — create her, then
         * record a {@code SECONDARY} union through the relationship endpoints — which is also the
         * only order in which the type can be stated.
         */
        SPOUSE_OF,

        /**
         * The person a branch descends from. Sets {@code branches.branch_root_person_id} (I-C5) and
         * places the node in that branch.
         */
        BRANCH_ROOT,

        /** Nowhere yet. Requires {@code TREE_EDIT} at clan scope. */
        UNPLACED
    }

    public static PersonPlacement childOfUnion(UUID unionId) {
        return new PersonPlacement(Type.CHILD_OF_UNION, unionId, null, null);
    }

    public static PersonPlacement childOf(PersonId parentId) {
        return new PersonPlacement(Type.CHILD_OF_PARENT, null, parentId, null);
    }

    public static PersonPlacement spouseOf(PersonId partnerId) {
        return new PersonPlacement(Type.SPOUSE_OF, null, partnerId, null);
    }

    public static PersonPlacement branchRoot(BranchId branchId) {
        return new PersonPlacement(Type.BRANCH_ROOT, null, null, branchId);
    }

    public static PersonPlacement unplaced() {
        return new PersonPlacement(Type.UNPLACED, null, null, null);
    }

    /** The person this placement hangs from, or null when it hangs from nothing. */
    public PersonId anchorPersonId() {
        return personId;
    }
}
