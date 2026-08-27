package app.giaky.clanorg.infrastructure;

import app.giaky.clanorg.domain.ClanMembership;
import app.giaky.clanorg.domain.ClanMembershipRepository;
import app.giaky.clanorg.domain.MembershipStatus;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link ClanMembershipRepository}. */
@Repository
public class ClanMembershipRepositoryAdapter implements ClanMembershipRepository {

    private static final String ACTIVE = MembershipStatus.ACTIVE.name();

    private final ClanMembershipJpaRepository repository;
    private final ClanMembershipMapper mapper;

    public ClanMembershipRepositoryAdapter(
            ClanMembershipJpaRepository repository, ClanMembershipMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<ClanMembership> findActive(ClanId clanId, UserId userId) {
        return repository
                .findByClanIdAndUserIdAndStatus(clanId, userId, ACTIVE)
                .map(mapper::toDomain);
    }

    @Override
    public List<ClanMembership> findActiveByUser(UserId userId) {
        return repository.findByUserIdAndStatusOrderByJoinedAtDesc(userId, ACTIVE).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<ClanMembership> findAllActive(ClanId clanId) {
        return repository.findByClanIdAndStatusOrderByJoinedAtDesc(clanId, ACTIVE).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<BranchMemberCount> countActiveMembersPerBranch(ClanId clanId) {
        return repository.countActiveMembersPerBranch(clanId).stream()
                .map(row -> new BranchMemberCount((BranchId) row[0], (Long) row[1]))
                .toList();
    }

    @Override
    public ClanMembership save(ClanMembership membership) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(membership)));
    }
}
