package app.giaky.lineage.infrastructure;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.TenantExempt;
import app.giaky.common.types.UserId;
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

    /** I-P4: the one node this account may hold in this clan (Phase 6). */
    Optional<PersonJpaEntity> findByClanIdAndLinkedUserIdAndDeletedAtIsNull(
            ClanId clanId, UserId linkedUserId);

    List<PersonJpaEntity> findByClanIdAndLinkedUserIdInAndDeletedAtIsNull(
            ClanId clanId, Collection<UserId> linkedUserIds);

    @TenantExempt(
            justification =
                    "Profile propagation spans every clan the account is linked in (FR-N6), so no"
                        + " ClanId exists to scope by. Scoped instead by the account id, which"
                        + " comes from the access token rather than from the request.")
    List<PersonJpaEntity> findByLinkedUserIdAndDeletedAtIsNull(UserId linkedUserId);

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

    // --- the link column (Phase 6) --------------------------------------------------------------

    /**
     * The compare-and-set behind {@code PersonUserLinker.link} (B6.11).
     *
     * <p>{@code linked_user_id IS NULL} in the {@code WHERE} clause is the entire concurrency
     * guarantee: whichever of two concurrent approvals reaches the row second updates nothing and
     * gets a zero back, which the service turns into {@code 409 NODE_ALREADY_LINKED}. Postgres
     * serializes the two statements on the row lock; nothing in the application has to.
     *
     * <p>Does not touch {@code version}, for the same reason {@link #updateGenerations} does not:
     * linking is not an edit to the genealogical record, and answering somebody's in-flight person
     * edit with a {@code 409 VERSION_CONFLICT} because a cousin created an account would be a
     * conflict about nothing.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            nativeQuery = true,
            value =
                    """
                    UPDATE persons
                    SET linked_user_id = :userId,
                        updated_by = :actor,
                        updated_at = :now
                    WHERE id = :personId
                      AND clan_id = :clanId
                      AND linked_user_id IS NULL
                      AND deleted_at IS NULL
                    """)
    int linkUserIfUnlinked(
            @Param("clanId") UUID clanId,
            @Param("personId") UUID personId,
            @Param("userId") UUID userId,
            @Param("actor") UUID actor,
            @Param("now") Instant now);

    /** The other half (FR-N5). Unconditional except for the tenancy and soft-delete predicates. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            nativeQuery = true,
            value =
                    """
                    UPDATE persons
                    SET linked_user_id = NULL,
                        updated_by = :actor,
                        updated_at = :now
                    WHERE id = :personId
                      AND clan_id = :clanId
                      AND linked_user_id IS NOT NULL
                      AND deleted_at IS NULL
                    """)
    int unlinkUser(
            @Param("clanId") UUID clanId,
            @Param("personId") UUID personId,
            @Param("actor") UUID actor,
            @Param("now") Instant now);

    /**
     * The self-service whitelist of doc 04 §6, expressed as columns (B6.7).
     *
     * <p>Two columns, named here and nowhere else. {@code linked_user_id = :linkedUserId} is what
     * makes this safe without a permission check: the statement can only reach the caller's own
     * node, and it can only write the two fields the whitelist names. Extending it is a schema-level
     * change somebody has to make on purpose — which is what phase 6 §8 asks for.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            nativeQuery = true,
            value =
                    """
                    UPDATE persons
                    SET avatar_media_id = CAST(:avatarMediaId AS UUID),
                        notes_html      = CAST(:notesHtml AS TEXT),
                        notes_text      = CAST(:notesText AS TEXT),
                        updated_by      = :linkedUserId,
                        updated_at      = :now
                    WHERE id = :personId
                      AND clan_id = :clanId
                      AND linked_user_id = :linkedUserId
                      AND deleted_at IS NULL
                    """)
    int updateSelfServiceFields(
            @Param("clanId") UUID clanId,
            @Param("personId") UUID personId,
            @Param("linkedUserId") UUID linkedUserId,
            @Param("avatarMediaId") UUID avatarMediaId,
            @Param("notesHtml") String notesHtml,
            @Param("notesText") String notesText,
            @Param("now") Instant now);

    /**
     * Opt-in propagation from the account profile (FR-N6, B6.8).
     *
     * <p>Each column is written only when its flag is on, so a member who opted in to their photo
     * and not to their name gets exactly that. The flags arrive as booleans rather than as two
     * statements because the alternative is two round trips for one profile save.
     *
     * <p>{@code alias} — <i>tên thường gọi</i> — never {@code full_name}. The book's name is what
     * the book says; an account display name is not a genealogical fact.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            nativeQuery = true,
            value =
                    """
                    UPDATE persons
                    SET avatar_media_id = CASE WHEN :avatar THEN CAST(:avatarMediaId AS UUID)
                                               ELSE avatar_media_id END,
                        alias           = CASE WHEN :displayName THEN CAST(:alias AS TEXT)
                                               ELSE alias END,
                        updated_by      = :linkedUserId,
                        updated_at      = :now
                    WHERE id = :personId
                      AND clan_id = :clanId
                      AND linked_user_id = :linkedUserId
                      AND deleted_at IS NULL
                    """)
    int updatePropagatedFields(
            @Param("clanId") UUID clanId,
            @Param("personId") UUID personId,
            @Param("linkedUserId") UUID linkedUserId,
            @Param("avatar") boolean avatar,
            @Param("avatarMediaId") UUID avatarMediaId,
            @Param("displayName") boolean displayName,
            @Param("alias") String alias,
            @Param("now") Instant now);
}
