package app.giaky.clanorg.domain;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage for {@link ClanJoinRequest}. */
public interface ClanJoinRequestRepository {

    /** One request inside a clan — the approver's read. */
    Optional<ClanJoinRequest> findById(ClanId clanId, UUID id);

    /**
     * One request by id and requester.
     *
     * <p>The requester's own read, used to cancel. Unscoped by clan because a requester holds a
     * request id and no clan membership to scope it with; the requester id is the scope, and it comes
     * from the access token rather than from the request.
     */
    Optional<ClanJoinRequest> findByIdAndRequester(UUID id, UserId requesterUserId);

    /** I-J1: the live request that would collide with a new one. */
    Optional<ClanJoinRequest> findPending(ClanId clanId, BranchId targetBranchId, UserId requesterUserId);

    /** The approver inbox (doc 05 §4.5), newest first. Null status means every status. */
    List<ClanJoinRequest> findByClanAndStatus(
            ClanId clanId, JoinRequestStatus status, int offset, int limit);

    long countByClanAndStatus(ClanId clanId, JoinRequestStatus status);

    /**
     * A user's own outbound requests, newest first (FR-J5).
     *
     * <p>Unscoped by clan for the same reason as {@link #findByIdAndRequester}: the whole point is
     * to show requests across clans the caller is not yet a member of.
     */
    List<ClanJoinRequest> findByRequester(UserId requesterUserId, int limit);

    /** Pending requests past their date, oldest first. The sweep reads this and nothing else. */
    List<ClanJoinRequest> findExpired(Instant now, int limit);

    ClanJoinRequest save(ClanJoinRequest request);
}
