package app.giaky.lineage.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code parentages}. Clan-scoped throughout. */
public interface ParentageJpaRepository extends JpaRepository<ParentageJpaEntity, UUID> {

    Optional<ParentageJpaEntity> findByClanIdAndIdAndDeletedAtIsNull(ClanId clanId, UUID id);

    List<ParentageJpaEntity> findByClanIdAndChildPersonIdAndDeletedAtIsNull(
            ClanId clanId, PersonId childPersonId);

    List<ParentageJpaEntity> findByClanIdAndParentPersonIdAndDeletedAtIsNull(
            ClanId clanId, PersonId parentPersonId);

    List<ParentageJpaEntity> findByClanIdAndDeletedAtIsNull(ClanId clanId);

    List<ParentageJpaEntity> findByClanIdAndChildPersonIdInAndDeletedAtIsNull(
            ClanId clanId, Collection<PersonId> childPersonIds);

    /** The I-R3 pre-check: does this child already have a parent in this role? */
    Optional<ParentageJpaEntity> findByClanIdAndChildPersonIdAndParentRoleAndDeletedAtIsNull(
            ClanId clanId, PersonId childPersonId, String parentRole);

    /** The guard on deleting a union (doc 05 §4.8). */
    List<ParentageJpaEntity> findByClanIdAndUnionIdAndDeletedAtIsNull(ClanId clanId, UUID unionId);
}
