package app.giaky.clanorg.infrastructure;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.TenantExempt;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code branches}.
 *
 * <p>Every derived finder takes a {@link ClanId} — not as ceremony, but because a branch id arrives
 * from a URL and this parameter is the entire difference between a 404 and another clan's structure
 * (doc 04 §7). {@code TenancyBoundaryIT} substitutes ids across clans on every one of these paths.
 */
public interface BranchJpaRepository extends JpaRepository<BranchJpaEntity, UUID> {

    Optional<BranchJpaEntity> findByClanIdAndIdAndDeletedAtIsNull(ClanId clanId, UUID id);

    /**
     * The whole tree of a clan, ordered by path.
     *
     * <p>Path order is depth-first order: a branch always sorts immediately before its descendants,
     * so the tree assembles in one pass without sorting again in Java.
     */
    List<BranchJpaEntity> findByClanIdAndDeletedAtIsNullOrderByPathAsc(ClanId clanId);

    /** I-C6: archiving is refused while this is non-zero. */
    long countByClanIdAndParentBranchIdAndDeletedAtIsNullAndArchivedAtIsNull(
            ClanId clanId, BranchId parentBranchId);

    /** The {@code ux_branches_path} pre-check, so a segment collision is a retry rather than a 500. */
    boolean existsByClanIdAndPathAndDeletedAtIsNull(ClanId clanId, String path);

    @TenantExempt(
            justification =
                    "Joining by branch code happens before any membership exists (FR-J1), so there"
                        + " is no clan to scope by — the code is the credential. The caller receives"
                        + " only the clan card of doc 05 §4.3, never the branch itself.")
    Optional<BranchJpaEntity> findByBranchCodeAndDeletedAtIsNull(String branchCode);

    @TenantExempt(
            justification =
                    "Branch codes are unique across the platform (`ux_branches_code`), so the"
                        + " collision check for CodeGenerator cannot be clan-scoped. It answers a"
                        + " boolean about a candidate this process just invented and discloses"
                        + " nothing about any clan.")
    boolean existsByBranchCode(String branchCode);

    /**
     * Recomputes {@code path} and {@code depth} for every branch below {@code rootId} (I-C4).
     *
     * <p>One statement, because the alternative is a read-modify-write loop over a subtree with a
     * window in the middle of it where the tree is neither its old shape nor its new one. The
     * recursion walks {@code parent_branch_id}, which is the authoritative structure, so the paths
     * it writes are derived from the tree rather than patched from the strings they replace — a
     * subtree whose paths had somehow drifted comes out correct instead of consistently wrong.
     *
     * <p>Bounded by the depth cap (I-C7): ten levels, and the whole clan is one index range.
     *
     * <p>{@code clan_id} appears in both terms of the recursion. It is redundant while the tree is
     * well-formed and it is not there for performance — it is there so that a corrupt
     * {@code parent_branch_id} pointing at another clan cannot make this statement rewrite that
     * clan's branches.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            nativeQuery = true,
            value =
                    """
                    WITH RECURSIVE subtree AS (
                        SELECT b.id,
                               CAST(b.path AS VARCHAR(512)) AS path,
                               CAST(b.depth AS SMALLINT)    AS depth
                        FROM branches b
                        WHERE b.id = :rootId
                          AND b.clan_id = :clanId
                          AND b.deleted_at IS NULL
                        UNION ALL
                        SELECT c.id,
                               CAST(s.path || substr(CAST(c.id AS VARCHAR), 1, 8) || '/' AS VARCHAR(512)),
                               CAST(s.depth + 1 AS SMALLINT)
                        FROM branches c
                                 JOIN subtree s ON c.parent_branch_id = s.id
                        WHERE c.clan_id = :clanId
                          AND c.deleted_at IS NULL
                    )
                    UPDATE branches b
                    SET path       = s.path,
                        depth      = s.depth,
                        updated_at = :now,
                        updated_by = :editor
                    FROM subtree s
                    WHERE b.id = s.id
                      AND b.id <> :rootId
                    """)
    int rewriteSubtreePaths(
            @Param("clanId") UUID clanId,
            @Param("rootId") UUID rootId,
            @Param("now") Instant now,
            @Param("editor") UUID editor);
}
