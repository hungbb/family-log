package app.giaky.linking.domain;

import app.giaky.common.error.ConflictException;
import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * <b>The only writer of {@code person.linkedUserId}</b> (doc 02 §5, phase 6 exit criterion 2).
 *
 * <p>Three services want to attach an account to a node — approving a claim, an editor assigning
 * somebody directly, and the seed runner — and every one of them has to satisfy the same three
 * invariants. Put the invariants in the services and there are three copies, one of which will be
 * missing a check within a year. Put them here and there is one, which {@code
 * architecture.LinkWriterTest} then keeps that way.
 *
 * <p><b>What it enforces</b>, in the order a failure is worth reporting:
 *
 * <ul>
 *   <li><b>I-K1</b> — the node exists, is not deleted, and is not already somebody else's.
 *   <li><b>I-P5</b> — the account holds an {@code ACTIVE} membership in the node's clan. A link to
 *       somebody who left is a link nothing downstream would honour anyway.
 *   <li><b>I-P4</b> — the account holds no other node in this clan. One person, one place in the
 *       family; a second link would make "which node am I?" a question with two answers.
 * </ul>
 *
 * <p><b>What it deliberately does not do:</b> grant anything. Doc 04 §6 keeps being <i>in</i> the
 * tree and being able to <i>edit</i> it apart, and the temptation to hand out {@code TREE_EDIT} here
 * because it feels helpful is the single most likely design mistake in this phase (phase 6 §8).
 * There is no resolver on this class, which is the structural version of saying no.
 *
 * <p>Pure domain: no Spring, no transaction, no database. The transaction is the calling application
 * service's, and every method here is written to be safe to call inside one.
 */
public final class PersonUserLinker {

    private final PersonLinkPort nodes;
    private final ClanMemberPort members;

    public PersonUserLinker(PersonLinkPort nodes, ClanMemberPort members) {
        this.nodes = Objects.requireNonNull(nodes, "nodes must not be null");
        this.members = Objects.requireNonNull(members, "members must not be null");
    }

    /**
     * Attaches an account to a node.
     *
     * <p>Idempotent when the node already belongs to this same account: the caller asked for a state
     * the world is already in, and answering 409 to a retried request would be wrong about what
     * happened.
     *
     * @param actor whoever authorized this — the approver, or the user themselves for a direct
     *     assignment they requested; recorded as the row's {@code updated_by}
     * @return the node as it now stands
     * @throws NotFoundException no such live node in this clan
     * @throws ConflictException the node is somebody else's (I-K1), or this account already holds a
     *     node in this clan (I-P4)
     * @throws DomainRuleViolation the account is not an active member of the clan (I-P5)
     */
    public LinkableNode link(
            ClanId clanId, PersonId personId, UserId userId, UserId actor, Instant now) {

        Objects.requireNonNull(userId, "userId must not be null");
        LinkableNode node = require(clanId, personId);

        if (node.isLinkedTo(userId)) {
            return node;
        }
        // I-K1, read-before-write: the common case deserves a sentence rather than a lost race.
        if (node.isLinked()) {
            throw nodeAlreadyLinked();
        }
        // I-P5. 422 rather than 404: the account exists and the clan exists, and what is wrong is
        // the combination — which is exactly what a validation failure means (doc 05 §2).
        if (!members.isActiveMember(clanId, userId)) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED,
                    "That person is not an active member of this clan, so they cannot be linked to a"
                        + " node in it.");
        }
        // I-P4, checked here for a clear answer and held by ux_persons_linked_user underneath.
        Optional<LinkableNode> already = nodes.findLinkedTo(clanId, userId);
        if (already.isPresent()) {
            throw new ConflictException(
                    ErrorCode.USER_ALREADY_LINKED_IN_CLAN,
                    "That account is already linked to "
                            + already.get().fullName()
                            + " in this clan. Unlink that node first.");
        }

        // B6.11. The compare-and-set, not the check above, is what decides a race: two approvers
        // both got past the read, and exactly one of them changes a row here.
        if (!nodes.linkIfUnlinked(clanId, personId, userId, actor, now)) {
            throw nodeAlreadyLinked();
        }
        return require(clanId, personId);
    }

    /**
     * Detaches whatever account is on a node (FR-N5).
     *
     * <p>Who is allowed to ask is the caller's question — an approver in scope, or the linked user
     * themselves — and this method does not second-guess it. What it owns is that the node comes
     * back to the unlinked pool cleanly, whether or not anything was attached.
     *
     * @return the node as it now stands, with no account on it
     * @throws NotFoundException no such live node in this clan
     */
    public LinkableNode unlink(ClanId clanId, PersonId personId, UserId actor, Instant now) {
        LinkableNode node = require(clanId, personId);
        if (!node.isLinked()) {
            return node;
        }
        nodes.unlink(clanId, personId, actor, now);
        return require(clanId, personId);
    }

    /** The node this account holds in this clan, if any — what {@code /me} reports per clan. */
    public Optional<LinkableNode> nodeOf(ClanId clanId, UserId userId) {
        return nodes.findLinkedTo(clanId, userId);
    }

    /** One node, or the 404 the API gives for a deleted one and for one in another clan. */
    public LinkableNode require(ClanId clanId, PersonId personId) {
        return nodes.find(clanId, personId)
                .orElseThrow(
                        () -> new NotFoundException(ErrorCode.PERSON_NOT_FOUND, "No such person."));
    }

    private static ConflictException nodeAlreadyLinked() {
        return new ConflictException(
                ErrorCode.NODE_ALREADY_LINKED,
                "Somebody else has already been linked to this person.");
    }
}
