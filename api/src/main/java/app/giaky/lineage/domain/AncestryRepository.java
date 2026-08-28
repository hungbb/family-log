package app.giaky.lineage.domain;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import java.util.List;
import java.util.Set;

/**
 * The {@code person_ancestry} closure table (doc 03 §3.10), as the domain sees it.
 *
 * <p>Every method here runs <b>inside the transaction that changed the edge</b>. That is not a
 * performance note, it is the whole design: doc 02 §6 forbids an asynchronous projection, because a
 * window in which the closure disagrees with the tree is a window in which authorization answers
 * "yes" for a node somebody was never granted. Risk R3 in the roadmap is this table drifting, and
 * the property test of doc 09 §4.5 is the gate on it.
 *
 * <p>Implemented in native SQL rather than JPA (doc 09 §2.3): a cross-product insert and a bounded
 * recompute are set operations, and expressing them through an entity graph would turn each into a
 * read-modify-write loop over rows nobody wants in memory.
 */
public interface AncestryRepository {

    /**
     * Whether {@code candidate} is {@code ancestor} or one of their descendants.
     *
     * <p>Reflexive, because of the depth-0 self-row: a subtree grant covers the person it names
     * (doc 04 §3.1).
     */
    boolean covers(ClanId clanId, PersonId ancestor, PersonId candidate);

    /** Everyone at or below {@code ancestor}, the person included. One query per subtree grant. */
    Set<PersonId> descendantsOf(ClanId clanId, PersonId ancestor);

    /** Everyone at or below {@code ancestor} within {@code maxDepth} generations, inclusive. */
    Set<PersonId> descendantsOf(ClanId clanId, PersonId ancestor, int maxDepth);

    /** Everyone at or above {@code descendant}, the person included — breadcrumbs (doc 05 §4.9). */
    List<AncestorLink> ancestorsOf(ClanId clanId, PersonId descendant);

    /**
     * Records a new person as their own ancestor at depth 0.
     *
     * <p>Called by the create path before any edge exists. Without it a childless, parentless person
     * would be absent from the closure entirely, and a subtree grant naming them would cover nobody
     * — including themselves.
     */
    void insertSelfRow(ClanId clanId, PersonId personId);

    /**
     * Extends the closure for a new edge {@code parent → child} (doc 03 §3.10).
     *
     * <p>The cross product {@code ancestors(parent) ∪ {parent}} × {@code descendants(child) ∪
     * {child}}, in one statement. Depths add through the new edge, and a pair that was already
     * connected by a longer path keeps the shorter of the two.
     */
    void addEdge(ClanId clanId, PersonId parentId, PersonId childId);

    /**
     * Repairs the closure below {@code personId} after an edge above it was removed.
     *
     * <p>Bounded by the subtree rather than the clan: rows wholly inside the subtree cannot have
     * used the removed edge, so only what reached in from outside is recomputed. See {@code
     * recompute_person_ancestry_from} in V9.
     */
    void recomputeFrom(ClanId clanId, PersonId personId);

    /**
     * Recomputes the whole clan's closure from the edges.
     *
     * <p>The repair tool of doc 03 §3.10, and the reference the property test compares incremental
     * maintenance against.
     *
     * @return how many rows the rebuilt closure holds
     */
    long rebuild(ClanId clanId);

    /** One row of {@link #ancestorsOf}: an ancestor and how many generations up they sit. */
    record AncestorLink(PersonId ancestorId, int depth) {}
}
