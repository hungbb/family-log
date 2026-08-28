package app.giaky.authorization.support;

import app.giaky.authorization.domain.LineageAncestryPort;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A closure table in a {@code Map}, for the tests that need {@code SUBTREE} scope without a database.
 *
 * <p>Phase 3 had no implementation of this port at all — {@code DeferredLineageAncestryAdapter}
 * answered "not available" and threw for everything else — so the ClanOrg and Authorization unit
 * tests could ignore lineage entirely. Phase 4 turns the scope on, and those tests now need
 * something that answers. This is it: parent → child edges in, transitive reachability out, no
 * Spring and no Postgres.
 *
 * <p>Deliberately naive. It walks the edges on every question rather than materializing a closure,
 * because a fast implementation here would be a second implementation of the thing the property test
 * exists to check, and the two would eventually disagree in the tests' favour.
 */
public final class InMemoryLineageAncestryPort implements LineageAncestryPort {

    private final Map<UUID, Set<UUID>> childrenOf = new HashMap<>();
    private final Map<UUID, BranchId> placements = new HashMap<>();
    private final Map<UUID, String> displayNames = new HashMap<>();
    private final Set<UUID> people = new LinkedHashSet<>();
    private boolean available = true;
    private LineageAncestryPort delegate;

    /**
     * Hands every question to a real lineage module instead of the maps above.
     *
     * <p>The Lineage fixtures need the resolver to answer {@code SUBTREE} questions from the same
     * closure their services are writing to, and the resolver takes its port at construction time —
     * which happens in a field initializer, before those fixtures exist. A swappable delegate is the
     * small price of keeping one wiring for both, instead of a second copy of the whole
     * ClanOrg-plus-Authorization graph in the lineage tests.
     */
    public void backedBy(LineageAncestryPort target) {
        this.delegate = target;
    }

    /** Records a person, with the branch they sit in. A null branch means unplaced. */
    public InMemoryLineageAncestryPort withPerson(UUID personId, BranchId branchId) {
        return withPerson(personId, branchId, null);
    }

    /** A person with a name, for the tests that assert on a grant label. */
    public InMemoryLineageAncestryPort withPerson(UUID personId, BranchId branchId, String name) {
        people.add(personId);
        if (name != null) {
            displayNames.put(personId, name);
        }
        if (branchId != null) {
            placements.put(personId, branchId);
        }
        return this;
    }

    /** Records a parent → child edge. Both people are implied to exist. */
    public InMemoryLineageAncestryPort withEdge(UUID parentId, UUID childId) {
        people.add(parentId);
        people.add(childId);
        childrenOf.computeIfAbsent(parentId, key -> new LinkedHashSet<>()).add(childId);
        return this;
    }

    /** Simulates a deployment with no lineage schema, which every caller must fail closed on. */
    public InMemoryLineageAncestryPort unavailable() {
        this.available = false;
        return this;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public boolean personExists(ClanId clan, UUID personId) {
        return delegate != null ? delegate.personExists(clan, personId) : people.contains(personId);
    }

    @Override
    public boolean covers(ClanId clan, UUID ancestor, UUID candidate) {
        return delegate != null
                ? delegate.covers(clan, ancestor, candidate)
                : descendantsOf(clan, ancestor).contains(candidate);
    }

    /** Reflexive, matching the depth-0 self-row the real closure carries. */
    @Override
    public Set<UUID> descendantsOf(ClanId clan, UUID ancestor) {
        if (delegate != null) {
            return delegate.descendantsOf(clan, ancestor);
        }
        Set<UUID> found = new LinkedHashSet<>();
        collect(ancestor, found);
        return found;
    }

    @Override
    public Map<UUID, String> displayNames(ClanId clan, Collection<UUID> personIds) {
        if (delegate != null) {
            return delegate.displayNames(clan, personIds);
        }
        Map<UUID, String> names = new LinkedHashMap<>();
        for (UUID personId : personIds) {
            if (people.contains(personId)) {
                names.put(personId, displayNames.getOrDefault(personId, personId.toString()));
            }
        }
        return names;
    }

    @Override
    public Optional<BranchId> placementOf(ClanId clan, UUID personId) {
        return delegate != null
                ? delegate.placementOf(clan, personId)
                : Optional.ofNullable(placements.get(personId));
    }

    private void collect(UUID personId, Set<UUID> sink) {
        // The add() guards the recursion: a cycle the production code refuses could still be built
        // here by hand, and a test that hangs is a test nobody diagnoses.
        if (!sink.add(personId)) {
            return;
        }
        for (UUID child : childrenOf.getOrDefault(personId, Set.of())) {
            collect(child, sink);
        }
    }
}
