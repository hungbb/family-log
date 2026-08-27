package app.giaky.clanorg.infrastructure;

import app.giaky.clanorg.domain.ClanJoinRequest;
import app.giaky.clanorg.domain.ClanJoinRequestRepository;
import app.giaky.clanorg.domain.JoinRequestStatus;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link ClanJoinRequestRepository}. */
@Repository
public class ClanJoinRequestRepositoryAdapter implements ClanJoinRequestRepository {

    private static final String PENDING = JoinRequestStatus.PENDING.name();

    private final ClanJoinRequestJpaRepository repository;
    private final ClanJoinRequestMapper mapper;

    public ClanJoinRequestRepositoryAdapter(
            ClanJoinRequestJpaRepository repository, ClanJoinRequestMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<ClanJoinRequest> findById(ClanId clanId, UUID id) {
        return repository.findByClanIdAndId(clanId, id).map(mapper::toDomain);
    }

    @Override
    public Optional<ClanJoinRequest> findByIdAndRequester(UUID id, UserId requesterUserId) {
        return repository.findByIdAndRequesterUserId(id, requesterUserId).map(mapper::toDomain);
    }

    @Override
    public Optional<ClanJoinRequest> findPending(
            ClanId clanId, BranchId targetBranchId, UserId requesterUserId) {

        // Null is a value here, not an absence: "the clan as a whole" is a distinct target from any
        // branch, and SQL will not compare it with `=`. See the two finders on the repository.
        return (targetBranchId == null
                        ? repository.findByClanIdAndRequesterUserIdAndTargetBranchIdIsNullAndStatus(
                                clanId, requesterUserId, PENDING)
                        : repository.findByClanIdAndRequesterUserIdAndTargetBranchIdAndStatus(
                                clanId, requesterUserId, targetBranchId, PENDING))
                .map(mapper::toDomain);
    }

    @Override
    public List<ClanJoinRequest> findByClanAndStatus(
            ClanId clanId, JoinRequestStatus status, int offset, int limit) {

        Pageable page = offsetPage(offset, limit);
        List<ClanJoinRequestJpaEntity> rows =
                status == null
                        ? repository.findByClanIdOrderByCreatedAtDesc(clanId, page)
                        : repository.findByClanIdAndStatusOrderByCreatedAtDesc(
                                clanId, status.name(), page);
        return rows.stream().map(mapper::toDomain).toList();
    }

    @Override
    public long countByClanAndStatus(ClanId clanId, JoinRequestStatus status) {
        return status == null
                ? repository.countByClanId(clanId)
                : repository.countByClanIdAndStatus(clanId, status.name());
    }

    @Override
    public List<ClanJoinRequest> findByRequester(UserId requesterUserId, int limit) {
        return repository
                .findByRequesterUserIdOrderByCreatedAtDesc(
                        requesterUserId, PageRequest.of(0, limit))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<ClanJoinRequest> findExpired(Instant now, int limit) {
        return repository
                .findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                        PENDING, now, PageRequest.of(0, limit))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public ClanJoinRequest save(ClanJoinRequest request) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(request)));
    }

    /**
     * An offset expressed as a Spring Data page.
     *
     * <p>The port speaks offset and limit because the domain may not know what a {@code Pageable}
     * is; the API speaks page and size. They agree whenever offset is a multiple of the limit, which
     * is exactly how the controller builds it.
     */
    private static Pageable offsetPage(int offset, int limit) {
        return PageRequest.of(limit == 0 ? 0 : offset / limit, Math.max(limit, 1));
    }
}
