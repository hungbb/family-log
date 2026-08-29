package app.giaky.linking.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.TenantExempt;
import app.giaky.common.types.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code node_claim_requests}. */
public interface NodeClaimRequestJpaRepository extends JpaRepository<NodeClaimRequestJpaEntity, UUID> {

    Optional<NodeClaimRequestJpaEntity> findByClanIdAndId(ClanId clanId, UUID id);

    /** I-K3, the row {@code ux_claim_pending} holds under a race. */
    Optional<NodeClaimRequestJpaEntity> findByClanIdAndPersonIdAndRequesterUserIdAndStatus(
            ClanId clanId, PersonId personId, UserId requesterUserId, String status);

    List<NodeClaimRequestJpaEntity> findByClanIdAndPersonIdAndStatus(
            ClanId clanId, PersonId personId, String status);

    List<NodeClaimRequestJpaEntity> findByClanIdAndStatusOrderByCreatedAtDesc(
            ClanId clanId, String status, Pageable pageable);

    long countByClanIdAndStatus(ClanId clanId, String status);

    List<NodeClaimRequestJpaEntity> findByClanIdOrderByCreatedAtDesc(
            ClanId clanId, Pageable pageable);

    long countByClanId(ClanId clanId);

    @TenantExempt(
            justification =
                    "The requester's own cancel path. The requester id comes from the access token,"
                        + " so this reads only the caller's own row; a claim id belonging to"
                        + " somebody else comes back empty and is answered 404.")
    Optional<NodeClaimRequestJpaEntity> findByIdAndRequesterUserId(UUID id, UserId requesterUserId);

    @TenantExempt(
            justification =
                    "\"My claims\" (F6.4) spans every clan the caller has claimed a node in, so no"
                        + " single ClanId exists to scope by. Scoped instead by the requester id"
                        + " from the access token.")
    List<NodeClaimRequestJpaEntity> findByRequesterUserIdOrderByCreatedAtDesc(
            UserId requesterUserId, Pageable pageable);
}
