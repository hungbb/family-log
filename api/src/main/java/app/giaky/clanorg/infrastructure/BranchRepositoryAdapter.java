package app.giaky.clanorg.infrastructure;

import app.giaky.clanorg.domain.Branch;
import app.giaky.clanorg.domain.BranchCode;
import app.giaky.clanorg.domain.BranchRepository;
import app.giaky.clanorg.domain.MaterializedPath;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link BranchRepository}. */
@Repository
public class BranchRepositoryAdapter implements BranchRepository {

    private final BranchJpaRepository repository;
    private final BranchMapper mapper;

    public BranchRepositoryAdapter(BranchJpaRepository repository, BranchMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Branch> findById(ClanId clanId, BranchId id) {
        return repository
                .findByClanIdAndIdAndDeletedAtIsNull(clanId, id.value())
                .map(mapper::toDomain);
    }

    @Override
    public List<Branch> findAllByClan(ClanId clanId) {
        return repository.findByClanIdAndDeletedAtIsNullOrderByPathAsc(clanId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Branch> findByBranchCode(BranchCode code) {
        return repository.findByBranchCodeAndDeletedAtIsNull(code.value()).map(mapper::toDomain);
    }

    @Override
    public boolean isBranchCodeTaken(String code) {
        return repository.existsByBranchCode(code);
    }

    @Override
    public boolean isPathTaken(ClanId clanId, MaterializedPath path) {
        return repository.existsByClanIdAndPathAndDeletedAtIsNull(clanId, path.value());
    }

    @Override
    public long countLiveChildren(ClanId clanId, BranchId parentId) {
        return repository.countByClanIdAndParentBranchIdAndDeletedAtIsNullAndArchivedAtIsNull(
                clanId, parentId);
    }

    @Override
    public Branch save(Branch branch) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(branch)));
    }

    @Override
    public int rewriteSubtreePaths(ClanId clanId, BranchId root, Instant now, UserId editor) {
        return repository.rewriteSubtreePaths(
                clanId.value(), root.value(), now, editor == null ? null : editor.value());
    }
}
