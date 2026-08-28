package app.giaky.authorization.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.giaky.authorization.support.ResolverFixture;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The scope-covering relation of doc 04 §5.4 — the half of {@link ScopeCoverage} that grant
 * management asks (B3.5).
 *
 * <p>Tested separately from target resolution because the two questions differ in a way that is easy
 * to get subtly wrong: "does my scope contain that node?" and "does my scope contain that
 * <i>scope</i>?" have different answers for the clan case, and getting the second one wrong is how
 * somebody grants themselves out of their own branch.
 *
 * <p>Reached through {@code PermissionResolver.scopeCovers} rather than by constructing a {@code
 * ScopeCoverage} directly, so what is tested is the object grant management actually calls.
 */
class ScopeCoverageTest {

    @Test
    @DisplayName("clan scope covers everything, including itself")
    void clanCoversEverything() {
        ResolverFixture world = new ResolverFixture();
        BranchId root = world.rootBranch();
        BranchId chi = world.branchUnder(root);
        UUID person = ResolverFixture.newId();

        assertThat(covers(world, PermissionScope.clan(), PermissionScope.clan())).isTrue();
        assertThat(covers(world, PermissionScope.clan(), PermissionScope.branch(chi))).isTrue();
        assertThat(covers(world, PermissionScope.clan(), PermissionScope.subtree(person))).isTrue();
    }

    @Test
    @DisplayName("branch scope covers itself and the branches below it")
    void branchCoversDescendantBranches() {
        ResolverFixture world = new ResolverFixture();
        BranchId root = world.rootBranch();
        BranchId chiHai = world.branchUnder(root);
        BranchId underChiHai = world.branchUnder(chiHai);
        BranchId chiBa = world.branchUnder(root);

        assertThat(covers(world, PermissionScope.branch(chiHai), PermissionScope.branch(chiHai)))
                .as("reflexive — granting what you hold, where you hold it")
                .isTrue();
        assertThat(covers(world, PermissionScope.branch(chiHai), PermissionScope.branch(underChiHai)))
                .isTrue();
        assertThat(covers(world, PermissionScope.branch(chiHai), PermissionScope.branch(chiBa)))
                .as("the chi next door")
                .isFalse();
        assertThat(covers(world, PermissionScope.branch(chiHai), PermissionScope.branch(root)))
                .as("upwards — this is the escalation the rule exists to refuse")
                .isFalse();
    }

    @Test
    @DisplayName("branch scope never covers clan scope")
    void branchNeverCoversClan() {
        ResolverFixture world = new ResolverFixture();
        BranchId root = world.rootBranch();
        BranchId chi = world.branchUnder(root);

        assertThat(covers(world, PermissionScope.branch(chi), PermissionScope.clan())).isFalse();
        assertThat(covers(world, PermissionScope.branch(root), PermissionScope.clan()))
                .as("even the root branch is not the clan")
                .isFalse();
    }

    @Test
    @DisplayName("a branch that does not exist covers nothing, and is covered by nothing narrower")
    void unknownBranchesFailClosed() {
        ResolverFixture world = new ResolverFixture();
        BranchId root = world.rootBranch();
        BranchId ghost = world.unknownBranch();

        assertThat(covers(world, PermissionScope.branch(ghost), PermissionScope.branch(root)))
                .isFalse();
        assertThat(covers(world, PermissionScope.branch(root), PermissionScope.branch(ghost)))
                .isFalse();
    }

    @Test
    @DisplayName("branch scope covers a subtree only when that person is placed inside it")
    void branchCoversSubtreeByPlacement() {
        ResolverFixture world = new ResolverFixture();
        BranchId root = world.rootBranch();
        BranchId chiHai = world.branchUnder(root);
        BranchId underChiHai = world.branchUnder(chiHai);
        BranchId chiBa = world.branchUnder(root);
        UUID inChiHai = ResolverFixture.newId();
        UUID deeper = ResolverFixture.newId();
        UUID inChiBa = ResolverFixture.newId();
        UUID unplaced = ResolverFixture.newId();

        world.enableAncestry();
        world.placePerson(inChiHai, chiHai);
        world.placePerson(deeper, underChiHai);
        world.placePerson(inChiBa, chiBa);

        assertThat(covers(world, PermissionScope.branch(chiHai), PermissionScope.subtree(inChiHai)))
                .isTrue();
        assertThat(covers(world, PermissionScope.branch(chiHai), PermissionScope.subtree(deeper)))
                .as("placed in a branch below the scope")
                .isTrue();
        assertThat(covers(world, PermissionScope.branch(chiHai), PermissionScope.subtree(inChiBa)))
                .isFalse();
        assertThat(covers(world, PermissionScope.branch(chiHai), PermissionScope.subtree(unplaced)))
                .as("no placement is a no, not a maybe")
                .isFalse();
    }

    @Test
    @DisplayName("before Phase 4, a branch scope covers no subtree at all")
    void branchCoversNoSubtreeWithoutAncestry() {
        ResolverFixture world = new ResolverFixture();
        BranchId root = world.rootBranch();
        UUID person = ResolverFixture.newId();

        // The port would throw if it were reached; ScopeCoverage checks isAvailable() first, so the
        // answer is a plain no rather than an exception escaping into a controller.
        assertThat(covers(world, PermissionScope.branch(root), PermissionScope.subtree(person)))
                .isFalse();
    }

    @Test
    @DisplayName("subtree scope covers descendants, and a branch whose root person is one")
    void subtreeCoversDescendants() {
        ResolverFixture world = new ResolverFixture();
        BranchId root = world.rootBranch();
        BranchId hisBranch = world.branchUnder(root);
        BranchId otherBranch = world.branchUnder(root);
        UUID uncle = ResolverFixture.newId();
        UUID son = ResolverFixture.newId();
        UUID stranger = ResolverFixture.newId();

        world.enableAncestry();
        world.descendants(uncle, son);
        world.branchRootPerson(hisBranch, son);
        world.branchRootPerson(otherBranch, stranger);

        assertThat(covers(world, PermissionScope.subtree(uncle), PermissionScope.subtree(son)))
                .isTrue();
        assertThat(covers(world, PermissionScope.subtree(uncle), PermissionScope.subtree(uncle)))
                .as("reflexive, through the self-row")
                .isTrue();
        assertThat(covers(world, PermissionScope.subtree(uncle), PermissionScope.subtree(stranger)))
                .isFalse();
        assertThat(covers(world, PermissionScope.subtree(uncle), PermissionScope.branch(hisBranch)))
                .isTrue();
        assertThat(covers(world, PermissionScope.subtree(uncle), PermissionScope.branch(otherBranch)))
                .isFalse();
        assertThat(covers(world, PermissionScope.subtree(uncle), PermissionScope.clan())).isFalse();
    }

    @Test
    @DisplayName("before Phase 4, a subtree scope covers nothing")
    void subtreeCoversNothingWithoutAncestry() {
        ResolverFixture world = new ResolverFixture();
        BranchId root = world.rootBranch();
        UUID uncle = ResolverFixture.newId();

        assertThat(covers(world, PermissionScope.subtree(uncle), PermissionScope.branch(root)))
                .isFalse();
        assertThat(
                        covers(
                                world,
                                PermissionScope.subtree(uncle),
                                PermissionScope.subtree(ResolverFixture.newId())))
                .isFalse();
    }

    @Test
    @DisplayName("a null scope on either side is a no")
    void nullsFailClosed() {
        ResolverFixture world = new ResolverFixture();

        assertThat(covers(world, null, PermissionScope.clan())).isFalse();
        assertThat(covers(world, PermissionScope.clan(), null)).isFalse();
    }

    /**
     * The target-side guard, reached directly rather than through the resolver.
     *
     * <p>{@code PermissionResolver.decide} takes the clan <i>from</i> the target and rejects a null
     * one outright, so these three conditions cannot fail on that path — which is exactly why they
     * are worth asserting here. They are the defence that would matter if a future caller reached
     * {@code ScopeCoverage} another way, and an untested guard is a guard that stops working without
     * anyone noticing.
     */
    @Test
    @DisplayName("the target-side guard refuses nulls and a target from another clan")
    void targetGuardFailsClosed() {
        ResolverFixture world = new ResolverFixture();
        BranchId root = world.rootBranch();
        ScopeCoverage coverage =
                new ScopeCoverage(
                        new BranchHierarchyPort() {
                            @Override
                            public Optional<String> pathOf(ClanId clan, BranchId branch) {
                                return Optional.empty();
                            }

                            @Override
                            public List<BranchPath> allPaths(ClanId clan) {
                                return List.of();
                            }

                            @Override
                            public Optional<UUID> rootPersonOf(ClanId clan, BranchId branch) {
                                return Optional.empty();
                            }
                        },
                        new LineageAncestryPort() {
                            @Override
                            public boolean isAvailable() {
                                return false;
                            }

                            @Override
                            public boolean covers(ClanId clan, UUID ancestor, UUID candidate) {
                                throw new IllegalStateException("not reachable");
                            }

                            @Override
                            public Set<UUID> descendantsOf(ClanId clan, UUID ancestor) {
                                throw new IllegalStateException("not reachable");
                            }

                            @Override
                            public boolean personExists(ClanId clan, UUID personId) {
                                throw new IllegalStateException("not reachable");
                            }

                            @Override
                            public java.util.Map<UUID, String> displayNames(
                                    ClanId clan, java.util.Collection<UUID> personIds) {
                                throw new IllegalStateException("not reachable");
                            }

                            @Override
                            public Optional<BranchId> placementOf(ClanId clan, UUID personId) {
                                throw new IllegalStateException("not reachable");
                            }
                        });

        assertThat(coverage.covers(world.clanId, null, Target.clan(world.clanId))).isFalse();
        assertThat(coverage.covers(world.clanId, PermissionScope.clan(), (Target) null)).isFalse();
        assertThat(
                        coverage.covers(
                                world.clanId,
                                PermissionScope.clan(),
                                Target.branch(world.otherClanId, root)))
                .as("a target in another clan is refused however wide the scope (NFR-4)")
                .isFalse();
    }

    private static boolean covers(
            ResolverFixture world, PermissionScope outer, PermissionScope inner) {
        return world.resolver.scopeCovers(world.clanId, outer, inner);
    }
}
