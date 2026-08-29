package app.giaky.linking.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.linking.domain.ClaimStatus;
import app.giaky.linking.domain.NodeClaimRequest;
import app.giaky.linking.domain.NodeClaimRequestRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link NodeClaimRequestRepository}. */
@Repository
public class NodeClaimRequestRepositoryAdapter implements NodeClaimRequestRepository {

    private static final String PENDING = ClaimStatus.PENDING.name();

    private final NodeClaimRequestJpaRepository repository;
    private final NodeClaimRequestMapper mapper;

    public NodeClaimRequestRepositoryAdapter(
            NodeClaimRequestJpaRepository repository, NodeClaimRequestMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<NodeClaimRequest> findById(ClanId clanId, UUID id) {
        return repository.findByClanIdAndId(clanId, id).map(mapper::toDomain);
    }

    @Override
    public Optional<NodeClaimRequest> findByIdAndRequester(UUID id, UserId requesterUserId) {
        return repository.findByIdAndRequesterUserId(id, requesterUserId).map(mapper::toDomain);
    }

    @Override
    public Optional<NodeClaimRequest> findPending(
            ClanId clanId, PersonId personId, UserId requesterUserId) {

        return repository
                .findByClanIdAndPersonIdAndRequesterUserIdAndStatus(
                        clanId, personId, requesterUserId, PENDING)
                .map(mapper::toDomain);
    }

    @Override
    public List<NodeClaimRequest> findPendingForPerson(ClanId clanId, PersonId personId) {
        return repository.findByClanIdAndPersonIdAndStatus(clanId, personId, PENDING).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<NodeClaimRequest> findByClanAndStatus(
            ClanId clanId, ClaimStatus status, int offset, int limit) {

        Pageable page = offsetPage(offset, limit);
        List<NodeClaimRequestJpaEntity> rows =
                status == null
                        ? repository.findByClanIdOrderByCreatedAtDesc(clanId, page)
                        : repository.findByClanIdAndStatusOrderByCreatedAtDesc(
                                clanId, status.name(), page);
        return rows.stream().map(mapper::toDomain).toList();
    }

    @Override
    public long countByClanAndStatus(ClanId clanId, ClaimStatus status) {
        return status == null
                ? repository.countByClanId(clanId)
                : repository.countByClanIdAndStatus(clanId, status.name());
    }

    @Override
    public List<NodeClaimRequest> findByRequester(UserId requesterUserId, int limit) {
        return repository
                .findByRequesterUserIdOrderByCreatedAtDesc(requesterUserId, PageRequest.of(0, limit))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public NodeClaimRequest save(NodeClaimRequest claim) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(claim)));
    }

    /**
     * An offset expressed as a Spring Data page.
     *
     * <p>The port speaks offset and limit because the domain may not know what a {@code Pageable}
     * is; the API speaks page and size. They agree whenever the offset is a multiple of the limit,
     * which is exactly how the controller builds it.
     */
    private static Pageable offsetPage(int offset, int limit) {
        return PageRequest.of(limit == 0 ? 0 : offset / limit, Math.max(limit, 1));
    }
}
