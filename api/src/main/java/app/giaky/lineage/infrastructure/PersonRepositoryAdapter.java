package app.giaky.lineage.infrastructure;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.lineage.domain.Person;
import app.giaky.lineage.domain.PersonRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link PersonRepository}. */
@Repository
public class PersonRepositoryAdapter implements PersonRepository {

    private final PersonJpaRepository repository;
    private final PersonMapper mapper;

    public PersonRepositoryAdapter(PersonJpaRepository repository, PersonMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Person> findById(ClanId clanId, PersonId id) {
        return repository
                .findByClanIdAndIdAndDeletedAtIsNull(clanId, id.value())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Person> findByIdIncludingDeleted(ClanId clanId, PersonId id) {
        return repository.findByClanIdAndId(clanId, id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Person> findAllByClan(ClanId clanId) {
        return repository.findByClanIdAndDeletedAtIsNull(clanId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Person> findAllByIds(ClanId clanId, Collection<PersonId> ids) {
        if (ids.isEmpty()) {
            // `IN ()` is not valid SQL, and Spring Data would happily build one.
            return List.of();
        }
        List<UUID> raw = ids.stream().map(PersonId::value).toList();
        return repository.findByClanIdAndIdInAndDeletedAtIsNull(clanId, raw).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Person> findByBranch(ClanId clanId, BranchId branchId) {
        return repository.findByClanIdAndBranchIdAndDeletedAtIsNull(clanId, branchId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Person> findDeletedSince(ClanId clanId, Instant deletedSince) {
        return repository
                .findByClanIdAndDeletedAtGreaterThanEqualOrderByDeletedAtDesc(clanId, deletedSince)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Person> findFoundingAncestor(ClanId clanId) {
        return repository
                .findByClanIdAndFoundingAncestorTrueAndDeletedAtIsNull(clanId)
                .map(mapper::toDomain);
    }

    @Override
    public PersonPage search(ClanId clanId, PersonQuery query) {
        Page<PersonJpaEntity> page =
                repository.search(
                        clanId.value(),
                        query.text(),
                        query.generation(),
                        query.lifeStatus() == null ? null : query.lifeStatus().name(),
                        query.branchId() == null ? null : query.branchId().value(),
                        query.linked(),
                        // Unsorted: the native query carries its own ORDER BY, and a Pageable sort
                        // would have Spring Data append a second one it cannot reconcile.
                        PageRequest.of(query.page(), query.size()));

        return new PersonPage(
                page.getContent().stream().map(mapper::toDomain).toList(), page.getTotalElements());
    }

    @Override
    public long countByClan(ClanId clanId) {
        return repository.countByClanIdAndDeletedAtIsNull(clanId);
    }

    @Override
    public Person save(Person person) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(person)));
    }

    @Override
    public int updateGenerations(
            ClanId clanId, Collection<GenerationAssignment> assignments, Instant now) {

        if (assignments.isEmpty()) {
            return 0;
        }
        StringJoiner ids = new StringJoiner(",");
        StringJoiner generations = new StringJoiner(",");
        for (GenerationAssignment assignment : assignments) {
            ids.add(assignment.personId().value().toString());
            // An empty position is "unknown", which the statement turns back into NULL. A person
            // with no path to the founding ancestor genuinely has no đời (phase 4 §8).
            generations.add(
                    assignment.generation() == null ? "" : assignment.generation().toString());
        }
        return repository.updateGenerations(
                clanId.value(), ids.toString(), generations.toString(), now);
    }
}
