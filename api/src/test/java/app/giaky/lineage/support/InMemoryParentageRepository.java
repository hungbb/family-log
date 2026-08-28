package app.giaky.lineage.support;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.lineage.domain.Parentage;
import app.giaky.lineage.domain.ParentRole;
import app.giaky.lineage.domain.ParentageRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/** {@link ParentageRepository} in a map. Soft-deleted edges are invisible, as in the real one. */
public final class InMemoryParentageRepository implements ParentageRepository {

    private final Map<UUID, Parentage> rows = new LinkedHashMap<>();

    @Override
    public Optional<Parentage> findById(ClanId clanId, UUID id) {
        return Optional.ofNullable(rows.get(id))
                .filter(edge -> edge.clanId().equals(clanId) && !edge.isDeleted());
    }

    @Override
    public List<Parentage> findByChild(ClanId clanId, PersonId childId) {
        return live(clanId).filter(edge -> edge.childPersonId().equals(childId)).toList();
    }

    @Override
    public List<Parentage> findByParent(ClanId clanId, PersonId parentId) {
        return live(clanId).filter(edge -> edge.parentPersonId().equals(parentId)).toList();
    }

    @Override
    public List<Parentage> findAllByClan(ClanId clanId) {
        return live(clanId).toList();
    }

    @Override
    public List<Parentage> findByChildren(ClanId clanId, Collection<PersonId> personIds) {
        return live(clanId).filter(edge -> personIds.contains(edge.childPersonId())).toList();
    }

    @Override
    public Optional<Parentage> findEdge(ClanId clanId, PersonId childId, ParentRole role) {
        return live(clanId)
                .filter(edge -> edge.childPersonId().equals(childId) && edge.parentRole() == role)
                .findFirst();
    }

    @Override
    public long countByUnion(ClanId clanId, UUID unionId) {
        return live(clanId).filter(edge -> unionId.equals(edge.unionId())).count();
    }

    @Override
    public Parentage save(Parentage parentage) {
        rows.put(parentage.id(), parentage);
        return parentage;
    }

    private Stream<Parentage> live(ClanId clanId) {
        return rows.values().stream()
                .filter(edge -> edge.clanId().equals(clanId))
                .filter(edge -> !edge.isDeleted());
    }
}
