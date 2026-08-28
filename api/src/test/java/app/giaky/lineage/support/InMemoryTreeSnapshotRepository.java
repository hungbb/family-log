package app.giaky.lineage.support;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.lineage.domain.Parentage;
import app.giaky.lineage.domain.Person;
import app.giaky.lineage.domain.TreeSnapshotRepository;
import app.giaky.lineage.domain.Union;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The flat tree read, over the in-memory repositories (B5.2).
 *
 * <p>Projects the same three lists {@code TreeSnapshotJdbcAdapter} selects, from the aggregates the
 * other in-memory repositories already hold. Deliberately re-applies the rule the SQL joins express
 * — an edge is returned only when both of its endpoints are live and inside the requested set —
 * because that rule is the one a unit test can get wrong quietly: soft-deleting a person leaves
 * their edges alone (I-P7), and an edge with a missing end draws a line into empty space.
 */
public final class InMemoryTreeSnapshotRepository implements TreeSnapshotRepository {

    private final InMemoryPersonRepository persons;
    private final InMemoryParentageRepository parentages;
    private final InMemoryUnionRepository unions;

    public InMemoryTreeSnapshotRepository(
            InMemoryPersonRepository persons,
            InMemoryParentageRepository parentages,
            InMemoryUnionRepository unions) {

        this.persons = persons;
        this.parentages = parentages;
        this.unions = unions;
    }

    @Override
    public TreeSnapshot ofClan(ClanId clanId) {
        return snapshot(clanId, persons.findAllByClan(clanId));
    }

    @Override
    public TreeSnapshot ofPersons(ClanId clanId, Collection<PersonId> personIds) {
        if (personIds.isEmpty()) {
            return TreeSnapshot.empty();
        }
        return snapshot(clanId, persons.findAllByIds(clanId, personIds));
    }

    private TreeSnapshot snapshot(ClanId clanId, List<Person> people) {
        Set<UUID> included = new HashSet<>();
        for (Person person : people) {
            included.add(person.id().value());
        }

        List<EdgeRow> edges =
                parentages.findAllByClan(clanId).stream()
                        .filter(
                                edge ->
                                        included.contains(edge.parentPersonId().value())
                                                && included.contains(edge.childPersonId().value()))
                        .map(InMemoryTreeSnapshotRepository::edge)
                        .toList();

        List<UnionRow> marriages =
                unions.findAllByClan(clanId).stream()
                        .filter(
                                union ->
                                        included.contains(union.partnerAId().value())
                                                && included.contains(union.partnerBId().value()))
                        .map(InMemoryTreeSnapshotRepository::union)
                        .toList();

        return new TreeSnapshot(people.stream().map(InMemoryTreeSnapshotRepository::node).toList(),
                edges,
                marriages);
    }

    private static NodeRow node(Person person) {
        return new NodeRow(
                person.id().value(),
                person.clanId().value(),
                person.branchId() == null ? null : person.branchId().value(),
                person.fullName(),
                person.name().alias(),
                person.gender(),
                person.lifeStatus(),
                person.birth(),
                person.death(),
                person.generation(),
                person.birthOrder(),
                person.avatarMediaId(),
                person.linkedUserId() == null ? null : person.linkedUserId().value(),
                person.foundingAncestor());
    }

    private static EdgeRow edge(Parentage parentage) {
        return new EdgeRow(
                parentage.id(),
                parentage.parentPersonId().value(),
                parentage.childPersonId().value(),
                parentage.parentRole(),
                parentage.nature(),
                parentage.unionId());
    }

    private static UnionRow union(Union union) {
        return new UnionRow(
                union.id(),
                union.partnerAId().value(),
                union.partnerBId().value(),
                union.unionType(),
                union.status(),
                union.startDate(),
                union.endDate(),
                union.notes());
    }
}
