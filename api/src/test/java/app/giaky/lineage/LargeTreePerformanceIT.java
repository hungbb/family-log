package app.giaky.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import app.giaky.authorization.domain.EditableSet;
import app.giaky.authorization.domain.PermissionResolver;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.application.TreeQueryService;
import app.giaky.lineage.application.TreeView;
import app.giaky.lineage.domain.AncestryProjection;
import app.giaky.lineage.domain.TreeSnapshotCache;
import app.giaky.lineage.domain.TreeVersionRepository;
import app.giaky.lineage.support.LargeTreeGenerator;
import app.giaky.support.AbstractIntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * NFR-1 against the five-thousand-person fixture (phase 4 §5, B4.15).
 *
 * <p>NFR-1 sets the budget at a 2,000-node clan under 500 ms p95. This measures two and a half times
 * that many people and holds the same number, because the phase's exit criterion is the 5,000-person
 * fixture and a budget that grew with the fixture would not be a budget.
 *
 * <p><b>What is being measured, and what is not.</b> These run in a Testcontainers PostgreSQL on
 * whatever machine invoked them, so the absolute numbers are not a production figure. What they are
 * good for is the shape: a full tree read is three indexed scans and must not become a per-node
 * walk, and closure maintenance on a deep re-parent must stay proportional to the subtree rather than
 * to the clan. Either regression shows up here as an order of magnitude, not as a few milliseconds,
 * which is why a generous threshold still catches the thing worth catching.
 *
 * <p>The measured numbers are logged so a phase report can quote them (doc 09 §5).
 */
class LargeTreePerformanceIT extends AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(LargeTreePerformanceIT.class);

    /** NFR-1's tree-read budget, applied to a fixture two and a half times its stated size. */
    private static final Duration TREE_READ_BUDGET = Duration.ofMillis(500);

    /**
     * Closure maintenance for one re-parent.
     *
     * <p>Not in NFR-1, which speaks of person mutations at 300 ms; this is the sub-operation inside
     * one, and the one whose cost is not obviously bounded. Half the mutation budget leaves room for
     * the rest of the transaction.
     */
    private static final Duration CLOSURE_BUDGET = Duration.ofMillis(150);

    /**
     * A cache hit's budget (B5.3).
     *
     * <p>Two orders of magnitude below the scan, which is the point of the cache: a hit is a map
     * lookup, one batch resolver call and the loop that stamps {@code canEdit}. If this ever
     * approaches the read budget, the cache has stopped being hit and the key is wrong.
     */
    private static final Duration CACHED_READ_BUDGET = Duration.ofMillis(120);

    /**
     * The batch {@code EditableSet} for one grant (B5.6).
     *
     * <p>Not in NFR-1 either, and the one number in the tree read that could plausibly grow with the
     * clan rather than with the caller's grants. A subtree grant expands through the closure, so
     * "one query" is not the same as "one row" — but it is one query, and this is where a per-node
     * regression would show up as seconds.
     */
    private static final Duration EDITABLE_SET_BUDGET = Duration.ofMillis(150);

    /** Runs before each measurement so the first one is not paying for a cold plan cache. */
    private static final int WARMUP_READS = 2;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TreeQueryService trees;
    @Autowired private AncestryProjection projection;
    @Autowired private TreeVersionRepository treeVersions;
    @Autowired private TreeSnapshotCache snapshotCache;
    @Autowired private PermissionResolver resolver;

    @Test
    @DisplayName("a full tree read of 5,000 people stays inside the NFR-1 budget")
    void fullTreeRead() {
        Fixture world = new Fixture();

        for (int index = 0; index < WARMUP_READS; index++) {
            trees.tree(world.member, world.tree.clanId(), null, null);
            // Each warmup would otherwise leave the snapshot cached, and the measurement below would
            // be timing a map lookup. Bumping the version makes the next read miss — which is the
            // read this budget is about (B5.2).
            treeVersions.bump(world.tree.clanId());
        }

        long startedAt = System.nanoTime();
        TreeView tree = trees.tree(world.member, world.tree.clanId(), null, null);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        log.info(
                "NFR-1 tree read (uncached): {} people, {} edges, {} unions in {} ms",
                tree.persons().size(),
                tree.parentages().size(),
                tree.unions().size(),
                elapsed.toMillis());

        assertThat(tree.persons()).hasSizeGreaterThan(4_000);
        assertThat(tree.unions())
                .as("the fixture records marriages, which the canvas draws as couples")
                .isNotEmpty();
        assertThat(elapsed).isLessThan(TREE_READ_BUDGET);
    }

    @Test
    @DisplayName("the second reader of an unchanged tree pays for no scan at all (B5.3)")
    void cachedTreeRead() {
        Fixture world = new Fixture();

        TreeView first = trees.tree(world.member, world.tree.clanId(), null, null);

        long startedAt = System.nanoTime();
        TreeView second = trees.tree(world.member, world.tree.clanId(), null, null);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        log.info("Cached tree read: {} people in {} ms", second.persons().size(), elapsed.toMillis());

        assertThat(second.persons()).hasSameSizeAs(first.persons());
        assertThat(second.treeVersion()).isEqualTo(first.treeVersion());
        assertThat(snapshotCache.size()).isPositive();
        assertThat(elapsed).isLessThan(CACHED_READ_BUDGET);

        // Fast and stale is not a pass. The version is what makes the entry unreachable, so a bump
        // has to change what comes back.
        treeVersions.bump(world.tree.clanId());
        assertThat(trees.tree(world.member, world.tree.clanId(), null, null).treeVersion())
                .isGreaterThan(first.treeVersion());
    }

    @Test
    @DisplayName("the batch EditableSet costs the caller's grants, not the clan's size (B5.6)")
    void editableSetIsBoundedByGrants() {
        Fixture world = new Fixture();

        resolver.editableSet(world.member, world.tree.clanId());

        long startedAt = System.nanoTime();
        EditableSet editable = resolver.editableSet(world.member, world.tree.clanId());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        log.info(
                "EditableSet for a clan-wide admin over {} people: {} ms",
                world.tree.personCount(),
                elapsed.toMillis());

        assertThat(editable.clanWide())
                .as("one CLAN grant short-circuits the whole computation — no branch or person sets")
                .isTrue();
        assertThat(editable.branches()).isEmpty();
        assertThat(editable.persons())
                .as("a clan-wide answer never enumerates the clan, which is the whole point")
                .isEmpty();
        assertThat(elapsed).isLessThan(EDITABLE_SET_BUDGET);
    }

    @Test
    @DisplayName("a subtree read is bounded by the subtree, not by the clan")
    void subtreeRead() {
        Fixture world = new Fixture();
        PersonId deepest = world.tree.deepest();

        trees.tree(world.member, world.tree.clanId(), deepest, null);

        long startedAt = System.nanoTime();
        TreeView subtree = trees.tree(world.member, world.tree.clanId(), deepest, null);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        log.info(
                "Subtree read: {} people in {} ms", subtree.persons().size(), elapsed.toMillis());

        assertThat(subtree.persons()).hasSizeLessThan(100);
        assertThat(elapsed).isLessThan(TREE_READ_BUDGET);
    }

    @Test
    @DisplayName("re-parenting a deep subtree costs the subtree, not the clan")
    void closureMaintenanceOnAReparent() {
        Fixture world = new Fixture();

        // Detach a whole line from the founding ancestor and hang it somewhere else — the most
        // expensive shape of edit the closure has, and the one doc 03 §3.10 sizes the design against.
        UUID edgeId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT e.id
                        FROM parentages e
                                 JOIN person_ancestry a ON a.descendant_id = e.child_person_id
                        WHERE e.clan_id = ?
                          AND e.deleted_at IS NULL
                          AND a.ancestor_id = ?
                          AND a.depth = 1
                        ORDER BY (SELECT count(*)
                                  FROM person_ancestry d
                                  WHERE d.ancestor_id = e.child_person_id) DESC
                        LIMIT 1
                        """,
                        UUID.class,
                        world.tree.clanId().value(),
                        world.tree.founder().value());

        PersonId movedRoot =
                PersonId.of(
                        jdbcTemplate.queryForObject(
                                "SELECT child_person_id FROM parentages WHERE id = ?",
                                UUID.class,
                                edgeId));
        long subtreeSize =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM person_ancestry WHERE ancestor_id = ?",
                        Long.class,
                        movedRoot.value());

        // The same courtesy the read measurements get from WARMUP_READS: the recompute's statements
        // are parsed and planned once before the clock starts. Recomputing a subtree whose edges
        // have not changed leaves the closure exactly as it was.
        for (int index = 0; index < WARMUP_READS; index++) {
            projection.parentageRemoved(world.tree.clanId(), movedRoot);
        }

        jdbcTemplate.update("UPDATE parentages SET deleted_at = now() WHERE id = ?", edgeId);

        long startedAt = System.nanoTime();
        projection.parentageRemoved(world.tree.clanId(), movedRoot);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        log.info(
                "Closure recompute after detaching a subtree of {} people: {} ms",
                subtreeSize,
                elapsed.toMillis());

        assertThat(subtreeSize).as("the fixture should have a substantial line to move").isGreaterThan(50);
        assertThat(elapsed).isLessThan(CLOSURE_BUDGET);

        // Cheap to assert and worth asserting: fast and wrong is not a pass.
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                SELECT count(*) FROM person_ancestry
                                WHERE ancestor_id = ? AND descendant_id = ?
                                """,
                                Integer.class,
                                world.tree.founder().value(),
                                movedRoot.value()))
                .isZero();
    }

    /** A member who may read the clan, and the clan itself. */
    private final class Fixture {

        private final UserId member;
        private final LargeTreeGenerator.LargeTree tree;

        Fixture() {
            UUID userId = UUID.randomUUID();
            jdbcTemplate.update(
                    """
                    INSERT INTO users (id, email, password_hash, display_name, status)
                    VALUES (?, ?, 'x', 'Người dùng', 'ACTIVE')
                    """,
                    userId,
                    "perf-" + userId + "@example.com");

            tree = new LargeTreeGenerator(jdbcTemplate).generate(userId);
            member = UserId.of(userId);

            jdbcTemplate.update(
                    """
                    INSERT INTO clan_memberships (id, clan_id, user_id, status)
                    VALUES (?, ?, ?, 'ACTIVE')
                    """,
                    UUID.randomUUID(),
                    tree.clanId().value(),
                    userId);
            jdbcTemplate.update(
                    """
                    INSERT INTO permission_grants
                      (id, clan_id, grantee_user_id, permission, scope_type, granted_by)
                    VALUES (?, ?, ?, 'CLAN_ADMIN', 'CLAN', ?)
                    """,
                    UUID.randomUUID(),
                    tree.clanId().value(),
                    userId,
                    userId);

            // The fixture is five thousand people and their closure, bulk-loaded inside this test's
            // own uncommitted transaction — where autovacuum cannot see them, so the planner still
            // believes every one of these tables is empty and plans accordingly. A deployment has
            // statistics; without this line the measurements below are of a nested loop over
            // sequential scans (5.8 s for the tree read, 29 s for the closure recompute) rather
            // than of the product.
            jdbcTemplate.execute("ANALYZE");
        }
    }
}
