package app.giaky.lineage.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.lineage.domain.Union;
import app.giaky.lineage.domain.UnionRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link UnionRepository}. */
@Repository
public class UnionRepositoryAdapter implements UnionRepository {

    private final UnionJpaRepository repository;
    private final UnionMapper mapper;

    public UnionRepositoryAdapter(UnionJpaRepository repository, UnionMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Union> findById(ClanId clanId, UUID id) {
        return repository.findByClanIdAndIdAndDeletedAtIsNull(clanId, id).map(mapper::toDomain);
    }

    @Override
    public List<Union> findByPartner(ClanId clanId, PersonId personId) {
        return repository.findByPartner(clanId, personId).stream().map(mapper::toDomain).toList();
    }

    /**
     * Canonicalizes the pair before looking it up.
     *
     * <p>The unique index is on the ordered pair, so a caller asking about (B, A) would otherwise
     * miss the row stored as (A, B) — and would then create the duplicate I-N2 exists to prevent.
     * Doing the ordering here means no call site has to remember it.
     */
    @Override
    public Optional<Union> findByPair(ClanId clanId, PersonId onePartner, PersonId otherPartner) {
        boolean ordered = onePartner.value().compareTo(otherPartner.value()) < 0;
        return repository
                .findByClanIdAndPartnerAIdAndPartnerBIdAndDeletedAtIsNull(
                        clanId,
                        ordered ? onePartner : otherPartner,
                        ordered ? otherPartner : onePartner)
                .map(mapper::toDomain);
    }

    @Override
    public List<Union> findAllByClan(ClanId clanId) {
        return repository.findByClanIdAndDeletedAtIsNull(clanId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Union> findByPartners(ClanId clanId, Collection<PersonId> personIds) {
        if (personIds.isEmpty()) {
            return List.of();
        }
        return repository.findByPartners(clanId, personIds).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Union save(Union union) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(union)));
    }
}
