package app.giaky.clanorg.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.giaky.clanorg.application.CreateBranchCommand;
import app.giaky.clanorg.support.ClanOrgTestFixtures;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The exit criterion of phase 2 §7: <b>branch paths are provably consistent after arbitrary move
 * sequences.</b>
 *
 * <p>A handful of hand-written move examples would pass against several wrong implementations. This
 * builds a random tree, performs a long run of random moves against the real {@code BranchService},
 * and after every one of them checks the whole clan against a definition of correctness that does
 * not consult the path at all — every branch's path must equal its parent's path plus its own
 * segment, and its depth must be its parent's plus one, walking up to the root.
 *
 * <p>Seeded, and the seed is the parameter: a failure names the exact sequence that produced it, and
 * re-running reproduces it. {@code BranchStructureIT} performs the same shape of check against real
 * PostgreSQL, which is what keeps the in-memory rewrite honest about the SQL it stands in for.
 */
class BranchPathPropertyTest {

    /** How many branches each generated clan has. Enough to be several levels deep and wide. */
    private static final int BRANCH_COUNT = 25;

    /** How many moves are attempted per seed. Most succeed; the rest are legitimately refused. */
    private static final int MOVE_ATTEMPTS = 60;

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1L, 2L, 3L, 7L, 11L, 42L, 1234L, 20260101L})
    @DisplayName("a clan tree stays consistent through a long run of random moves (I-C3, I-C4)")
    void pathsStayConsistent(long seed) {
        Random random = new Random(seed);
        ClanOrgTestFixtures fixtures = new ClanOrgTestFixtures();
        UserId founder = fixtures.newUser("Nguyễn Văn An");
        ClanId clanId = fixtures.newClan(founder, "Nguyễn Văn");

        List<BranchId> branchIds = growTree(fixtures, founder, clanId, random);
        assertConsistent(fixtures, clanId);

        int moved = 0;
        for (int attempt = 0; attempt < MOVE_ATTEMPTS; attempt++) {
            BranchId subject = branchIds.get(random.nextInt(branchIds.size()));
            BranchId destination = branchIds.get(random.nextInt(branchIds.size()));
            try {
                fixtures.branchService.move(founder, clanId, subject, destination);
                moved++;
            } catch (RuntimeException refused) {
                // Cycles, the root, and moves that would breach the depth cap are all supposed to
                // be refused. What matters is that a refusal changes nothing, which the assertion
                // below re-checks either way.
            }
            assertConsistent(fixtures, clanId);
        }

        // A run in which every move was refused would assert nothing, so make the coverage explicit.
        assertThat(moved).as("moves that actually happened for seed %s", seed).isGreaterThan(5);
    }

    /** A random tree: each new branch hangs from a randomly chosen existing one. */
    private static List<BranchId> growTree(
            ClanOrgTestFixtures fixtures, UserId founder, ClanId clanId, Random random) {

        List<BranchId> ids = new ArrayList<>();
        ids.add(BranchId.of(fixtures.clanService.detail(founder, clanId).rootBranchId()));

        while (ids.size() < BRANCH_COUNT) {
            BranchId parent = ids.get(random.nextInt(ids.size()));
            try {
                ids.add(
                        BranchId.of(
                                fixtures.branchService
                                        .create(
                                                founder,
                                                clanId,
                                                new CreateBranchCommand(
                                                        parent, "Chi " + ids.size(), null))
                                        .id()));
            } catch (RuntimeException refusedByDepthCap) {
                // The chosen parent was already ten deep. Draw another.
            }
        }
        return ids;
    }

    /**
     * Checks the clan against the tree, not against itself.
     *
     * <p>Deliberately re-derives every path and depth from {@code parentBranchId} alone. Comparing
     * paths to other paths would happily agree with an implementation that was consistently wrong.
     */
    private static void assertConsistent(ClanOrgTestFixtures fixtures, ClanId clanId) {
        List<Branch> all = fixtures.branches.findAllByClan(clanId);
        Map<BranchId, Branch> byId = new HashMap<>();
        all.forEach(branch -> byId.put(branch.id(), branch));

        long roots = all.stream().filter(Branch::isRoot).count();
        // I-C2: exactly one root, always.
        assertThat(roots).as("root branches in the clan").isEqualTo(1);

        for (Branch branch : all) {
            MaterializedPath expectedPath = derivePath(branch, byId);
            assertThat(branch.path())
                    .as("path of %s (%s)", branch.name(), branch.id())
                    .isEqualTo(expectedPath);
            assertThat(branch.depth())
                    .as("depth of %s (%s)", branch.name(), branch.id())
                    .isEqualTo(expectedPath.depth());
            assertThat(branch.depth())
                    .as("depth cap for %s", branch.name())
                    .isLessThanOrEqualTo(Branch.MAX_DEPTH);
        }
    }

    /** The path a branch must have, walked from the root down through its ancestors. */
    private static MaterializedPath derivePath(Branch branch, Map<BranchId, Branch> byId) {
        List<BranchId> chain = new ArrayList<>();
        Branch current = branch;
        // Bounded by the depth cap; a cycle would make this loop forever, so it is capped hard.
        for (int step = 0; step <= Branch.MAX_DEPTH + 1 && !current.isRoot(); step++) {
            chain.add(current.id());
            current = byId.get(current.parentBranchId());
            assertThat(current).as("every branch reaches the root (I-C3)").isNotNull();
        }
        assertThat(current.isRoot()).as("no cycle above %s", branch.name()).isTrue();

        MaterializedPath path = MaterializedPath.root(current.id());
        for (int i = chain.size() - 1; i >= 0; i--) {
            path = path.child(chain.get(i));
        }
        return path;
    }
}
