package app.giaky.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.lineage.domain.AncestryProjection;
import app.giaky.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * <b>The release gate on risk R3</b> (doc 09 §4.5, phase 4 §5).
 *
 * <p>The ancestry closure is maintained incrementally — a cross product when an edge appears, a
 * bounded recompute when one goes away — because recomputing a clan on every edit would put a
 * recursive walk on the hot path of every mutation. The price is that the incremental path can
 * drift, silently, in a way no ordinary test would notice: an ancestry that is subtly wrong still
 * looks like an ancestry, and what it changes is who may edit which branch.
 *
 * <p>So this test does the only thing that settles it. It applies randomized sequences of edge
 * additions and removals through the production projection, then rebuilds the same clan's closure
 * from the edges with {@code rebuild_person_ancestry} and asserts the two agree, pair for pair and
 * depth for depth. The rebuild is an independent implementation — breadth-first over the edge table,
 * written in SQL, sharing no code with the incremental path — which is what makes the comparison
 * worth anything.
 *
 * <p>Named {@code *IT} rather than {@code *Test}: the thing under test is SQL, and a version of it
 * running against an in-memory double would be testing a reimplementation of the bug it is looking
 * for. It needs a real PostgreSQL, so it runs under Failsafe.
 *
 * <p>The three explicit cases at the foot of the file are the ones the phase doc calls out by name,
 * because random sequences reach them rarely and each is a way the incremental path could plausibly
 * be wrong.
 */
class AncestryProjectionPropertyIT extends AbstractIntegrationTest {

    /** Fixed, so a failure reproduces (doc 09 §4.6). */
    private static final long SEED = 20260828L;

    private static final int PEOPLE = 40;
    private static final int OPERATIONS = 200;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AncestryProjection projection;

    @Test
    @DisplayName("randomized add/remove sequences leave a closure identical to a fresh rebuild")
    void randomizedSequencesMatchARebuild() {
        World world = new World();
        Random random = new Random(SEED);
        List<Edge> live = new ArrayList<>();

        for (int step = 0; step < OPERATIONS; step++) {
            boolean removing = !live.isEmpty() && random.nextInt(100) < 30;
            if (removing) {
                Edge edge = live.remove(random.nextInt(live.size()));
                world.removeEdge(edge);
            } else {
                world.tryAddRandomEdge(random).ifPresent(live::add);
            }
        }

        assertClosureMatchesRebuild(world, "after " + OPERATIONS + " randomized operations");
    }

    @Test
    @DisplayName("removing a middle edge detaches only what depended on it")
    void removingAMiddleEdge() {
        World world = new World();
        PersonId a = world.person("A");
        PersonId b = world.person("B");
        PersonId c = world.person("C");
        PersonId d = world.person("D");

        Edge ab = world.addEdge(a, b);
        Edge bc = world.addEdge(b, c);
        world.addEdge(c, d);

        assertThat(world.depth(a, d)).isEqualTo(3);

        world.removeEdge(bc);

        assertThat(world.depth(a, b)).isEqualTo(1);
        assertThat(world.depth(a, d)).as("nothing above C reaches D any more").isNull();
        assertThat(world.depth(c, d)).as("and the subtree below the cut is untouched").isEqualTo(1);
        assertThat(ab).isNotNull();
        assertClosureMatchesRebuild(world, "after removing a middle edge");
    }

    @Test
    @DisplayName("re-parenting a node carries its whole subtree with it")
    void reparentingALargeSubtree() {
        World world = new World();
        PersonId oldParent = world.person("old");
        PersonId newParent = world.person("new");
        PersonId subtreeRoot = world.person("root");

        Edge attachment = world.addEdge(oldParent, subtreeRoot);

        // A deep chain hanging off the node being moved — the case where a naive "delete the pairs
        // this edge contributed" would leave the subtree half-attached.
        PersonId current = subtreeRoot;
        for (int depth = 0; depth < 8; depth++) {
            PersonId child = world.person("d" + depth);
            world.addEdge(current, child);
            current = child;
        }

        assertThat(world.depth(oldParent, current)).isEqualTo(9);

        world.removeEdge(attachment);
        world.addEdge(newParent, subtreeRoot);

        assertThat(world.depth(oldParent, current)).isNull();
        assertThat(world.depth(newParent, current)).isEqualTo(9);
        assertClosureMatchesRebuild(world, "after re-parenting a deep subtree");
    }

    @Test
    @DisplayName("a diamond keeps its connection when one of the two paths is cut")
    void diamond() {
        World world = new World();
        PersonId ancestor = world.person("ancestor");
        PersonId father = world.person("father");
        PersonId mother = world.person("mother");
        PersonId child = world.person("child");

        // Both of the child's parents descend from the same ancestor — a cousin marriage, which
        // real books contain, and the case where removal cannot be subtraction.
        world.addEdge(ancestor, father);
        world.addEdge(ancestor, mother);
        world.addEdge(father, child);
        Edge motherEdge = world.addEdge(mother, child);

        assertThat(world.depth(ancestor, child)).isEqualTo(2);

        world.removeEdge(motherEdge);

        assertThat(world.depth(ancestor, child))
                .as("the father's line still connects them; subtracting the mother's would not have")
                .isEqualTo(2);
        assertClosureMatchesRebuild(world, "after cutting one side of a diamond");
    }

    @Test
    @DisplayName("depth is the shortest path when two routes are of different length")
    void depthIsTheShortestPath() {
        World world = new World();
        PersonId ancestor = world.person("ancestor");
        PersonId middle = world.person("middle");
        PersonId child = world.person("child");

        world.addEdge(ancestor, middle);
        world.addEdge(middle, child);
        // A second, shorter route: the ancestor is also recorded as the child's own mother.
        world.addEdge(ancestor, child);

        assertThat(world.depth(ancestor, child)).isEqualTo(1);
        assertClosureMatchesRebuild(world, "with two routes of different length");
    }

    // --- the comparison --------------------------------------------------------------------------

    /**
     * Snapshots the incrementally maintained closure, rebuilds it from the edges, and compares.
     *
     * <p>Order matters: the rebuild replaces what it is being compared against, so the snapshot has
     * to be taken first. Both sides are read the same way, so a difference is a difference in the
     * data rather than in how it was read.
     */
    private void assertClosureMatchesRebuild(World world, String context) {
        Map<String, Integer> incremental = world.closureSnapshot();
        projection.rebuild(world.clanId);
        Map<String, Integer> rebuilt = world.closureSnapshot();

        assertThat(incremental)
                .as(
                        "the incremental closure disagrees with a rebuild %s — this is risk R3, and"
                                + " it means somebody may be able to edit a branch they were never"
                                + " granted",
                        context)
                .isEqualTo(rebuilt);
    }

    // --- a clan to experiment on -------------------------------------------------------------------

    /** One clan, its people, and the edges between them — all through the production write paths. */
    private final class World {

        private final ClanId clanId = ClanId.of(UUID.randomUUID());
        private final UUID branchId = UUID.randomUUID();
        private final List<PersonId> people = new ArrayList<>();

        World() {
            String suffix =
                    clanId.value()
                            .toString()
                            .substring(0, 4)
                            .toUpperCase()
                            .replaceAll("[^A-HJKMNP-TV-Z0-9]", "9");
            jdbcTemplate.update(
                    """
                    INSERT INTO clans (id, clan_code, name, visibility)
                    VALUES (?, ?, 'Nguyễn Văn', 'PRIVATE')
                    """,
                    clanId.value(),
                    "NGUYEN-" + suffix);
            jdbcTemplate.update(
                    """
                    INSERT INTO branches (id, clan_id, branch_code, name, path, depth)
                    VALUES (?, ?, ?, 'Họ', ?, 0)
                    """,
                    branchId,
                    clanId.value(),
                    "HO-" + suffix,
                    "/" + branchId.toString().substring(0, 8) + "/");

            for (int index = 0; index < PEOPLE; index++) {
                people.add(person("P" + index));
            }
        }

        PersonId person(String name) {
            PersonId id = PersonId.of(UUID.randomUUID());
            jdbcTemplate.update(
                    """
                    INSERT INTO persons (id, clan_id, branch_id, full_name)
                    VALUES (?, ?, ?, ?)
                    """,
                    id.value(),
                    clanId.value(),
                    branchId,
                    name);
            projection.personCreated(clanId, id);
            return id;
        }

        /**
         * Adds an edge if it would be legal, and reports whether it did.
         *
         * <p>The legality checks mirror what {@code LineageEdges} enforces — no cycle (I-R4) and one
         * parent per role (I-R3) — because the projection is only ever asked to maintain a closure
         * over a graph those rules have already constrained, and its behaviour on a cycle is
         * undefined by design.
         */
        java.util.Optional<Edge> tryAddRandomEdge(Random random) {
            PersonId parent = people.get(random.nextInt(people.size()));
            PersonId child = people.get(random.nextInt(people.size()));
            if (parent.equals(child) || wouldCycle(parent, child) || parentCount(child) >= 2) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(addEdge(parent, child));
        }

        /**
         * Adds an edge, taking whichever parent role is still free.
         *
         * <p>I-R3 allows one father and one mother, and {@code ux_parentage_child_role} enforces it.
         * Choosing the role here rather than making every call site pass one keeps the tests about
         * the shape of the graph, which is what the closure cares about — it has no opinion on which
         * parent an edge came from.
         */
        Edge addEdge(PersonId parent, PersonId child) {
            UUID id = UUID.randomUUID();
            String role = freeRole(child);
            jdbcTemplate.update(
                    """
                    INSERT INTO parentages
                      (id, clan_id, parent_person_id, child_person_id, parent_role, nature)
                    VALUES (?, ?, ?, ?, ?, 'BIOLOGICAL')
                    """,
                    id,
                    clanId.value(),
                    parent.value(),
                    child.value(),
                    role);
            projection.parentageAdded(clanId, parent, child);
            return new Edge(id, parent, child);
        }

        /**
         * The role this child has no parent in yet.
         *
         * <p>Not "FATHER for the first edge, MOTHER for the second": once a child's father edge has
         * been removed and only the mother's is left, the next edge is the second one again and
         * that rule would hand out MOTHER twice — which the unique index refuses, about a hundred
         * operations into the randomized sequence.
         */
        private String freeRole(PersonId child) {
            List<String> taken =
                    jdbcTemplate.queryForList(
                            """
                            SELECT parent_role FROM parentages
                            WHERE child_person_id = ? AND deleted_at IS NULL
                            """,
                            String.class,
                            child.value());
            return taken.contains("FATHER") ? "MOTHER" : "FATHER";
        }

        void removeEdge(Edge edge) {
            // Soft-deleted first, exactly as LineageEdges does it: the recompute reads the surviving
            // edges, so an edge still visible to it is an edge it helpfully puts back.
            jdbcTemplate.update("UPDATE parentages SET deleted_at = now() WHERE id = ?", edge.id());
            projection.parentageRemoved(clanId, edge.child());
        }

        boolean wouldCycle(PersonId parent, PersonId child) {
            return projection.covers(clanId, child, parent);
        }

        int parentCount(PersonId child) {
            Integer count =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT count(*) FROM parentages
                            WHERE child_person_id = ? AND deleted_at IS NULL
                            """,
                            Integer.class,
                            child.value());
            return count == null ? 0 : count;
        }

        Integer depth(PersonId ancestor, PersonId descendant) {
            List<Integer> rows =
                    jdbcTemplate.queryForList(
                            """
                            SELECT depth FROM person_ancestry
                            WHERE ancestor_id = ? AND descendant_id = ?
                            """,
                            Integer.class,
                            ancestor.value(),
                            descendant.value());
            return rows.isEmpty() ? null : rows.get(0);
        }

        /** The whole closure as {@code "ancestor>descendant" → depth}, for a value comparison. */
        Map<String, Integer> closureSnapshot() {
            Map<String, Integer> snapshot = new LinkedHashMap<>();
            jdbcTemplate
                    .queryForList(
                            """
                            SELECT ancestor_id, descendant_id, depth
                            FROM person_ancestry
                            WHERE clan_id = ?
                            ORDER BY ancestor_id, descendant_id
                            """,
                            clanId.value())
                    .forEach(
                            row ->
                                    snapshot.put(
                                            row.get("ancestor_id") + ">" + row.get("descendant_id"),
                                            ((Number) row.get("depth")).intValue()));
            return snapshot;
        }
    }

    private record Edge(UUID id, PersonId parent, PersonId child) {}
}
