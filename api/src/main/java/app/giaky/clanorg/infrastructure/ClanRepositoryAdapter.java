package app.giaky.clanorg.infrastructure;

import app.giaky.clanorg.domain.Clan;
import app.giaky.clanorg.domain.ClanCode;
import app.giaky.clanorg.domain.ClanRepository;
import app.giaky.common.types.ClanId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * The JPA side of {@link ClanRepository}.
 *
 * <p>Thin: it maps and delegates. {@code saveAndFlush} rather than {@code save} is the one decision
 * here, and it is a deliberate one — {@code @Version} is bumped when the UPDATE reaches the
 * database, so a plain {@code save} would hand the caller the version it started with and the
 * client's next PATCH would fail with a conflict it had no way to avoid.
 */
@Repository
public class ClanRepositoryAdapter implements ClanRepository {

    private final ClanJpaRepository repository;
    private final ClanMapper mapper;

    public ClanRepositoryAdapter(ClanJpaRepository repository, ClanMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Clan> findById(ClanId id) {
        return repository.findByIdAndDeletedAtIsNull(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Clan> findByClanCode(ClanCode code) {
        return repository.findByClanCodeAndDeletedAtIsNull(code.value()).map(mapper::toDomain);
    }

    @Override
    public boolean isClanCodeTaken(String code) {
        return repository.existsByClanCode(code);
    }

    @Override
    public List<Clan> findAllById(Collection<ClanId> ids) {
        if (ids.isEmpty()) {
            // `IN ()` is not valid SQL, and Spring Data would happily build it.
            return List.of();
        }
        List<UUID> raw = ids.stream().map(ClanId::value).toList();
        return repository.findByIdInAndDeletedAtIsNull(raw).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Clan save(Clan clan) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(clan)));
    }
}
