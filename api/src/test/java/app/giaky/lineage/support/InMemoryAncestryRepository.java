package app.giaky.lineage.support;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.lineage.domain.AncestryRepository;
import app.giaky.lineage.domain.Parentage;
import app.giaky.lineage.domain.Person;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ancestry closure, computed from the edges every time it is asked.
 *
 * <p><b>Deliberately not incremental.</b> The production adapter maintains the closure with a cross
 * product on insert and a bounded recompute on removal, and that incremental path is the thing risk
 * R3 is about — so a test double that reimplemented it would be a second incremental implementation,
 * and the unit tests would be checking that two versions of the same idea agree with each other.
 *
 * <p>This one is correct by construction instead: {@link #addEdge} and {@link #recomputeFrom} both
 * just recompute, so the unit tests can exercise service logic — placement, cycles, generations,
 * permissions — against an oracle. The incremental SQL is tested where it lives, in {@code
 * AncestryProjectionPropertyIT}, against a real database and against {@code
 * rebuild_person_ancestry}.
 */
public final class InMemoryAncestryRepository implements AncestryRepository {

    private final InMemoryPersonRepository persons;
    private final InMemoryParentageRepository parentages;

    /** ancestor → descendant → shortest depth, per clan. */
    private final Map<ClanId, Map<PersonId, Map<PersonId, Integer>>> closure = new HashMap<>();

    public InMemoryAncestryRepository(
            InMemoryPersonRepository persons, InMemoryParentageRepository parentages) {
        this.persons = persons;
        this.parentages = parentages;
    }

    @Override
    public boolean covers(ClanId clanId, PersonId ancestor, PersonId candidate) {
        return rows(clanId).getOrDefault(ancestor, Map.of()).containsKey(candidate);
    }

    @Override
    public Set<PersonId> descendantsOf(ClanId clanId, PersonId ancestor) {
        return new LinkedHashSet<>(rows(clanId).getOrDefault(ancestor, Map.of()).keySet());
    }

    @Override
    public Set<PersonId> descendantsOf(ClanId clanId, PersonId ancestor, int maxDepth) {
        Set<PersonId> within = new LinkedHashSet<>();
        rows(clanId)
                .getOrDefault(ancestor, Map.of())
                .forEach(
                        (descendant, depth) -> {
                            if (depth <= maxDepth) {
                                within.add(descendant);
                            }
                        });
        return within;
    }

    @Override
    public List<AncestorLink> ancestorsOf(ClanId clanId, PersonId descendant) {
        List<AncestorLink> links = new ArrayList<>();
        rows(clanId)
                .forEach(
                        (ancestor, descendants) -> {
                            Integer depth = descendants.get(descendant);
                            if (depth != null) {
                                links.add(new AncestorLink(ancestor, depth));
                            }
                        });
        links.sort(java.util.Comparator.comparingInt(AncestorLink::depth));
        return links;
    }

    @Override
    public void insertSelfRow(ClanId clanId, PersonId personId) {
        rows(clanId).computeIfAbsent(personId, key -> new LinkedHashMap<>()).put(personId, 0);
    }

    @Override
    public void addEdge(ClanId clanId, PersonId parentId, PersonId childId) {
        rebuild(clanId);
    }

    @Override
    public void recomputeFrom(ClanId clanId, PersonId personId) {
        rebuild(clanId);
    }

    @Override
    public long rebuild(ClanId clanId) {
        Map<PersonId, Map<PersonId, Integer>> rebuilt = new LinkedHashMap<>();

        // A self-row for everybody, deleted or not — the closure describes the edge graph, not what
        // is visible, which is what makes restoring a person a timestamp rather than a repair.
        for (Person person : persons.all()) {
            if (person.clanId().equals(clanId)) {
                rebuilt.computeIfAbsent(person.id(), key -> new LinkedHashMap<>())
                        .put(person.id(), 0);
            }
        }

        Map<PersonId, List<PersonId>> childrenOf = new HashMap<>();
        for (Parentage edge : parentages.findAllByClan(clanId)) {
            childrenOf
                    .computeIfAbsent(edge.parentPersonId(), key -> new ArrayList<>())
                    .add(edge.childPersonId());
        }

        // Breadth-first from every person, taking the shortest depth to each descendant — the same
        // semantics rebuild_person_ancestry() has in V9.
        long total = 0;
        for (PersonId ancestor : List.copyOf(rebuilt.keySet())) {
            Map<PersonId, Integer> reachable = rebuilt.get(ancestor);
            Deque<PersonId> frontier = new ArrayDeque<>();
            frontier.add(ancestor);
            while (!frontier.isEmpty()) {
                PersonId current = frontier.removeFirst();
                int depth = reachable.get(current);
                for (PersonId child : childrenOf.getOrDefault(current, List.of())) {
                    if (!reachable.containsKey(child)) {
                        reachable.put(child, depth + 1);
                        frontier.addLast(child);
                    }
                }
            }
            total += reachable.size();
        }

        closure.put(clanId, rebuilt);
        return total;
    }

    private Map<PersonId, Map<PersonId, Integer>> rows(ClanId clanId) {
        return closure.computeIfAbsent(clanId, key -> new LinkedHashMap<>());
    }
}
