package app.giaky.lineage.domain;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage for {@link Union} rows. Clan-scoped throughout. */
public interface UnionRepository {

    Optional<Union> findById(ClanId clanId, UUID id);

    /** Every live union one person is part of, in either position. */
    List<Union> findByPartner(ClanId clanId, PersonId personId);

    /**
     * The live union between two people, in either order.
     *
     * <p>The pre-check behind {@code ux_union_pair} (I-N2). The caller passes the pair as the user
     * gave it; the implementation canonicalizes, so no call site has to remember to.
     */
    Optional<Union> findByPair(ClanId clanId, PersonId onePartner, PersonId otherPartner);

    /** Every live union of a clan — the tree read's third query. */
    List<Union> findAllByClan(ClanId clanId);

    /** Live unions touching any of {@code personIds}, for a subtree read. */
    List<Union> findByPartners(ClanId clanId, Collection<PersonId> personIds);

    Union save(Union union);
}
