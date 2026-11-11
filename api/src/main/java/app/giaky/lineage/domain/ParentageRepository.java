package app.giaky.lineage.domain;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage for {@link Parentage} edges. Clan-scoped throughout, like every repository here. */
public interface ParentageRepository {

    Optional<Parentage> findById(ClanId clanId, UUID id);

    /** The live edges into a person — at most two, one per {@link ParentRole} (I-R3). */
    List<Parentage> findByChild(ClanId clanId, PersonId childId);

    /** The live edges out of a person: their children. */
    List<Parentage> findByParent(ClanId clanId, PersonId parentId);

    /** Every live edge of a clan. The master tree read fetches these in one go (doc 03 §4). */
    List<Parentage> findAllByClan(ClanId clanId);

    /** The live edges whose child is in {@code personIds} — the subtree read's second query. */
    List<Parentage> findByChildren(ClanId clanId, Collection<PersonId> personIds);

    /** The existing edge for this (child, role), if any. The pre-check behind I-R3's index. */
    Optional<Parentage> findEdge(ClanId clanId, PersonId childId, ParentRole role);

    /**
     * Live edges that were attached through a union — the guard on deleting one (doc 05 §4.8).
     *
     * <p>Includes edges whose child has since been soft-deleted: a person's edges outlive them so
     * that a restore has nothing to rebuild, and the caller decides what those edges mean.
     */
    List<Parentage> findByUnion(ClanId clanId, UUID unionId);

    Parentage save(Parentage parentage);
}
