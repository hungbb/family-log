package app.giaky.clanorg.support;

import app.giaky.clanorg.domain.ClanJoinRequest;
import app.giaky.clanorg.domain.ClanJoinRequestRepository;
import app.giaky.clanorg.domain.JoinRequestStatus;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A {@link ClanJoinRequestRepository} backed by a map.
 *
 * <p>Enforces I-J1 the way {@code ux_join_pending} does, including its treatment of a null target
 * branch as a value rather than an absence — the case a derived SQL query gets wrong if nobody is
 * watching.
 */
public final class InMemoryClanJoinRequestRepository implements ClanJoinRequestRepository {

    private final Map<UUID, ClanJoinRequest> byId = new LinkedHashMap<>();

    @Override
    public Optional<ClanJoinRequest> findById(ClanId clanId, UUID id) {
        return Optional.ofNullable(byId.get(id)).filter(r -> r.clanId().equals(clanId));
    }

    @Override
    public Optional<ClanJoinRequest> findByIdAndRequester(UUID id, UserId requesterUserId) {
        return Optional.ofNullable(byId.get(id))
                .filter(r -> r.requesterUserId().equals(requesterUserId));
    }

    @Override
    public Optional<ClanJoinRequest> findPending(
            ClanId clanId, BranchId targetBranchId, UserId requesterUserId) {

        return byId.values().stream()
                .filter(ClanJoinRequest::isPending)
                .filter(r -> r.clanId().equals(clanId))
                .filter(r -> r.requesterUserId().equals(requesterUserId))
                .filter(r -> Objects.equals(r.targetBranchId(), targetBranchId))
                .findFirst();
    }

    @Override
    public List<ClanJoinRequest> findByClanAndStatus(
            ClanId clanId, JoinRequestStatus status, int offset, int limit) {

        List<ClanJoinRequest> all =
                byId.values().stream()
                        .filter(r -> r.clanId().equals(clanId))
                        .filter(r -> status == null || r.status() == status)
                        .sorted(Comparator.comparing(ClanJoinRequest::createdAt).reversed())
                        .toList();
        int from = Math.min(offset, all.size());
        return all.subList(from, Math.min(from + limit, all.size()));
    }

    @Override
    public long countByClanAndStatus(ClanId clanId, JoinRequestStatus status) {
        return byId.values().stream()
                .filter(r -> r.clanId().equals(clanId))
                .filter(r -> status == null || r.status() == status)
                .count();
    }

    @Override
    public List<ClanJoinRequest> findByRequester(UserId requesterUserId, int limit) {
        return byId.values().stream()
                .filter(r -> r.requesterUserId().equals(requesterUserId))
                .sorted(Comparator.comparing(ClanJoinRequest::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<ClanJoinRequest> findExpired(Instant now, int limit) {
        return byId.values().stream()
                .filter(ClanJoinRequest::isPending)
                .filter(r -> r.hasExpired(now))
                .sorted(Comparator.comparing(ClanJoinRequest::expiresAt))
                .limit(limit)
                .toList();
    }

    @Override
    public ClanJoinRequest save(ClanJoinRequest request) {
        if (request.isPending()) {
            // `ux_join_pending`, in a map.
            findPending(request.clanId(), request.targetBranchId(), request.requesterUserId())
                    .filter(existing -> !existing.id().equals(request.id()))
                    .ifPresent(
                            existing -> {
                                throw new IllegalStateException(
                                        "I-J1: a pending request already exists for that target");
                            });
        }
        byId.put(request.id(), request);
        return request;
    }

    public List<ClanJoinRequest> all() {
        return List.copyOf(byId.values());
    }
}
