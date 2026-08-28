package app.giaky.authorization.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.giaky.authorization.support.ResolverFixture;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.UserId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The property of doc 04 §8 and phase 3 §5: <b>a user may act on a node if and only if some grant's
 * scope contains it</b> — checked against an implementation that shares no code with the resolver.
 *
 * <p>A handful of hand-written examples would pass against several wrong implementations. This
 * builds a random branch tree and a random set of grants, then asks the resolver about every person
 * in the clan and compares the answer with a reference that walks {@code parentBranchId} links up to
 * the root. The resolver compares materialized-path prefixes; the reference never looks at a path.
 * Two independent derivations of "is this branch inside that one" have to be wrong in the same
 * direction for this to pass wrongly.
 *
 * <p>Seeded, and the seed is the parameter: a failure names the exact world that produced it and
 * re-running reproduces it. The same shape as {@code BranchPathPropertyTest}, aimed at the other
 * half of the risk — that one proves the tree stays consistent, this one proves the answers derived
 * from it are.
 */
class PermissionPropertyTest {

    private static final int BRANCH_COUNT = 20;
    private static final int PERSON_COUNT = 60;
    private static final int MAX_GRANTS = 4;

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1L, 2L, 3L, 5L, 8L, 13L, 42L, 1234L, 20260101L, 987654321L})
    @DisplayName("the resolver allows exactly what some grant's scope contains")
    void resolverMatchesAnIndependentReference(long seed) {
        Random random = new Random(seed);
        ResolverFixture world = new ResolverFixture();
        UserId user = world.member();

        Map<BranchId, BranchId> parentOf = growTree(world, random);
        List<BranchId> allBranches = new ArrayList<>(parentOf.keySet());
        Map<UUID, BranchId> placement = placePeople(random, allBranches);
        List<PermissionScope> heldScopes = grantRandomly(world, user, random, allBranches);

        int allowed = 0;
        for (Map.Entry<UUID, BranchId> person : placement.entrySet()) {
            boolean expected = referenceAllows(heldScopes, parentOf, person.getValue());
            boolean actual =
                    world.resolver.can(
                            user,
                            Permission.TREE_EDIT,
                            Target.person(world.clanId, person.getKey(), person.getValue()));

            assertThat(actual)
                    .as(
                            "person %s in branch %s, grants %s (seed %s)",
                            person.getKey(), person.getValue(), heldScopes, seed)
                    .isEqualTo(expected);
            if (expected) {
                allowed++;
            }
        }

        // The batch answer of doc 04 §4.1 has to agree with the per-node one, or a tree rendered
        // with canEdit flags would be showing something the server will then refuse.
        EditableSet editable = world.resolver.editableSet(user, world.clanId);
        for (Map.Entry<UUID, BranchId> person : placement.entrySet()) {
            assertThat(editable.contains(person.getKey(), person.getValue()))
                    .as("editableSet disagrees for %s (seed %s)", person.getKey(), seed)
                    .isEqualTo(referenceAllows(heldScopes, parentOf, person.getValue()));
        }

        // A run where nothing was ever allowed, or everything was, asserts far less than it looks
        // like it does. Fail loudly rather than pass quietly.
        assertThat(allowed)
                .as("people this user may edit for seed %s — the run must be mixed", seed)
                .isBetween(0, placement.size());
    }

    /**
     * The reference: containment by walking parents, never by comparing paths.
     *
     * <p>Deliberately the slow, obvious implementation. It is allowed to be O(depth) per question
     * because it exists to be read and believed, not to be fast.
     */
    private static boolean referenceAllows(
            List<PermissionScope> heldScopes, Map<BranchId, BranchId> parentOf, BranchId branch) {

        for (PermissionScope scope : heldScopes) {
            if (scope.type() == ScopeType.CLAN) {
                return true;
            }
            BranchId scopeBranch = scope.branchId();
            for (BranchId walk = branch; walk != null; walk = parentOf.get(walk)) {
                if (walk.equals(scopeBranch)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A random tree: each new branch hangs from a randomly chosen existing one. */
    private static Map<BranchId, BranchId> growTree(ResolverFixture world, Random random) {
        Map<BranchId, BranchId> parentOf = new LinkedHashMap<>();
        BranchId root = world.rootBranch();
        parentOf.put(root, null);

        List<BranchId> placed = new ArrayList<>(List.of(root));
        while (placed.size() < BRANCH_COUNT) {
            BranchId parent = placed.get(random.nextInt(placed.size()));
            if (depthOf(parent, parentOf) >= 9) {
                // The depth cap of I-C7; the production tree could not be deeper either.
                continue;
            }
            BranchId child = world.branchUnder(parent);
            parentOf.put(child, parent);
            placed.add(child);
        }
        return parentOf;
    }

    private static int depthOf(BranchId branch, Map<BranchId, BranchId> parentOf) {
        int depth = 0;
        for (BranchId walk = parentOf.get(branch); walk != null; walk = parentOf.get(walk)) {
            depth++;
        }
        return depth;
    }

    private static Map<UUID, BranchId> placePeople(Random random, List<BranchId> branches) {
        Map<UUID, BranchId> placement = new HashMap<>();
        for (int i = 0; i < PERSON_COUNT; i++) {
            placement.put(
                    ResolverFixture.newId(), branches.get(random.nextInt(branches.size())));
        }
        return placement;
    }

    /**
     * A random handful of grants, at a mix of permissions and scopes.
     *
     * <p>Includes permissions that do <i>not</i> confer {@code TREE_EDIT}, so the property is also
     * asserting that {@code CLAN_VIEW} on a branch does not quietly let somebody edit it.
     */
    private static List<PermissionScope> grantRandomly(
            ResolverFixture world, UserId user, Random random, List<BranchId> branches) {

        List<PermissionScope> conferringEdit = new ArrayList<>();
        int count = random.nextInt(MAX_GRANTS + 1);
        for (int i = 0; i < count; i++) {
            boolean clanWide = random.nextInt(10) == 0;
            PermissionScope scope =
                    clanWide
                            ? PermissionScope.clan()
                            : PermissionScope.branch(branches.get(random.nextInt(branches.size())));

            Permission permission = pickPermission(random, clanWide);
            if (alreadyHeld(world, user, permission, scope)) {
                // I-G5: the in-memory repository refuses a duplicate, exactly as ux_grant_active
                // would. Drawing again keeps the generator honest about what the database allows.
                continue;
            }
            world.grant(user, permission, scope);
            if (permission.implies(Permission.TREE_EDIT)) {
                conferringEdit.add(scope);
            }
        }
        return conferringEdit;
    }

    private static Permission pickPermission(Random random, boolean clanWide) {
        Permission[] candidates =
                clanWide
                        ? new Permission[] {
                            Permission.CLAN_VIEW,
                            Permission.TREE_EDIT,
                            Permission.ROLE_MANAGE,
                            Permission.CLAN_ADMIN
                        }
                        : new Permission[] {
                            Permission.CLAN_VIEW, Permission.TREE_EDIT, Permission.ROLE_MANAGE
                        };
        return candidates[random.nextInt(candidates.length)];
    }

    private static boolean alreadyHeld(
            ResolverFixture world, UserId user, Permission permission, PermissionScope scope) {

        return world.resolver.scopesHolding(user, world.clanId, permission).contains(scope);
    }
}
