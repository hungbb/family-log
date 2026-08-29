package app.giaky.linking.domain;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * The seam between Linking and the {@code persons} table Lineage owns.
 *
 * <p><b>The two writes here are the only two writes to {@code persons.linked_user_id} in the
 * product</b>, and {@link PersonUserLinker} is the only class that calls them — asserted by {@code
 * architecture.LinkWriterTest}, which is what makes the phase-6 exit criterion ("written by exactly
 * one class") a build failure rather than a habit.
 *
 * <p>{@link #linkIfUnlinked} is a compare-and-set rather than a read followed by a write, and that
 * is the whole of the concurrency guard (B6.11). Two approvers deciding the same claim at the same
 * moment both pass the read-before-write check in the service; the second one loses here, atomically,
 * and gets {@code 409 NODE_ALREADY_LINKED}. A read-modify-write would let both through and the last
 * writer would silently take the node.
 */
public interface PersonLinkPort {

    /** One live node, or empty when it is deleted or belongs to another clan. */
    Optional<LinkableNode> find(ClanId clanId, PersonId personId);

    /** I-P4: the node this account already holds in this clan, if it holds one. */
    Optional<LinkableNode> findLinkedTo(ClanId clanId, UserId userId);

    /**
     * Attaches an account to a node, but only while the node is still unattached.
     *
     * @return false when the node was linked, deleted or gone by the time the statement ran — the
     *     losing side of a race, which the caller turns into {@code 409 NODE_ALREADY_LINKED}
     */
    boolean linkIfUnlinked(
            ClanId clanId, PersonId personId, UserId userId, UserId actor, Instant now);

    /**
     * Detaches whatever account is on the node.
     *
     * @return false when there was nothing to detach, which is not an error — an unlink that finds
     *     the node already unlinked has produced the state the caller asked for
     */
    boolean unlink(ClanId clanId, PersonId personId, UserId actor, Instant now);
}
