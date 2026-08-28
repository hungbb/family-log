package app.giaky.lineage.domain;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * <b>Keeps {@code person_ancestry} true.</b> B4.5, and the phase's main risk (R3).
 *
 * <p>The closure is what makes "is X below Y?" a single indexed lookup, which is what makes {@code
 * SUBTREE} scope affordable on the hot path of every authorization decision (doc 04 §3). The price
 * of that is this class: three operations that must run in the same transaction as the edge change
 * that provoked them, and must produce exactly what a rebuild from the edges would.
 *
 * <p><b>Why a class and not a listener.</b> Doc 02 §6 puts the maintenance inside the mutation's
 * transaction, and doc 03 §3.10 repeats it. An {@code @EventListener} — even a synchronous one —
 * invites an {@code AFTER_COMMIT} phase later, and the window that opens is one where the resolver
 * reads an ancestry the tree no longer has. The failure mode is somebody editing a branch they were
 * never granted, and it would show up as an intermittent authorization bug, which is the worst kind
 * to find.
 *
 * <p><b>Why it is this thin.</b> Every method is one statement in {@link AncestryRepository}, and
 * the set logic lives in SQL (V9). Doing the cross product in Java would mean loading two ancestor
 * sets into memory to write their product back out — more code, more round trips, and a
 * non-atomic version of a statement the database does atomically. What this class adds is the
 * <i>protocol</i>: which operation follows which change, stated once, so no call site has to
 * remember.
 *
 * <p>Held to ≥ 95 % line coverage plus the property test (doc 09 §4.4).
 */
public final class AncestryProjection {

    private final AncestryRepository ancestry;

    public AncestryProjection(AncestryRepository ancestry) {
        this.ancestry = Objects.requireNonNull(ancestry, "ancestry must not be null");
    }

    /**
     * A person joined the clan's tree.
     *
     * <p>Writes the depth-0 self-row. Easy to think of as optional — a person with no edges has no
     * ancestry — but it is the row that makes a subtree grant cover the person it names, and the row
     * every cross product is built from. Without it, adding an edge to this person later would
     * insert nothing at all.
     */
    public void personCreated(ClanId clanId, PersonId personId) {
        ancestry.insertSelfRow(clanId, personId);
    }

    /**
     * An edge was added: every ancestor of {@code parent} is now an ancestor of every descendant of
     * {@code child} (doc 03 §3.10).
     *
     * <p>Call <i>after</i> the edge is persisted and <i>after</i> {@link
     * LineageGraphService#requireNoCycle} has passed. Order matters for the second one: the cross
     * product of a cycle is unbounded, and the check is the thing that makes this statement finite.
     */
    public void parentageAdded(ClanId clanId, PersonId parentId, PersonId childId) {
        ancestry.addEdge(clanId, parentId, childId);
    }

    /**
     * An edge was removed: the subtree below {@code child} is recomputed.
     *
     * <p>Subtraction is not the inverse of the cross product — a pair may still be connected by a
     * second path, and a diamond is exactly that case — so removal recomputes rather than deletes.
     * Bounded by the subtree, because nothing above it can have changed and nothing inside it can
     * have used the removed edge.
     *
     * <p>Call <i>after</i> the edge is soft-deleted, or the recompute will find it and put the
     * ancestry straight back.
     */
    public void parentageRemoved(ClanId clanId, PersonId childId) {
        ancestry.recomputeFrom(clanId, childId);
    }

    /** The repair path of doc 03 §3.10, and the reference the property test compares against. */
    public long rebuild(ClanId clanId) {
        return ancestry.rebuild(clanId);
    }

    /** Whether {@code candidate} is at or below {@code ancestorId} — {@code SUBTREE} coverage. */
    public boolean covers(ClanId clanId, PersonId ancestorId, PersonId candidate) {
        return ancestry.covers(clanId, ancestorId, candidate);
    }

    /** Everyone at or below {@code ancestorId}, that person included. */
    public Set<PersonId> descendantsOf(ClanId clanId, PersonId ancestorId) {
        return ancestry.descendantsOf(clanId, ancestorId);
    }

    /** Everyone at or below {@code ancestorId} within {@code maxDepth} generations. */
    public Set<PersonId> descendantsOf(ClanId clanId, PersonId ancestorId, int maxDepth) {
        return ancestry.descendantsOf(clanId, ancestorId, maxDepth);
    }

    /** Everyone at or above {@code personId}, nearest first — the breadcrumb chain. */
    public List<AncestryRepository.AncestorLink> ancestorsOf(ClanId clanId, PersonId personId) {
        return ancestry.ancestorsOf(clanId, personId);
    }
}
