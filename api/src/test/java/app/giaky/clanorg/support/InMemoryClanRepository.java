package app.giaky.clanorg.support;

import app.giaky.clanorg.domain.Clan;
import app.giaky.clanorg.domain.ClanCode;
import app.giaky.clanorg.domain.ClanRepository;
import app.giaky.common.types.ClanId;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link ClanRepository} backed by a map.
 *
 * <p>This is what the port is for: {@code ClanService} can be driven against real aggregates with no
 * Spring context and no database, so the rules it enforces are asserted on state rather than on SQL.
 *
 * <p>It mirrors two behaviours of the real adapter that tests depend on: soft-deleted clans are
 * invisible to every read, and {@link #isClanCodeTaken} counts codes that <i>have been</i> issued
 * rather than codes currently in use (I-C1).
 */
public final class InMemoryClanRepository implements ClanRepository {

    private final Map<ClanId, Clan> byId = new LinkedHashMap<>();
    private final Set<String> issuedCodes = new HashSet<>();

    @Override
    public Optional<Clan> findById(ClanId id) {
        return Optional.ofNullable(byId.get(id)).filter(clan -> !clan.isDeleted());
    }

    @Override
    public Optional<Clan> findByClanCode(ClanCode code) {
        return byId.values().stream()
                .filter(clan -> !clan.isDeleted())
                .filter(clan -> clan.clanCode().equals(code))
                .findFirst();
    }

    @Override
    public boolean isClanCodeTaken(String code) {
        return issuedCodes.contains(code);
    }

    @Override
    public List<Clan> findAllById(Collection<ClanId> ids) {
        return ids.stream().map(byId::get).filter(clan -> clan != null && !clan.isDeleted()).toList();
    }

    @Override
    public Clan save(Clan clan) {
        byId.put(clan.id(), clan);
        issuedCodes.add(clan.clanCode().value());
        return clan;
    }

    /** Including deleted ones. A soft delete must not remove a row. */
    public int count() {
        return byId.size();
    }

    /** Reads a clan regardless of its soft-delete state, to assert that a delete was soft. */
    public Optional<Clan> findIncludingDeleted(ClanId id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Pre-loads a code so the collision path in {@code CodeGenerator} can be exercised. */
    public void reserveCode(String code) {
        issuedCodes.add(code);
    }
}
