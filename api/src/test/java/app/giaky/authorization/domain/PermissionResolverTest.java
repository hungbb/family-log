package app.giaky.authorization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.authorization.domain.AccessDecision.DenialReason;
import app.giaky.authorization.support.ResolverFixture;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.error.PermissionDeniedException;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The resolver, against the requirements of doc 04 §8 — the strictest test bar in the project.
 *
 * <p>Written as many small explicit cases rather than as a few clever ones, on purpose. Phase 3 §8
 * says it outright: <i>prefer duplicated tests over clever abstractions in this phase</i>. A leak in
 * scope resolution breaks the product's central promise, and a table-driven test that is subtly
 * wrong about its own expectations is worse than no test, because it is green.
 */
class PermissionResolverTest {

    @Nested
    @DisplayName("membership gates everything (doc 04 §1.6)")
    class Membership {

        @Test
        @DisplayName("a non-member is denied, and told the clan does not exist")
        void nonMemberIsDenied() {
            ResolverFixture world = new ResolverFixture();
            UserId stranger = world.outsider();

            AccessDecision decision =
                    world.resolver.decide(
                            stranger, Permission.CLAN_VIEW, Target.clan(world.clanId));

            assertThat(decision.allowed()).isFalse();
            assertThat(decision.reason()).isEqualTo(DenialReason.NOT_A_MEMBER);

            assertThatThrownBy(
                            () ->
                                    world.resolver.require(
                                            stranger,
                                            Permission.CLAN_VIEW,
                                            Target.clan(world.clanId)))
                    .isInstanceOf(NotFoundException.class)
                    .extracting(error -> ((NotFoundException) error).code())
                    .isEqualTo(ErrorCode.CLAN_NOT_FOUND);
        }

        @Test
        @DisplayName("a grant is ineffective once the membership ends, without being revoked")
        void grantsDieWithTheMembership() {
            ResolverFixture world = new ResolverFixture();
            UserId admin = world.member();
            world.grant(admin, Permission.CLAN_ADMIN, PermissionScope.clan());
            assertThat(world.resolver.can(admin, Permission.TREE_EDIT, Target.clan(world.clanId)))
                    .isTrue();

            world.endMembership(admin);

            // The row is untouched and confers nothing. This is why membership revocation does not
            // have to delete grants for the model to be correct (B3.12 is hygiene, not enforcement).
            assertThat(world.resolver.can(admin, Permission.TREE_EDIT, Target.clan(world.clanId)))
                    .isFalse();
            assertThat(world.resolver.permissionsOf(admin, world.clanId)).isEmpty();
            assertThat(world.resolver.editableSet(admin, world.clanId).isEmpty()).isTrue();
        }

        @Test
        @DisplayName("membership in this clan says nothing about another one (NFR-4)")
        void membershipIsPerClan() {
            ResolverFixture world = new ResolverFixture();
            UserId admin = world.member();
            world.grant(admin, Permission.CLAN_ADMIN, PermissionScope.clan());

            AccessDecision decision =
                    world.resolver.decide(
                            admin, Permission.CLAN_VIEW, Target.clan(world.otherClanId));

            assertThat(decision.reason()).isEqualTo(DenialReason.NOT_A_MEMBER);
        }
    }

    @Nested
    @DisplayName("implication expansion (doc 04 §2, FR-Z6)")
    class Implications {

        @ParameterizedTest
        @EnumSource(Permission.class)
        @DisplayName("CLAN_ADMIN@CLAN confers every permission at clan scope")
        void clanAdminConfersEverything(Permission requested) {
            ResolverFixture world = new ResolverFixture();
            UserId admin = world.member();
            world.grant(admin, Permission.CLAN_ADMIN, PermissionScope.clan());

            assertThat(world.resolver.can(admin, requested, Target.clan(world.clanId))).isTrue();
        }

        @ParameterizedTest
        @EnumSource(Permission.class)
        @DisplayName("every permission confers CLAN_VIEW at its own scope")
        void everythingConfersView(Permission held) {
            ResolverFixture world = new ResolverFixture();
            UserId user = world.member();
            BranchId root = world.rootBranch();
            // CLAN_ADMIN is clan-scoped only (I-G2), so it is granted where it is legal to.
            PermissionScope scope =
                    held == Permission.CLAN_ADMIN
                            ? PermissionScope.clan()
                            : PermissionScope.branch(root);
            world.grant(user, held, scope);

            Target target =
                    held == Permission.CLAN_ADMIN
                            ? Target.clan(world.clanId)
                            : Target.branch(world.clanId, root);

            assertThat(world.resolver.can(user, Permission.CLAN_VIEW, target)).isTrue();
        }

        @Test
        @DisplayName("an implication does not widen the scope it was granted at")
        void implicationsStayInScope() {
            ResolverFixture world = new ResolverFixture();
            UserId editor = world.member();
            BranchId root = world.rootBranch();
            BranchId chiHai = world.branchUnder(root);
            world.grant(editor, Permission.TREE_EDIT, PermissionScope.branch(chiHai));

            // CLAN_VIEW comes with TREE_EDIT — but only over Chi Hai, not over the clan.
            assertThat(world.resolver.can(editor, Permission.CLAN_VIEW, Target.branch(world.clanId, chiHai)))
                    .isTrue();
            assertThat(world.resolver.can(editor, Permission.CLAN_VIEW, Target.clan(world.clanId)))
                    .isFalse();
        }

        @Test
        @DisplayName("a lesser permission does not confer a greater one")
        void viewDoesNotConferEdit() {
            ResolverFixture world = new ResolverFixture();
            UserId viewer = world.member();
            world.grant(viewer, Permission.CLAN_VIEW, PermissionScope.clan());

            assertThat(world.resolver.can(viewer, Permission.TREE_EDIT, Target.clan(world.clanId)))
                    .isFalse();
            assertThat(world.resolver.can(viewer, Permission.ROLE_MANAGE, Target.clan(world.clanId)))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("ineffective grants")
    class Ineffective {

        /**
         * The fixture's grant port hands over revoked and expired rows unfiltered, which the
         * production adapter never does. That is the point: it proves the resolver's own filter is
         * doing the work rather than being carried by the {@code WHERE} clause behind it (see
         * {@code PermissionResolver.effectiveGrants}). Two independent filters have to fail together
         * for revoked access to survive.
         */
        @Test
        @DisplayName("a revoked grant confers nothing, even when the port hands it over")
        void revokedGrantsAreIneffective() {
            ResolverFixture world = new ResolverFixture();
            UserId user = world.member();
            world.revokedGrant(user, Permission.TREE_EDIT, PermissionScope.clan());

            AccessDecision decision =
                    world.resolver.decide(user, Permission.TREE_EDIT, Target.clan(world.clanId));

            assertThat(decision.allowed()).isFalse();
            assertThat(decision.reason()).isEqualTo(DenialReason.NO_SUCH_PERMISSION);
            assertThat(world.resolver.permissionsOf(user, world.clanId)).isEmpty();
        }

        @Test
        @DisplayName("an expired grant confers nothing once its moment passes")
        void expiredGrantsAreIneffective() {
            ResolverFixture world = new ResolverFixture();
            UserId user = world.member();
            // The fixture's clock is frozen at NOW, so this lapsed an hour ago.
            PermissionGrant lapsed =
                    world.grantUntil(
                            user,
                            Permission.TREE_EDIT,
                            PermissionScope.clan(),
                            ResolverFixture.NOW.minusSeconds(3600));

            assertThat(lapsed.isEffectiveAt(ResolverFixture.NOW.minusSeconds(7200))).isTrue();
            assertThat(lapsed.isEffectiveAt(ResolverFixture.NOW)).isFalse();

            assertThat(world.resolver.can(user, Permission.TREE_EDIT, Target.clan(world.clanId)))
                    .isFalse();
            assertThat(world.resolver.editableSet(user, world.clanId).isEmpty()).isTrue();
        }

        @Test
        @DisplayName("a grant that has not yet lapsed still works")
        void unexpiredGrantsStillWork() {
            ResolverFixture world = new ResolverFixture();
            UserId user = world.member();
            world.grantUntil(
                    user,
                    Permission.TREE_EDIT,
                    PermissionScope.clan(),
                    ResolverFixture.NOW.plusSeconds(3600));

            assertThat(world.resolver.can(user, Permission.TREE_EDIT, Target.clan(world.clanId)))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("branch scope: the isolation guarantee (FR-Z4, doc 04 §3.1)")
    class BranchScope {

        @Test
        @DisplayName("covers its own branch, and every branch under it")
        void coversDescendants() {
            ResolverFixture world = new ResolverFixture();
            UserId editorC = world.member();
            BranchId root = world.rootBranch();
            BranchId branchC = world.branchUnder(root);
            BranchId underC = world.branchUnder(branchC);
            world.grant(editorC, Permission.TREE_EDIT, PermissionScope.branch(branchC));

            assertThat(world.resolver.can(editorC, Permission.TREE_EDIT, Target.branch(world.clanId, branchC)))
                    .as("its own branch")
                    .isTrue();
            assertThat(world.resolver.can(editorC, Permission.TREE_EDIT, Target.branch(world.clanId, underC)))
                    .as("a branch beneath it")
                    .isTrue();
        }

        @Test
        @DisplayName("does not cover a sibling branch, the root, or the clan")
        void doesNotCoverSiblingsOrAncestors() {
            ResolverFixture world = new ResolverFixture();
            UserId editorC = world.member();
            BranchId root = world.rootBranch();
            BranchId branchC = world.branchUnder(root);
            BranchId branchD = world.branchUnder(root);
            world.grant(editorC, Permission.TREE_EDIT, PermissionScope.branch(branchC));

            assertThat(world.resolver.can(editorC, Permission.TREE_EDIT, Target.branch(world.clanId, branchD)))
                    .as("Uncle D's branch")
                    .isFalse();
            assertThat(world.resolver.can(editorC, Permission.TREE_EDIT, Target.branch(world.clanId, root)))
                    .as("the đời-13 root branch, above the scope")
                    .isFalse();
            assertThat(world.resolver.can(editorC, Permission.TREE_EDIT, Target.clan(world.clanId)))
                    .as("clan settings")
                    .isFalse();
        }

        @Test
        @DisplayName("a person is covered by where they are placed")
        void coversPersonsByPlacement() {
            ResolverFixture world = new ResolverFixture();
            UserId editorC = world.member();
            BranchId root = world.rootBranch();
            BranchId branchC = world.branchUnder(root);
            BranchId branchD = world.branchUnder(root);
            world.grant(editorC, Permission.TREE_EDIT, PermissionScope.branch(branchC));

            UUID childOfC = ResolverFixture.newId();
            UUID sonOfD = ResolverFixture.newId();
            UUID ancestor = ResolverFixture.newId();

            assertThat(
                            world.resolver.can(
                                    editorC,
                                    Permission.TREE_EDIT,
                                    Target.person(world.clanId, childOfC, branchC)))
                    .isTrue();
            assertThat(
                            world.resolver.can(
                                    editorC,
                                    Permission.TREE_EDIT,
                                    Target.person(world.clanId, sonOfD, branchD)))
                    .isFalse();
            assertThat(
                            world.resolver.can(
                                    editorC,
                                    Permission.TREE_EDIT,
                                    Target.person(world.clanId, ancestor, root)))
                    .isFalse();
        }

        @Test
        @DisplayName("an unplaced person is reachable only from clan scope")
        void unplacedPersonNeedsClanScope() {
            ResolverFixture world = new ResolverFixture();
            UserId editorC = world.member();
            UserId admin = world.member();
            BranchId root = world.rootBranch();
            BranchId branchC = world.branchUnder(root);
            world.grant(editorC, Permission.TREE_EDIT, PermissionScope.branch(branchC));
            world.grant(admin, Permission.TREE_EDIT, PermissionScope.clan());

            UUID floating = ResolverFixture.newId();
            Target target = Target.person(world.clanId, floating, null);

            assertThat(world.resolver.can(editorC, Permission.TREE_EDIT, target)).isFalse();
            assertThat(world.resolver.can(admin, Permission.TREE_EDIT, target)).isTrue();
        }

        @Test
        @DisplayName("a scope naming a branch this clan does not have covers nothing")
        void unknownBranchScopeCoversNothing() {
            ResolverFixture world = new ResolverFixture();
            UserId user = world.member();
            BranchId root = world.rootBranch();
            // Deny by default (doc 04 §1.5): "I cannot classify this" is not "probably fine".
            world.grant(user, Permission.TREE_EDIT, PermissionScope.branch(world.unknownBranch()));

            assertThat(world.resolver.can(user, Permission.TREE_EDIT, Target.branch(world.clanId, root)))
                    .isFalse();
            assertThat(world.resolver.editableSet(user, world.clanId).isEmpty()).isTrue();
        }

        @Test
        @DisplayName("a target branch this clan does not have is denied")
        void unknownTargetBranchIsDenied() {
            ResolverFixture world = new ResolverFixture();
            UserId user = world.member();
            BranchId root = world.rootBranch();
            world.grant(user, Permission.TREE_EDIT, PermissionScope.branch(root));

            assertThat(
                            world.resolver.can(
                                    user,
                                    Permission.TREE_EDIT,
                                    Target.branch(world.clanId, world.unknownBranch())))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("relationship targets need every endpoint (doc 04 §5.2)")
    class Relationships {

        @Test
        @DisplayName("an edge inside the scope is allowed")
        void edgeWithinScope() {
            ResolverFixture world = new ResolverFixture();
            UserId editorC = world.member();
            BranchId root = world.rootBranch();
            BranchId branchC = world.branchUnder(root);
            world.grant(editorC, Permission.TREE_EDIT, PermissionScope.branch(branchC));

            Target edge =
                    Target.relationship(
                            world.clanId,
                            List.of(
                                    new Target.PersonTarget(
                                            world.clanId, ResolverFixture.newId(), branchC),
                                    new Target.PersonTarget(
                                            world.clanId, ResolverFixture.newId(), branchC)));

            assertThat(world.resolver.can(editorC, Permission.TREE_EDIT, edge)).isTrue();
        }

        @Test
        @DisplayName("an edge reaching out of the scope is refused — the escape hatch, closed")
        void edgeLeavingScope() {
            ResolverFixture world = new ResolverFixture();
            UserId editorC = world.member();
            BranchId root = world.rootBranch();
            BranchId branchC = world.branchUnder(root);
            BranchId branchD = world.branchUnder(root);
            world.grant(editorC, Permission.TREE_EDIT, PermissionScope.branch(branchC));

            Target edge =
                    Target.relationship(
                            world.clanId,
                            List.of(
                                    new Target.PersonTarget(
                                            world.clanId, ResolverFixture.newId(), branchC),
                                    new Target.PersonTarget(
                                            world.clanId, ResolverFixture.newId(), branchD)));

            assertThat(world.resolver.can(editorC, Permission.TREE_EDIT, edge))
                    .as("linking into Branch D would be editing Branch D")
                    .isFalse();
        }

        @Test
        @DisplayName("two grants, one endpoint each: access is the union of the grants (doc 04 §1.3)")
        void edgeAcrossTwoGrants() {
            ResolverFixture world = new ResolverFixture();
            UserId editor = world.member();
            BranchId root = world.rootBranch();
            BranchId branchC = world.branchUnder(root);
            BranchId branchD = world.branchUnder(root);
            PermissionGrant onC =
                    world.grant(editor, Permission.TREE_EDIT, PermissionScope.branch(branchC));
            world.grant(editor, Permission.TREE_EDIT, PermissionScope.branch(branchD));

            Target.PersonTarget inC =
                    new Target.PersonTarget(world.clanId, ResolverFixture.newId(), branchC);
            Target.PersonTarget inD =
                    new Target.PersonTarget(world.clanId, ResolverFixture.newId(), branchD);
            // Each end is editable on its own…
            assertThat(world.resolver.can(editor, Permission.TREE_EDIT, inC)).isTrue();
            assertThat(world.resolver.can(editor, Permission.TREE_EDIT, inD)).isTrue();

            AccessDecision decision =
                    world.resolver.decide(
                            editor,
                            Permission.TREE_EDIT,
                            Target.relationship(world.clanId, List.of(inC, inD)));

            // …so the edge between them is, and the audit entry names the grant on the first end.
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.authorizingGrantId()).isEqualTo(onC.id());
        }

        @Test
        @DisplayName("…and a branch grant combines with a subtree grant the same way")
        void edgeAcrossABranchAndASubtreeGrant() {
            ResolverFixture world = new ResolverFixture();
            UserId editor = world.member();
            BranchId branchC = world.branchUnder(world.rootBranch());
            UUID uncle = ResolverFixture.newId();
            UUID son = ResolverFixture.newId();
            world.enableAncestry();
            world.descendants(uncle, son);
            world.grant(editor, Permission.TREE_EDIT, PermissionScope.branch(branchC));
            world.grant(editor, Permission.TREE_EDIT, PermissionScope.subtree(uncle));

            Target marriage =
                    Target.relationship(
                            world.clanId,
                            List.of(
                                    new Target.PersonTarget(world.clanId, son, null),
                                    new Target.PersonTarget(
                                            world.clanId, ResolverFixture.newId(), branchC)));

            assertThat(world.resolver.can(editor, Permission.TREE_EDIT, marriage)).isTrue();
        }

        @Test
        @DisplayName("an endpoint no grant covers still refuses the edge, however many are held")
        void edgeLeavingEveryScope() {
            ResolverFixture world = new ResolverFixture();
            UserId editor = world.member();
            BranchId root = world.rootBranch();
            BranchId branchC = world.branchUnder(root);
            BranchId branchD = world.branchUnder(root);
            BranchId branchE = world.branchUnder(root);
            world.grant(editor, Permission.TREE_EDIT, PermissionScope.branch(branchC));
            world.grant(editor, Permission.TREE_EDIT, PermissionScope.branch(branchD));

            AccessDecision decision =
                    world.resolver.decide(
                            editor,
                            Permission.TREE_EDIT,
                            Target.relationship(
                                    world.clanId,
                                    List.of(
                                            new Target.PersonTarget(
                                                    world.clanId, ResolverFixture.newId(), branchC),
                                            new Target.PersonTarget(
                                                    world.clanId, ResolverFixture.newId(), branchD),
                                            new Target.PersonTarget(
                                                    world.clanId, ResolverFixture.newId(), branchE))));

            assertThat(decision.allowed())
                    .as("a child attached to a marriage across C and D, but living in E")
                    .isFalse();
            assertThat(decision.reason()).isEqualTo(AccessDecision.DenialReason.OUT_OF_SCOPE);
        }

        @Test
        @DisplayName("a grant of some other permission on the far end does not count")
        void edgeNeedsThePermissionOnBothEnds() {
            ResolverFixture world = new ResolverFixture();
            UserId editor = world.member();
            BranchId root = world.rootBranch();
            BranchId branchC = world.branchUnder(root);
            BranchId branchD = world.branchUnder(root);
            world.grant(editor, Permission.TREE_EDIT, PermissionScope.branch(branchC));
            world.grant(editor, Permission.MEMBER_APPROVE, PermissionScope.branch(branchD));

            Target edge =
                    Target.relationship(
                            world.clanId,
                            List.of(
                                    new Target.PersonTarget(
                                            world.clanId, ResolverFixture.newId(), branchC),
                                    new Target.PersonTarget(
                                            world.clanId, ResolverFixture.newId(), branchD)));

            assertThat(world.resolver.can(editor, Permission.TREE_EDIT, edge))
                    .as("approving members in D is not editing D")
                    .isFalse();
        }

        @Test
        @DisplayName("somebody holding nothing that edits is told so, not that they are out of scope")
        void edgeWithoutThePermissionAtAll() {
            ResolverFixture world = new ResolverFixture();
            UserId viewer = world.member();
            BranchId branchC = world.branchUnder(world.rootBranch());
            world.grant(viewer, Permission.CLAN_VIEW, PermissionScope.clan());

            AccessDecision decision =
                    world.resolver.decide(
                            viewer,
                            Permission.TREE_EDIT,
                            Target.relationship(
                                    world.clanId,
                                    List.of(
                                            new Target.PersonTarget(
                                                    world.clanId, ResolverFixture.newId(), branchC),
                                            new Target.PersonTarget(
                                                    world.clanId,
                                                    ResolverFixture.newId(),
                                                    branchC))));

            assertThat(decision.allowed()).isFalse();
            assertThat(decision.reason())
                    .isEqualTo(AccessDecision.DenialReason.NO_SUCH_PERMISSION);
        }

        @Test
        @DisplayName("an edge spanning two clans cannot even be expressed")
        void edgeAcrossClansIsRejected() {
            ResolverFixture world = new ResolverFixture();
            BranchId root = world.rootBranch();

            assertThatThrownBy(
                            () ->
                                    Target.relationship(
                                            world.clanId,
                                            List.of(
                                                    new Target.PersonTarget(
                                                            world.clanId,
                                                            ResolverFixture.newId(),
                                                            root),
                                                    new Target.PersonTarget(
                                                            world.otherClanId,
                                                            ResolverFixture.newId(),
                                                            null))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("same clan");
        }
    }

    @Nested
    @DisplayName("subtree scope is understood but not available until Phase 4")
    class SubtreeScope {

        @Test
        @DisplayName("without the closure table, a subtree grant confers nothing")
        void deniedWhileAncestryIsUnavailable() {
            ResolverFixture world = new ResolverFixture();
            UserId user = world.member();
            UUID uncle = ResolverFixture.newId();
            UUID child = ResolverFixture.newId();
            world.grant(user, Permission.TREE_EDIT, PermissionScope.subtree(uncle));

            // Deny, not throw: the port is never reached because ScopeCoverage checks first.
            assertThat(
                            world.resolver.can(
                                    user,
                                    Permission.TREE_EDIT,
                                    Target.person(world.clanId, child, null)))
                    .isFalse();
            assertThat(world.resolver.editableSet(user, world.clanId).isEmpty()).isTrue();
        }

        @Test
        @DisplayName("with the closure table, it covers the person named and their descendants")
        void coversDescendantsOnceAvailable() {
            ResolverFixture world = new ResolverFixture();
            UserId user = world.member();
            UUID uncle = ResolverFixture.newId();
            UUID son = ResolverFixture.newId();
            UUID grandson = ResolverFixture.newId();
            UUID nephew = ResolverFixture.newId();
            world.enableAncestry();
            world.descendants(uncle, son, grandson);
            world.grant(user, Permission.TREE_EDIT, PermissionScope.subtree(uncle));

            assertThat(personAllowed(world, user, uncle)).as("himself, the self-row").isTrue();
            assertThat(personAllowed(world, user, son)).isTrue();
            assertThat(personAllowed(world, user, grandson)).isTrue();
            assertThat(personAllowed(world, user, nephew)).as("a sibling line").isFalse();
        }

        @Test
        @DisplayName("it covers a branch only when that branch's root person is a descendant")
        void coversBranchByItsRootPerson() {
            ResolverFixture world = new ResolverFixture();
            UserId user = world.member();
            BranchId root = world.rootBranch();
            BranchId hisBranch = world.branchUnder(root);
            BranchId anotherBranch = world.branchUnder(root);
            UUID uncle = ResolverFixture.newId();
            UUID son = ResolverFixture.newId();
            UUID stranger = ResolverFixture.newId();

            world.enableAncestry();
            world.descendants(uncle, son);
            world.branchRootPerson(hisBranch, son);
            world.branchRootPerson(anotherBranch, stranger);
            world.grant(user, Permission.TREE_EDIT, PermissionScope.subtree(uncle));

            assertThat(world.resolver.can(user, Permission.TREE_EDIT, Target.branch(world.clanId, hisBranch)))
                    .isTrue();
            assertThat(
                            world.resolver.can(
                                    user, Permission.TREE_EDIT, Target.branch(world.clanId, anotherBranch)))
                    .isFalse();
            assertThat(world.resolver.can(user, Permission.TREE_EDIT, Target.branch(world.clanId, root)))
                    .as("a branch with no root person recorded")
                    .isFalse();
        }

        @Test
        @DisplayName("it never covers the clan itself")
        void neverCoversTheClan() {
            ResolverFixture world = new ResolverFixture();
            UserId user = world.member();
            UUID uncle = ResolverFixture.newId();
            world.enableAncestry();
            world.grant(user, Permission.CLAN_VIEW, PermissionScope.subtree(uncle));

            assertThat(world.resolver.can(user, Permission.CLAN_VIEW, Target.clan(world.clanId)))
                    .isFalse();
        }

        @Test
        @DisplayName("an edge is covered only when every endpoint is a descendant")
        void relationshipUnderSubtreeScope() {
            ResolverFixture world = new ResolverFixture();
            UserId user = world.member();
            UUID uncle = ResolverFixture.newId();
            UUID son = ResolverFixture.newId();
            UUID outsider = ResolverFixture.newId();
            world.enableAncestry();
            world.descendants(uncle, son);
            world.grant(user, Permission.TREE_EDIT, PermissionScope.subtree(uncle));

            Target inside =
                    Target.relationship(
                            world.clanId,
                            List.of(
                                    new Target.PersonTarget(world.clanId, uncle, null),
                                    new Target.PersonTarget(world.clanId, son, null)));
            Target reachingOut =
                    Target.relationship(
                            world.clanId,
                            List.of(
                                    new Target.PersonTarget(world.clanId, son, null),
                                    new Target.PersonTarget(world.clanId, outsider, null)));

            assertThat(world.resolver.can(user, Permission.TREE_EDIT, inside)).isTrue();
            assertThat(world.resolver.can(user, Permission.TREE_EDIT, reachingOut)).isFalse();
        }

        private static boolean personAllowed(ResolverFixture world, UserId user, UUID personId) {
            return world.resolver.can(
                    user, Permission.TREE_EDIT, Target.person(world.clanId, personId, null));
        }
    }

    @Nested
    @DisplayName("overlapping grants union (doc 04 §1.3)")
    class Union {

        @Test
        @DisplayName("two branch grants cover both branches and nothing else")
        void twoBranchesUnion() {
            ResolverFixture world = new ResolverFixture();
            UserId editor = world.member();
            BranchId root = world.rootBranch();
            BranchId branchC = world.branchUnder(root);
            BranchId branchD = world.branchUnder(root);
            BranchId branchE = world.branchUnder(root);
            world.grant(editor, Permission.TREE_EDIT, PermissionScope.branch(branchC));
            world.grant(editor, Permission.TREE_EDIT, PermissionScope.branch(branchD));

            assertThat(world.resolver.can(editor, Permission.TREE_EDIT, Target.branch(world.clanId, branchC)))
                    .isTrue();
            assertThat(world.resolver.can(editor, Permission.TREE_EDIT, Target.branch(world.clanId, branchD)))
                    .isTrue();
            assertThat(world.resolver.can(editor, Permission.TREE_EDIT, Target.branch(world.clanId, branchE)))
                    .isFalse();
        }

        @Test
        @DisplayName("the order grants are held in does not change the answer")
        void resolutionIsOrderIndependent() {
            ResolverFixture narrowFirst = new ResolverFixture();
            UserId a = narrowFirst.member();
            BranchId rootA = narrowFirst.rootBranch();
            BranchId chiA = narrowFirst.branchUnder(rootA);
            narrowFirst.grant(a, Permission.TREE_EDIT, PermissionScope.branch(chiA));
            narrowFirst.grant(a, Permission.TREE_EDIT, PermissionScope.clan());

            ResolverFixture wideFirst = new ResolverFixture();
            UserId b = wideFirst.member();
            BranchId rootB = wideFirst.rootBranch();
            BranchId chiB = wideFirst.branchUnder(rootB);
            wideFirst.grant(b, Permission.TREE_EDIT, PermissionScope.clan());
            wideFirst.grant(b, Permission.TREE_EDIT, PermissionScope.branch(chiB));

            // No negative rules, so the union is the whole answer and neither ordering wins.
            assertThat(narrowFirst.resolver.can(a, Permission.TREE_EDIT, Target.branch(narrowFirst.clanId, rootA)))
                    .isTrue();
            assertThat(wideFirst.resolver.can(b, Permission.TREE_EDIT, Target.branch(wideFirst.clanId, rootB)))
                    .isTrue();
        }

        @Test
        @DisplayName("a revoked grant does not subtract from a live one")
        void revocationIsNotANegativeRule() {
            ResolverFixture world = new ResolverFixture();
            UserId editor = world.member();
            BranchId root = world.rootBranch();
            world.grant(editor, Permission.TREE_EDIT, PermissionScope.clan());
            world.revokedGrant(editor, Permission.TREE_EDIT, PermissionScope.branch(root));

            assertThat(world.resolver.can(editor, Permission.TREE_EDIT, Target.branch(world.clanId, root)))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("denials explain themselves (doc 04 §7)")
    class Explanations {

        @Test
        @DisplayName("holding the permission elsewhere is OUT_OF_SCOPE, naming what is held")
        void outOfScopeNamesTheHeldScope() {
            ResolverFixture world = new ResolverFixture();
            UserId editorC = world.member();
            BranchId root = world.rootBranch();
            BranchId branchC = world.branchUnder(root);
            BranchId branchD = world.branchUnder(root);
            world.grant(editorC, Permission.TREE_EDIT, PermissionScope.branch(branchC));

            AccessDecision decision =
                    world.resolver.decide(
                            editorC, Permission.TREE_EDIT, Target.branch(world.clanId, branchD));

            assertThat(decision.reason()).isEqualTo(DenialReason.OUT_OF_SCOPE);
            assertThat(decision.heldScopeSummary()).contains(branchC.value().toString());

            assertThatThrownBy(
                            () ->
                                    world.resolver.require(
                                            editorC,
                                            Permission.TREE_EDIT,
                                            Target.branch(world.clanId, branchD)))
                    .isInstanceOf(PermissionDeniedException.class)
                    .extracting(error -> ((PermissionDeniedException) error).code())
                    .isEqualTo(ErrorCode.OUT_OF_SCOPE);
        }

        @Test
        @DisplayName("not holding it at all is PERMISSION_DENIED, with nothing to name")
        void missingPermissionIsPlainlyDenied() {
            ResolverFixture world = new ResolverFixture();
            UserId viewer = world.member();
            world.grant(viewer, Permission.CLAN_VIEW, PermissionScope.clan());

            AccessDecision decision =
                    world.resolver.decide(viewer, Permission.TREE_EDIT, Target.clan(world.clanId));

            assertThat(decision.reason()).isEqualTo(DenialReason.NO_SUCH_PERMISSION);
            assertThat(decision.heldScopeSummary()).isNull();

            assertThatThrownBy(
                            () ->
                                    world.resolver.require(
                                            viewer, Permission.TREE_EDIT, Target.clan(world.clanId)))
                    .isInstanceOf(PermissionDeniedException.class)
                    .extracting(error -> ((PermissionDeniedException) error).code())
                    .isEqualTo(ErrorCode.PERMISSION_DENIED);
        }

        @Test
        @DisplayName("an allow names the grant that authorised it (FR-Z8)")
        void allowCarriesTheAuthorizingGrant() {
            ResolverFixture world = new ResolverFixture();
            UserId admin = world.member();
            PermissionGrant bootstrap =
                    world.grant(admin, Permission.CLAN_ADMIN, PermissionScope.clan());

            AccessDecision decision =
                    world.resolver.require(admin, Permission.TREE_EDIT, Target.clan(world.clanId));

            assertThat(decision.authorizingGrantId()).isEqualTo(bootstrap.id());
            assertThat(decision.grantId()).contains(bootstrap.id());
        }
    }

    @Nested
    @DisplayName("membership and read access (doc 04 §5.5)")
    class Reads {

        @Test
        @DisplayName("a member with a branch-scoped grant may still read the clan")
        void readsAreClanWide() {
            ResolverFixture world = new ResolverFixture();
            UserId member = world.member();
            BranchId root = world.rootBranch();
            BranchId chi = world.branchUnder(root);
            world.grant(member, Permission.CLAN_VIEW, PermissionScope.branch(chi));

            // The distinction that matters: requireMembership passes where a clan-scoped CLAN_VIEW
            // check would not. Scoping constrains writes, not reads (FR-T2).
            world.resolver.requireMembership(member, world.clanId);
            assertThat(world.resolver.can(member, Permission.CLAN_VIEW, Target.clan(world.clanId)))
                    .isFalse();
        }

        @Test
        @DisplayName("a non-member gets a 404 from requireMembership")
        void nonMemberCannotRead() {
            ResolverFixture world = new ResolverFixture();
            UserId stranger = world.outsider();

            assertThatThrownBy(() -> world.resolver.requireMembership(stranger, world.clanId))
                    .isInstanceOf(NotFoundException.class);
            assertThat(world.resolver.isMember(stranger, world.clanId)).isFalse();
        }
    }

    @Nested
    @DisplayName("batch resolution (doc 04 §4.1)")
    class Editable {

        @Test
        @DisplayName("a clan-wide edit grant makes everything editable and asks nothing further")
        void clanWideShortCircuits() {
            ResolverFixture world = new ResolverFixture();
            UserId admin = world.member();
            world.grant(admin, Permission.CLAN_ADMIN, PermissionScope.clan());

            EditableSet editable = world.resolver.editableSet(admin, world.clanId);

            assertThat(editable.clanWide()).isTrue();
            assertThat(editable.contains(ResolverFixture.newId(), null)).isTrue();
            assertThat(editable.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("a branch grant expands to that branch and its descendants")
        void branchGrantExpands() {
            ResolverFixture world = new ResolverFixture();
            UserId editorC = world.member();
            BranchId root = world.rootBranch();
            BranchId branchC = world.branchUnder(root);
            BranchId underC = world.branchUnder(branchC);
            BranchId branchD = world.branchUnder(root);
            world.grant(editorC, Permission.TREE_EDIT, PermissionScope.branch(branchC));

            EditableSet editable = world.resolver.editableSet(editorC, world.clanId);

            assertThat(editable.clanWide()).isFalse();
            assertThat(editable.branches()).containsExactlyInAnyOrder(branchC, underC);
            assertThat(editable.contains(ResolverFixture.newId(), branchC)).isTrue();
            assertThat(editable.contains(ResolverFixture.newId(), underC)).isTrue();
            assertThat(editable.contains(ResolverFixture.newId(), branchD)).isFalse();
            assertThat(editable.contains(ResolverFixture.newId(), null)).isFalse();
        }

        @Test
        @DisplayName("it agrees with decide() for every person in the clan")
        void agreesWithDecide() {
            ResolverFixture world = new ResolverFixture();
            UserId editorC = world.member();
            BranchId root = world.rootBranch();
            BranchId branchC = world.branchUnder(root);
            BranchId underC = world.branchUnder(branchC);
            BranchId branchD = world.branchUnder(root);
            world.grant(editorC, Permission.TREE_EDIT, PermissionScope.branch(branchC));

            EditableSet editable = world.resolver.editableSet(editorC, world.clanId);
            for (BranchId branch : List.of(root, branchC, underC, branchD)) {
                UUID person = ResolverFixture.newId();
                // The batch answer and the per-node answer must never disagree; a tree rendered
                // with canEdit flags from one and enforced with the other would be a lie.
                assertThat(editable.contains(person, branch))
                        .as("branch %s", branch)
                        .isEqualTo(
                                world.resolver.can(
                                        editorC,
                                        Permission.TREE_EDIT,
                                        Target.person(world.clanId, person, branch)));
            }
        }

        @Test
        @DisplayName("a subtree grant contributes its descendants once the closure table exists")
        void subtreeGrantExpands() {
            ResolverFixture world = new ResolverFixture();
            UserId user = world.member();
            UUID uncle = ResolverFixture.newId();
            UUID son = ResolverFixture.newId();
            world.enableAncestry();
            world.descendants(uncle, son);
            world.grant(user, Permission.TREE_EDIT, PermissionScope.subtree(uncle));

            EditableSet editable = world.resolver.editableSet(user, world.clanId);

            assertThat(editable.persons()).containsExactlyInAnyOrder(uncle, son);
            assertThat(editable.contains(son, null)).isTrue();
        }

        @Test
        @DisplayName("holding only CLAN_VIEW makes nothing editable")
        void viewerEditsNothing() {
            ResolverFixture world = new ResolverFixture();
            UserId viewer = world.member();
            world.grant(viewer, Permission.CLAN_VIEW, PermissionScope.clan());

            assertThat(world.resolver.editableSet(viewer, world.clanId).isEmpty()).isTrue();
            assertThat(EditableSet.none().isEmpty()).isTrue();
            assertThat(EditableSet.everything().clanWide()).isTrue();
        }
    }

    @Nested
    @DisplayName("arguments")
    class Arguments {

        @Test
        @DisplayName("a null target is a bug, not a denial")
        void nullTargetThrows() {
            ResolverFixture world = new ResolverFixture();
            UserId user = world.member();

            assertThatThrownBy(() -> world.resolver.decide(user, Permission.CLAN_VIEW, null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> world.resolver.decide(null, Permission.CLAN_VIEW, Target.clan(world.clanId)))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> world.resolver.decide(user, null, Target.clan(world.clanId)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("scopesHolding and permissionsOf are empty for a non-member")
        void queriesAreEmptyForNonMembers() {
            ResolverFixture world = new ResolverFixture();
            UserId stranger = world.outsider();

            assertThat(world.resolver.permissionsOf(stranger, world.clanId)).isEmpty();
            assertThat(world.resolver.scopesHolding(stranger, world.clanId, Permission.TREE_EDIT))
                    .isEmpty();
        }

        @Test
        @DisplayName("scopesHolding expands implications")
        void scopesHoldingExpands() {
            ResolverFixture world = new ResolverFixture();
            UserId admin = world.member();
            UserId editor = world.member();
            BranchId root = world.rootBranch();
            world.grant(admin, Permission.CLAN_ADMIN, PermissionScope.clan());
            world.grant(editor, Permission.TREE_EDIT, PermissionScope.branch(root));

            assertThat(world.resolver.scopesHolding(admin, world.clanId, Permission.ROLE_MANAGE))
                    .containsExactly(PermissionScope.clan());
            assertThat(world.resolver.scopesHolding(editor, world.clanId, Permission.CLAN_VIEW))
                    .containsExactly(PermissionScope.branch(root));
            assertThat(world.resolver.scopesHolding(editor, world.clanId, Permission.ROLE_MANAGE))
                    .isEmpty();
        }
    }
}
