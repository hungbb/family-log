package app.giaky.linking.domain;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage for {@link NodeClaimRequest}. */
public interface NodeClaimRequestRepository {

    /** One claim inside a clan — the approver's read. */
    Optional<NodeClaimRequest> findById(ClanId clanId, UUID id);

    /**
     * One claim by id and requester — the requester's own read, used to cancel.
     *
     * <p>Scoped by the requester id from the access token rather than by clan, so an id belonging to
     * somebody else comes back empty and is answered with a 404 rather than with an authorization
     * question.
     */
    Optional<NodeClaimRequest> findByIdAndRequester(UUID id, UserId requesterUserId);

    /** I-K3: the live claim that would collide with a new one. */
    Optional<NodeClaimRequest> findPending(ClanId clanId, PersonId personId, UserId requesterUserId);

    /** Live claims on one node — what approving one has to close on the others. */
    List<NodeClaimRequest> findPendingForPerson(ClanId clanId, PersonId personId);

    /** The approver inbox (doc 05 §4.10), newest first. A null status means every status. */
    List<NodeClaimRequest> findByClanAndStatus(
            ClanId clanId, ClaimStatus status, int offset, int limit);

    long countByClanAndStatus(ClanId clanId, ClaimStatus status);

    /** A user's own claims, newest first (F6.4). Spans clans, like the join-request equivalent. */
    List<NodeClaimRequest> findByRequester(UserId requesterUserId, int limit);

    NodeClaimRequest save(NodeClaimRequest claim);
}
