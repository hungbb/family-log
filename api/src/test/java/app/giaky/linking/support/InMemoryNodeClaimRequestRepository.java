package app.giaky.linking.support;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.linking.domain.ClaimStatus;
import app.giaky.linking.domain.NodeClaimRequest;
import app.giaky.linking.domain.NodeClaimRequestRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link NodeClaimRequestRepository} in a map.
 *
 * <p>Honest about what it cannot do: {@code ux_claim_pending} is a database constraint, and nothing
 * here throws {@code DataIntegrityViolationException}. The read-before-write in {@code ClaimService}
 * is what these tests exercise; the index that catches the race is covered by an integration test
 * against real Postgres, which is the only place it can be covered honestly.
 */
public final class InMemoryNodeClaimRequestRepository implements NodeClaimRequestRepository {

    private final Map<UUID, NodeClaimRequest> rows = new LinkedHashMap<>();

    @Override
    public Optional<NodeClaimRequest> findById(ClanId clanId, UUID id) {
        return Optional.ofNullable(rows.get(id)).filter(claim -> claim.clanId().equals(clanId));
    }

    @Override
    public Optional<NodeClaimRequest> findByIdAndRequester(UUID id, UserId requesterUserId) {
        return Optional.ofNullable(rows.get(id))
                .filter(claim -> claim.requesterUserId().equals(requesterUserId));
    }

    @Override
    public Optional<NodeClaimRequest> findPending(
            ClanId clanId, PersonId personId, UserId requesterUserId) {

        return rows.values().stream()
                .filter(claim -> claim.clanId().equals(clanId))
                .filter(claim -> claim.personId().equals(personId))
                .filter(claim -> claim.requesterUserId().equals(requesterUserId))
                .filter(NodeClaimRequest::isPending)
                .findFirst();
    }

    @Override
    public List<NodeClaimRequest> findPendingForPerson(ClanId clanId, PersonId personId) {
        return rows.values().stream()
                .filter(claim -> claim.clanId().equals(clanId))
                .filter(claim -> claim.personId().equals(personId))
                .filter(NodeClaimRequest::isPending)
                .toList();
    }

    @Override
    public List<NodeClaimRequest> findPendingByRequester(ClanId clanId, UserId requesterUserId) {
        return rows.values().stream()
                .filter(claim -> claim.clanId().equals(clanId))
                .filter(claim -> claim.requesterUserId().equals(requesterUserId))
                .filter(NodeClaimRequest::isPending)
                .toList();
    }

    @Override
    public List<NodeClaimRequest> findByClanAndStatus(
            ClanId clanId, ClaimStatus status, int offset, int limit) {

        List<NodeClaimRequest> matching =
                rows.values().stream()
                        .filter(claim -> claim.clanId().equals(clanId))
                        .filter(claim -> status == null || claim.status() == status)
                        .sorted(Comparator.comparing(NodeClaimRequest::createdAt).reversed())
                        .toList();

        int from = Math.min(offset, matching.size());
        int to = Math.min(from + limit, matching.size());
        return new ArrayList<>(matching.subList(from, to));
    }

    @Override
    public long countByClanAndStatus(ClanId clanId, ClaimStatus status) {
        return rows.values().stream()
                .filter(claim -> claim.clanId().equals(clanId))
                .filter(claim -> status == null || claim.status() == status)
                .count();
    }

    @Override
    public List<NodeClaimRequest> findByRequester(UserId requesterUserId, int limit) {
        return rows.values().stream()
                .filter(claim -> claim.requesterUserId().equals(requesterUserId))
                .sorted(Comparator.comparing(NodeClaimRequest::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public NodeClaimRequest save(NodeClaimRequest claim) {
        rows.put(claim.id(), claim);
        return claim;
    }
}
