package app.giaky.lineage.infrastructure;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.TenantExempt;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code persons}.
 *
 * <p>Every finder takes a {@link ClanId}. A person id arrives from a URL and this parameter is the
 * whole difference between a 404 and another family's private records; {@code TenancyBoundaryIT}
 * substitutes ids across clans on each of these paths.
 */
public interface PersonJpaRepository extends JpaRepository<PersonJpaEntity, UUID> {

    Optional<PersonJpaEntity> findByClanIdAndIdAndDeletedAtIsNull(ClanId clanId, UUID id);

    /** The restore path's read: the row whether or not it is deleted. */
    Optional<PersonJpaEntity> findByClanIdAndId(ClanId clanId, UUID id);

    List<PersonJpaEntity> findByClanIdAndDeletedAtIsNull(ClanId clanId);

    List<PersonJpaEntity> findByClanIdAndIdInAndDeletedAtIsNull(ClanId clanId, Collection<UUID> ids);

    List<PersonJpaEntity> findByClanIdAndBranchIdAndDeletedAtIsNull(
            ClanId clanId, BranchId branchId);

    List<PersonJpaEntity> findByClanIdAndDeletedAtGreaterThanEqualOrderByDeletedAtDesc(
            ClanId clanId, Instant deletedSince);

    Optional<PersonJpaEntity> findByClanIdAndFoundingAncestorTrueAndDeletedAtIsNull(
            ClanId clanId);

    long countByClanIdAndDeletedAtIsNull(ClanId clanId);

    /**
     * Search (B4.11, doc 03 §6).
     *
     * <p>Two text strategies, OR-ed, because they fail in opposite directions. The {@code tsvector}
     * match is word-based and fast and finds "nguyen van an" in "Nguyễn Văn An" — but it will not
     * match "ngu" while somebody is still typing, and it will not survive a transposed letter. The
     * trigram {@code LIKE} does both and is the one that makes the search box feel alive. Both read
     * through {@code f_unaccent}, so the caller's diacritics — or absence of them — never matter.
     *
     * <p>Every optional filter is expressed as {@code CAST(:x AS …) IS NULL OR …}. The cast is not
     * decoration: PostgreSQL cannot infer a parameter's type from {@code IS NULL} alone and refuses
     * the statement without it.
     *
     * <p>Ordering is <i>đời</i>, then birth order, then name — the order a family book is written in,
     * so a result list reads like the book rather than like a database.
     */
    @TenantExempt(
            justification =
                    "Clan-scoped in the strongest sense — clan_id is the first predicate and the"
                        + " index it uses. The parameter is a raw UUID rather than a ClanId because"
                        + " a native query binds by JDBC type and never sees the AttributeConverter"
                        + " that maps the wrapper; the adapter above it takes a ClanId and unwraps"
                        + " it here.")
    @Query(
            nativeQuery = true,
            value =
                    """
                    SELECT p.*
                    FROM persons p
                    WHERE p.clan_id = :clanId
                      AND p.deleted_at IS NULL
                      AND (CAST(:text AS TEXT) IS NULL
                           OR p.name_search @@ plainto_tsquery('simple', f_unaccent(CAST(:text AS TEXT)))
                           OR f_unaccent(lower(p.full_name))
                              LIKE '%' || f_unaccent(lower(CAST(:text AS TEXT))) || '%')
                      AND (CAST(:generation AS INT) IS NULL OR p.generation = CAST(:generation AS INT))
                      AND (CAST(:lifeStatus AS TEXT) IS NULL OR p.life_status = CAST(:lifeStatus AS TEXT))
                      AND (CAST(:branchId AS UUID) IS NULL OR p.branch_id = CAST(:branchId AS UUID))
                      AND (CAST(:linked AS BOOLEAN) IS NULL
                           OR (p.linked_user_id IS NOT NULL) = CAST(:linked AS BOOLEAN))
                    ORDER BY p.generation NULLS LAST, p.birth_order NULLS LAST, p.full_name
                    """,
            countQuery =
                    """
                    SELECT count(*)
                    FROM persons p
                    WHERE p.clan_id = :clanId
                      AND p.deleted_at IS NULL
                      AND (CAST(:text AS TEXT) IS NULL
                           OR p.name_search @@ plainto_tsquery('simple', f_unaccent(CAST(:text AS TEXT)))
                           OR f_unaccent(lower(p.full_name))
                              LIKE '%' || f_unaccent(lower(CAST(:text AS TEXT))) || '%')
                      AND (CAST(:generation AS INT) IS NULL OR p.generation = CAST(:generation AS INT))
                      AND (CAST(:lifeStatus AS TEXT) IS NULL OR p.life_status = CAST(:lifeStatus AS TEXT))
                      AND (CAST(:branchId AS UUID) IS NULL OR p.branch_id = CAST(:branchId AS UUID))
                      AND (CAST(:linked AS BOOLEAN) IS NULL
                           OR (p.linked_user_id IS NOT NULL) = CAST(:linked AS BOOLEAN))
                    """)
    Page<PersonJpaEntity> search(
            @Param("clanId") UUID clanId,
            @Param("text") String text,
            @Param("generation") Integer generation,
            @Param("lifeStatus") String lifeStatus,
            @Param("branchId") UUID branchId,
            @Param("linked") Boolean linked,
            Pageable pageable);

    /**
     * Restamps <i>đời</i> for a batch of people in one statement (I-R5).
     *
     * <p>Ids and generations arrive as two comma-separated lists rather than as SQL arrays: array
     * binding through JPA depends on the driver and the dialect agreeing about a type that has no
     * portable JDBC representation, and {@code string_to_array} needs neither to agree about
     * anything. An empty generation position means "unknown", which is a real answer for an imported
     * fragment (phase 4 §8).
     *
     * <p>Deliberately does <b>not</b> touch {@code version}. Generation is derived and advisory —
     * nobody edits it in a form — so bumping the optimistic lock would answer an unrelated in-flight
     * edit with {@code 409 VERSION_CONFLICT} because somebody else added a cousin.
     *
     * <p>{@code clearAutomatically} because a bulk statement is invisible to the persistence context
     * that issued it, and the caller is about to read these rows back.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            nativeQuery = true,
            value =
                    """
                    UPDATE persons p
                    SET generation = CAST(NULLIF(data.generation, '') AS SMALLINT),
                        updated_at = :now
                    FROM (SELECT unnest(string_to_array(CAST(:ids AS TEXT), ','))         AS id,
                                 unnest(string_to_array(CAST(:generations AS TEXT), ',')) AS generation) data
                    WHERE p.id = CAST(data.id AS UUID)
                      AND p.clan_id = :clanId
                      AND p.deleted_at IS NULL
                    """)
    int updateGenerations(
            @Param("clanId") UUID clanId,
            @Param("ids") String ids,
            @Param("generations") String generations,
            @Param("now") Instant now);
}
