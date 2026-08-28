package app.giaky.lineage.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.lineage.domain.Parentage;
import app.giaky.lineage.domain.ParentRole;
import app.giaky.lineage.domain.ParentageRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link ParentageRepository}. */
@Repository
public class ParentageRepositoryAdapter implements ParentageRepository {

    private final ParentageJpaRepository repository;
    private final ParentageMapper mapper;

    public ParentageRepositoryAdapter(ParentageJpaRepository repository, ParentageMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Parentage> findById(ClanId clanId, UUID id) {
        return repository.findByClanIdAndIdAndDeletedAtIsNull(clanId, id).map(mapper::toDomain);
    }

    @Override
    public List<Parentage> findByChild(ClanId clanId, PersonId childId) {
        return repository.findByClanIdAndChildPersonIdAndDeletedAtIsNull(clanId, childId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Parentage> findByParent(ClanId clanId, PersonId parentId) {
        return repository.findByClanIdAndParentPersonIdAndDeletedAtIsNull(clanId, parentId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Parentage> findAllByClan(ClanId clanId) {
        return repository.findByClanIdAndDeletedAtIsNull(clanId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Parentage> findByChildren(ClanId clanId, Collection<PersonId> personIds) {
        if (personIds.isEmpty()) {
            return List.of();
        }
        return repository.findByClanIdAndChildPersonIdInAndDeletedAtIsNull(clanId, personIds).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Parentage> findEdge(ClanId clanId, PersonId childId, ParentRole role) {
        return repository
                .findByClanIdAndChildPersonIdAndParentRoleAndDeletedAtIsNull(
                        clanId, childId, role.name())
                .map(mapper::toDomain);
    }

    @Override
    public long countByUnion(ClanId clanId, UUID unionId) {
        return repository.countByClanIdAndUnionIdAndDeletedAtIsNull(clanId, unionId);
    }

    @Override
    public Parentage save(Parentage parentage) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(parentage)));
    }
}
