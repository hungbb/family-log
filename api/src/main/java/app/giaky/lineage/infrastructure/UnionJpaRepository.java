package app.giaky.lineage.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code unions}. Clan-scoped throughout.
 *
 * <p>The "either partner" reads are written as JPQL rather than derived from a method name. A
 * derived {@code ...PartnerAIdOrClanIdAndPartnerBIdAndDeletedAtIsNull} parses as {@code (clan AND a)
 * OR (clan AND b AND not deleted)} — the soft-delete predicate binds to the second arm only, so
 * removed marriages come back through the first. That is the kind of bug a method name hides and a
 * {@code WHERE} clause states.
 */
public interface UnionJpaRepository extends JpaRepository<UnionJpaEntity, UUID> {

    Optional<UnionJpaEntity> findByClanIdAndIdAndDeletedAtIsNull(ClanId clanId, UUID id);

    /** One person's marriages, from either side of the canonical pair. */
    @Query(
            """
            SELECT u FROM UnionJpaEntity u
            WHERE u.clanId = :clanId
              AND u.deletedAt IS NULL
              AND (u.partnerAId = :personId OR u.partnerBId = :personId)
            """)
    List<UnionJpaEntity> findByPartner(
            @Param("clanId") ClanId clanId, @Param("personId") PersonId personId);

    List<UnionJpaEntity> findByClanIdAndDeletedAtIsNull(ClanId clanId);

    /** Every marriage touching any of these people — the subtree read's third query. */
    @Query(
            """
            SELECT u FROM UnionJpaEntity u
            WHERE u.clanId = :clanId
              AND u.deletedAt IS NULL
              AND (u.partnerAId IN :personIds OR u.partnerBId IN :personIds)
            """)
    List<UnionJpaEntity> findByPartners(
            @Param("clanId") ClanId clanId, @Param("personIds") Collection<PersonId> personIds);

    /** The I-N2 pre-check. The adapter canonicalizes the pair before calling. */
    Optional<UnionJpaEntity> findByClanIdAndPartnerAIdAndPartnerBIdAndDeletedAtIsNull(
            ClanId clanId, PersonId partnerAId, PersonId partnerBId);
}
