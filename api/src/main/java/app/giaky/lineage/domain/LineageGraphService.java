package app.giaky.lineage.domain;

import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The two graph rules that need to see more than one row: cycles and generations (B4.4).
 *
 * <p>{@link Parentage} enforces what a single edge can be checked for; this enforces what the graph
 * must be. Both live in the domain and neither knows about Spring, so the whole of I-R4 and I-R5 is
 * unit-testable against in-memory repositories (doc 09 §4.2).
 */
public final class LineageGraphService {

    /**
     * How far a generation cascade will walk before it decides the graph is corrupt.
     *
     * <p>{@link #requireNoCycle} makes a cycle impossible through the API, so reaching this means
     * data got in another way — a restore, a migration, a bug. Failing loudly at a bound beats
     * spinning inside a transaction that holds row locks.
     */
    private static final int MAX_CASCADE_DEPTH = 64;

    private final PersonRepository persons;
    private final ParentageRepository parentages;
    private final AncestryProjection ancestry;

    public LineageGraphService(
            PersonRepository persons,
            ParentageRepository parentages,
            AncestryProjection ancestry) {

        this.persons = Objects.requireNonNull(persons, "persons must not be null");
        this.parentages = Objects.requireNonNull(parentages, "parentages must not be null");
        this.ancestry = Objects.requireNonNull(ancestry, "ancestry must not be null");
    }

    /**
     * Refuses an edge that would close a loop (I-R4).
     *
     * <p>One indexed lookup, in the direction that answers the question: the new edge runs {@code
     * parent → child}, so a cycle exists exactly when {@code child} already reaches {@code parent}.
     * The closure's self-row makes the degenerate case — somebody made their own parent — fall out of
     * the same check rather than needing its own.
     *
     * <p>Called <i>before</i> the edge is written. Afterwards the closure maintenance would already
     * be enumerating an infinite set.
     *
     * @throws DomainRuleViolation {@code 422 CYCLE_DETECTED} (doc 05 §2)
     */
    public void requireNoCycle(ClanId clanId, PersonId parentId, PersonId childId) {
        if (ancestry.covers(clanId, childId, parentId)) {
            throw new DomainRuleViolation(
                    ErrorCode.CYCLE_DETECTED,
                    "That would make somebody their own ancestor: the person you chose as the parent"
                            + " is already a descendant of the child.");
        }
    }

    /**
     * Recomputes <i>đời</i> for a person and everyone below them (I-R5).
     *
     * <p>Run after any change to the edges into {@code from} — an edge added, an edge removed, or a
     * parent's own generation restamped. The walk is breadth-first over children, and a node whose
     * recomputed value is unchanged stops the cascade there, so re-parenting a leaf costs one row
     * even in a large tree.
     *
     * <p><b>The rule.</b> A person's generation is one more than the <i>smallest</i> known
     * generation among their live parents, and null when no parent has one. Smallest, not largest,
     * because <i>đời</i> counts distance from the founding ancestor and the closure measures that
     * same distance the same way — a person whose father is đời 5 and whose mother married in from
     * đời 8 is đời 6 in the book. The founding ancestor is đời 1 by definition and is never
     * recomputed, because nothing sits above them to compute from.
     *
     * <p>Null is a real answer and is written as one. An imported fragment with no path to the
     * founder has no <i>đời</i>, and inventing 1 for it would place a stranger at the head of the
     * family (phase 4 §8).
     *
     * @return how many people had their generation changed
     */
    public int recomputeGenerationsFrom(ClanId clanId, PersonId from, Instant now) {
        Map<PersonId, Person> loaded = new HashMap<>();
        Map<PersonId, Integer> assigned = new HashMap<>();
        List<PersonRepository.GenerationAssignment> changes = new ArrayList<>();

        Deque<PersonId> queue = new ArrayDeque<>();
        Set<PersonId> queued = new HashSet<>();
        queue.add(from);
        queued.add(from);

        int steps = 0;
        while (!queue.isEmpty()) {
            if (++steps > MAX_CASCADE_DEPTH * MAX_CASCADE_DEPTH) {
                throw new DomainRuleViolation(
                        ErrorCode.CYCLE_DETECTED,
                        "The family tree below this person does not terminate; the link that caused"
                                + " it has to be removed before generations can be recomputed.");
            }
            PersonId current = queue.removeFirst();
            Person person = load(clanId, current, loaded);
            if (person == null) {
                continue;
            }

            // An if rather than a ternary, and not for style: a conditional expression whose two
            // arms are int and Integer unboxes the Integer one, so "no known generation" would
            // become a NullPointerException instead of the answer it is.
            Integer recomputed;
            if (person.foundingAncestor()) {
                recomputed = Person.FIRST_GENERATION;
            } else {
                recomputed = generationFromParents(clanId, current, loaded, assigned);
            }

            assigned.put(current, recomputed);
            if (Objects.equals(person.generation(), recomputed)) {
                // Nothing below can have changed either: a child's generation is a function of its
                // parents', and this one did not move.
                continue;
            }
            changes.add(new PersonRepository.GenerationAssignment(current, recomputed));

            for (Parentage edge : parentages.findByParent(clanId, current)) {
                if (queued.add(edge.childPersonId())) {
                    queue.addLast(edge.childPersonId());
                }
            }
        }

        if (changes.isEmpty()) {
            return 0;
        }
        return persons.updateGenerations(clanId, changes, now);
    }

    /**
     * One more than the closest known parent generation, or null.
     *
     * <p>Reads {@code assigned} before the stored value, so a cascade uses the number it just
     * computed for the parent rather than the one still on the row — the two differ for exactly the
     * duration of a cascade, which is when this is called.
     */
    private Integer generationFromParents(
            ClanId clanId,
            PersonId childId,
            Map<PersonId, Person> loaded,
            Map<PersonId, Integer> assigned) {

        Integer best = null;
        for (Parentage edge : parentages.findByChild(clanId, childId)) {
            PersonId parentId = edge.parentPersonId();
            Integer parentGeneration;
            if (assigned.containsKey(parentId)) {
                parentGeneration = assigned.get(parentId);
            } else {
                Person parent = load(clanId, parentId, loaded);
                parentGeneration = parent == null ? null : parent.generation();
            }
            if (parentGeneration != null && (best == null || parentGeneration < best)) {
                best = parentGeneration;
            }
        }
        return best == null ? null : best + 1;
    }

    /**
     * Everyone directly below {@code personId}, in birth order where the book gives one.
     *
     * <p>Ordering here rather than in the repository because "children" is a domain concept with a
     * domain order: <i>thứ tự sinh</i> first, then birth date, then name — and a node with none of
     * those still has to land somewhere stable so two renders agree.
     */
    public List<Person> childrenOf(ClanId clanId, PersonId personId) {
        List<PersonId> childIds =
                parentages.findByParent(clanId, personId).stream()
                        .map(Parentage::childPersonId)
                        .distinct()
                        .toList();
        List<Person> children = new ArrayList<>(persons.findAllByIds(clanId, childIds));
        children.sort(LineageGraphService::bySiblingOrder);
        return children;
    }

    /** The live parent edges into a person — at most one father and one mother (I-R3). */
    public List<Parentage> parentEdgesOf(ClanId clanId, PersonId personId) {
        return parentages.findByChild(clanId, personId);
    }

    /**
     * Siblings: everyone who shares at least one parent, excluding the person themselves.
     *
     * <p>"At least one" is the right rule for a <i>gia phả</i> with several wives — the children of
     * a second wife are half-siblings and the book lists them together, under the same father.
     */
    public List<Person> siblingsOf(ClanId clanId, PersonId personId) {
        Set<PersonId> siblingIds = new HashSet<>();
        for (Parentage edge : parentages.findByChild(clanId, personId)) {
            for (Parentage childEdge : parentages.findByParent(clanId, edge.parentPersonId())) {
                if (!childEdge.childPersonId().equals(personId)) {
                    siblingIds.add(childEdge.childPersonId());
                }
            }
        }
        List<Person> siblings = new ArrayList<>(persons.findAllByIds(clanId, siblingIds));
        siblings.sort(LineageGraphService::bySiblingOrder);
        return siblings;
    }

    /** Whether removing this person would leave children with no other recorded parent. */
    public List<Person> childrenLeftWithoutAnotherParent(ClanId clanId, PersonId personId) {
        List<Person> orphaned = new ArrayList<>();
        for (Person child : childrenOf(clanId, personId)) {
            boolean hasAnother =
                    parentages.findByChild(clanId, child.id()).stream()
                            .anyMatch(edge -> !edge.parentPersonId().equals(personId));
            if (!hasAnother) {
                orphaned.add(child);
            }
        }
        return orphaned;
    }

    private Person load(ClanId clanId, PersonId id, Map<PersonId, Person> cache) {
        // computeIfAbsent would store nulls badly and re-query a missing person on every visit; a
        // containsKey check keeps one lookup per person even when the answer is "not there".
        if (cache.containsKey(id)) {
            return cache.get(id);
        }
        Person person = persons.findById(clanId, id).orElse(null);
        cache.put(id, person);
        return person;
    }

    /**
     * <i>Thứ tự sinh</i> first, then birth date, then name.
     *
     * <p>People with no birth order sort after those who have one: a book that numbered its sons
     * meant the numbering, and a sibling added later without a number should not displace them.
     */
    private static int bySiblingOrder(Person left, Person right) {
        int byOrder = compareNullsLast(left.birthOrder(), right.birthOrder());
        if (byOrder != 0) {
            return byOrder;
        }
        int byBirth =
                compareNullsLast(
                        left.birth().isKnown() ? left.birth().earliestOrdinal() : null,
                        right.birth().isKnown() ? right.birth().earliestOrdinal() : null);
        if (byBirth != 0) {
            return byBirth;
        }
        return left.fullName().compareTo(right.fullName());
    }

    private static int compareNullsLast(Integer left, Integer right) {
        if (Objects.equals(left, right)) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return Integer.compare(left, right);
    }
}
