package app.giaky.linking.domain;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;

/**
 * A person node, reduced to what linking has to know about it.
 *
 * <p>Deliberately not a {@code Person}: Lineage owns that aggregate and no other context may import
 * it ({@code LayeringTest} rule 4). What linking needs is four facts — where the node sits, what it
 * is called, and whether an account is already attached — and a record that carries only those
 * cannot grow into a second copy of somebody else's model.
 *
 * <p>Soft-deleted nodes never appear as one of these. A deleted node is not claimable and not
 * linkable, and the port answers "no such node" for it, which is the same answer the API gives.
 *
 * @param branchId where the node is placed; null for an unplaced node, which no {@code BRANCH}-scoped
 *     approver can reach (doc 04 §3)
 * @param linkedUserId the account attached to it, or null — the field this whole context exists to
 *     write
 */
public record LinkableNode(
        PersonId id, ClanId clanId, BranchId branchId, String fullName, UserId linkedUserId) {

    public boolean isLinked() {
        return linkedUserId != null;
    }

    public boolean isLinkedTo(UserId userId) {
        return linkedUserId != null && linkedUserId.equals(userId);
    }
}
