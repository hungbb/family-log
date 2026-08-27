package app.giaky.clanorg.infrastructure;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.TenantExempt;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code clan_join_requests}. */
public interface ClanJoinRequestJpaRepository extends JpaRepository<ClanJoinRequestJpaEntity, UUID> {

    Optional<ClanJoinRequestJpaEntity> findByClanIdAndId(ClanId clanId, UUID id);

    /** I-J1, for a request aimed at a specific branch. */
    Optional<ClanJoinRequestJpaEntity> findByClanIdAndRequesterUserIdAndTargetBranchIdAndStatus(
            ClanId clanId, UserId requesterUserId, BranchId targetBranchId, String status);

    /**
     * I-J1, for a request aimed at the clan as a whole.
     *
     * <p>A separate method because {@code target_branch_id IS NULL} is not something a derived query
     * expresses with a null argument — Spring Data would generate {@code = null}, which matches
     * nothing, and the duplicate check would silently stop working.
     */
    Optional<ClanJoinRequestJpaEntity> findByClanIdAndRequesterUserIdAndTargetBranchIdIsNullAndStatus(
            ClanId clanId, UserId requesterUserId, String status);

    List<ClanJoinRequestJpaEntity> findByClanIdAndStatusOrderByCreatedAtDesc(
            ClanId clanId, String status, Pageable pageable);

    long countByClanIdAndStatus(ClanId clanId, String status);

    List<ClanJoinRequestJpaEntity> findByClanIdOrderByCreatedAtDesc(ClanId clanId, Pageable pageable);

    long countByClanId(ClanId clanId);

    @TenantExempt(
            justification =
                    "A requester holds a request id and, by definition, no membership to scope it"
                        + " with. The requester id comes from the access token, so this reads only"
                        + " the caller's own row (FR-J5).")
    Optional<ClanJoinRequestJpaEntity> findByIdAndRequesterUserId(UUID id, UserId requesterUserId);

    @TenantExempt(
            justification =
                    "\"My requests\" (FR-J5) spans the clans a user has applied to and is not a"
                        + " member of, so no ClanId exists to scope by. Scoped instead by the"
                        + " requester id from the access token.")
    List<ClanJoinRequestJpaEntity> findByRequesterUserIdOrderByCreatedAtDesc(
            UserId requesterUserId, Pageable pageable);

    @TenantExempt(
            justification =
                    "The 90-day expiry sweep (B2.9) is a background job over every tenant; a clan"
                        + " scope would defeat its purpose. It reads only pending requests past"
                        + " their date and writes only their status.")
    List<ClanJoinRequestJpaEntity> findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            String status, Instant now, Pageable pageable);
}
