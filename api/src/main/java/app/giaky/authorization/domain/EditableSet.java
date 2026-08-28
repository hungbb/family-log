package app.giaky.authorization.domain;

import app.giaky.common.types.BranchId;
import java.util.Set;
import java.util.UUID;

/**
 * Which nodes a user may edit in one clan, computed once (doc 04 §4.1).
 *
 * <p>Rendering a tree needs a {@code canEdit} flag per node, and asking {@code decide()} per node is
 * a query per node. This is the batch answer: clan-wide, or an explicit set of branches and persons
 * built from a handful of queries whose count depends on how many grants the user holds, not on how
 * large the tree is.
 *
 * <p>Phase 4 is what consumes it — there are no persons yet — but it is built and tested here,
 * because the thing it must agree with is the resolver, and the resolver is written here.
 *
 * @param clanWide true when a {@code CLAN}-scoped grant makes the question moot
 * @param branches branches whose members are all editable, expanded to include descendants
 * @param persons individually editable people, from {@code SUBTREE} grants
 */
public record EditableSet(boolean clanWide, Set<BranchId> branches, Set<UUID> persons) {

    public EditableSet {
        branches = Set.copyOf(branches);
        persons = Set.copyOf(persons);
    }

    /** Nothing at all — the answer for a member who holds only {@code CLAN_VIEW}. */
    public static EditableSet none() {
        return new EditableSet(false, Set.of(), Set.of());
    }

    /** Everything in the clan. */
    public static EditableSet everything() {
        return new EditableSet(true, Set.of(), Set.of());
    }

    /**
     * Whether a person may be edited.
     *
     * @param branchId where they are placed; null for an unplaced person, whom only a clan-wide or
     *     subtree grant can reach
     */
    public boolean contains(UUID personId, BranchId branchId) {
        if (clanWide) {
            return true;
        }
        if (persons.contains(personId)) {
            return true;
        }
        return branchId != null && branches.contains(branchId);
    }

    public boolean isEmpty() {
        return !clanWide && branches.isEmpty() && persons.isEmpty();
    }
}
