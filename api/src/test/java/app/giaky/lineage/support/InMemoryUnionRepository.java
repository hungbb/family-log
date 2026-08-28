package app.giaky.lineage.support;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.lineage.domain.Union;
import app.giaky.lineage.domain.UnionRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/** {@link UnionRepository} in a map. */
public final class InMemoryUnionRepository implements UnionRepository {

    private final Map<UUID, Union> rows = new LinkedHashMap<>();

    @Override
    public Optional<Union> findById(ClanId clanId, UUID id) {
        return Optional.ofNullable(rows.get(id))
                .filter(union -> union.clanId().equals(clanId) && !union.isDeleted());
    }

    @Override
    public List<Union> findByPartner(ClanId clanId, PersonId personId) {
        return live(clanId).filter(union -> union.involves(personId)).toList();
    }

    /** Order-insensitive, like the adapter that canonicalizes the pair before querying. */
    @Override
    public Optional<Union> findByPair(ClanId clanId, PersonId onePartner, PersonId otherPartner) {
        return live(clanId)
                .filter(union -> union.involves(onePartner) && union.involves(otherPartner))
                .findFirst();
    }

    @Override
    public List<Union> findAllByClan(ClanId clanId) {
        return live(clanId).toList();
    }

    @Override
    public List<Union> findByPartners(ClanId clanId, Collection<PersonId> personIds) {
        return live(clanId)
                .filter(
                        union ->
                                personIds.contains(union.partnerAId())
                                        || personIds.contains(union.partnerBId()))
                .toList();
    }

    @Override
    public Union save(Union union) {
        rows.put(union.id(), union);
        return union;
    }

    private Stream<Union> live(ClanId clanId) {
        return rows.values().stream()
                .filter(union -> union.clanId().equals(clanId))
                .filter(union -> !union.isDeleted());
    }
}
