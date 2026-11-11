package app.giaky.lineage.application;

import static org.assertj.core.api.Assertions.assertThat;

import app.giaky.authorization.application.GrantCommand;
import app.giaky.authorization.domain.Permission;
import app.giaky.authorization.domain.Target;
import app.giaky.clanorg.application.JoinRequestView;
import app.giaky.clanorg.application.UpdateBranchCommand;
import app.giaky.clanorg.application.SubmitJoinRequestCommand;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.domain.Gender;
import app.giaky.lineage.support.LineageTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link TreeQueryService} — the flat payload of doc 05 §4.9 (B4.9). */
class TreeQueryServiceTest {

    private LineageTestFixtures fixtures;
    private UserId admin;
    private ClanId clan;
    private BranchId root;

    private String clanCode;

    private PersonId doi1;
    private PersonId doi2;
    private PersonId doi3;

    @BeforeEach
    void setUp() {
        fixtures = new LineageTestFixtures();
        admin = fixtures.clanOrg.newUser("Trưởng họ");
        clan = fixtures.clanOrg.newClan(admin, "Họ Nguyễn");
        root = fixtures.rootBranch(clan);
        clanCode = fixtures.clanOrg.clanService.detail(admin, clan).clanCode();

        doi1 = fixtures.founder(admin, clan, root, "Nguyễn Phúc Tổ");
        fixtures.spouseOf(admin, clan, doi1, "Trần Thị Tổ Mẫu", Gender.FEMALE);
        doi2 = fixtures.childOf(admin, clan, doi1, "Nguyễn Văn An", Gender.MALE);
        doi3 = fixtures.childOf(admin, clan, doi2, "Nguyễn Văn Bình", Gender.MALE);
    }

    @Test
    @DisplayName("the master view returns the whole clan, flat")
    void masterView() {
        TreeView tree = fixtures.treeQueryService.tree(admin, clan, null, null);

        assertThat(tree.persons()).hasSize(4);
        assertThat(tree.parentages()).hasSize(2);
        assertThat(tree.unions()).hasSize(1);
        assertThat(tree.meta().personCount()).isEqualTo(4);
        assertThat(tree.meta().maxGeneration()).isEqualTo(3);
        assertThat(tree.meta().truncated()).isFalse();
    }

    @Test
    @DisplayName("roots are everybody with no parent inside the payload — usually more than one")
    void rootsIncludeMarriedInPartners() {
        TreeView tree = fixtures.treeQueryService.tree(admin, clan, null, null);

        assertThat(tree.rootPersonIds())
                .as("the founding ancestor, and the wife who married in")
                .hasSize(2)
                .contains(doi1.value());
    }

    @Test
    @DisplayName("a subtree read is bounded to one person and below")
    void subtree() {
        TreeView tree = fixtures.treeQueryService.tree(admin, clan, doi2, null);

        assertThat(tree.persons()).extracting(PersonSummaryView::id).containsExactlyInAnyOrder(
                doi2.value(), doi3.value());
        assertThat(tree.rootPersonIds()).containsExactly(doi2.value());
        assertThat(tree.parentages())
                .as("the edge above the subtree root is left out — its other end is not here")
                .hasSize(1);
    }

    @Test
    @DisplayName("a depth bound says so, so the client can offer to load more")
    void depthBoundIsReported() {
        TreeView bounded = fixtures.treeQueryService.tree(admin, clan, doi1, 1);

        assertThat(bounded.persons()).hasSize(2);
        assertThat(bounded.meta().truncated()).isTrue();

        TreeView whole = fixtures.treeQueryService.tree(admin, clan, doi1, 5);
        assertThat(whole.meta().truncated()).isFalse();
    }

    @Test
    @DisplayName("ancestors come back nearest first, the person themselves at depth 0")
    void ancestors() {
        assertThat(fixtures.treeQueryService.ancestors(admin, clan, doi3))
                .extracting(AncestorView::depth)
                .containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("stats count by generation, life status and branch")
    void stats() {
        TreeStatsView stats = fixtures.treeQueryService.stats(admin, clan);

        assertThat(stats.personCount()).isEqualTo(4);
        assertThat(stats.unlinkedCount())
                .as("nobody has an account — which is goal G1, stated as a number")
                .isEqualTo(4);
        assertThat(stats.maxGeneration()).isEqualTo(3);
        assertThat(stats.generationCounts())
                .extracting(TreeStatsView.GenerationCount::generation)
                .containsExactly(1, 2, 3, null);
    }

    @Test
    @DisplayName("the payload carries the tree version the ETag is built from")
    void treeVersionTravelsWithThePayload() {
        long before = fixtures.treeQueryService.tree(admin, clan, null, null).treeVersion();
        fixtures.childOf(admin, clan, doi3, "Nguyễn Văn Cường", Gender.MALE);

        assertThat(fixtures.treeQueryService.tree(admin, clan, null, null).treeVersion())
                .isGreaterThan(before);
    }

    @Nested
    @DisplayName("the snapshot cache (B5.3)")
    class SnapshotCache {

        @Test
        @DisplayName("a second read at the same tree version does not scan again")
        void repeatReadIsServedFromTheCache() {
            fixtures.treeQueryService.tree(admin, clan, null, null);
            int afterFirst = fixtures.treeSnapshotCache.loads;

            fixtures.treeQueryService.tree(admin, clan, null, null);

            assertThat(fixtures.treeSnapshotCache.loads).isEqualTo(afterFirst);
        }

        @Test
        @DisplayName("an edit bumps the version, so the next read is a fresh scan and shows it")
        void anEditIsNeverServedStale() {
            TreeView before = fixtures.treeQueryService.tree(admin, clan, null, null);
            int afterFirst = fixtures.treeSnapshotCache.loads;

            fixtures.childOf(admin, clan, doi3, "Nguyễn Văn Cường", Gender.MALE);
            TreeView after = fixtures.treeQueryService.tree(admin, clan, null, null);

            assertThat(fixtures.treeSnapshotCache.loads)
                    .as("the version moved, so the old key is unreachable")
                    .isEqualTo(afterFirst + 1);
            assertThat(after.persons()).hasSize(before.persons().size() + 1);
            assertThat(after.treeVersion()).isGreaterThan(before.treeVersion());
        }

        @Test
        @DisplayName("the cache holds rows, not the payload — canEdit is still each caller's own")
        void canEditIsNotCached() {
            fixtures.treeQueryService.tree(admin, clan, null, null);

            UserId onlooker = fixtures.clanOrg.newUser("Người xem");
            join(onlooker, root);

            assertThat(fixtures.treeQueryService.tree(onlooker, clan, null, null).persons())
                    .as("a member with no TREE_EDIT edits nothing, cache hit or not")
                    .allMatch(person -> !person.canEdit());
            assertThat(fixtures.treeQueryService.tree(admin, clan, null, null).persons())
                    .allMatch(PersonSummaryView::canEdit);
        }

        @Test
        @DisplayName("stats read the same cached snapshot the tree read loaded")
        void statsShareTheSnapshot() {
            fixtures.treeQueryService.tree(admin, clan, null, null);
            int afterTree = fixtures.treeSnapshotCache.loads;

            fixtures.treeQueryService.stats(admin, clan);

            assertThat(fixtures.treeSnapshotCache.loads).isEqualTo(afterTree);
        }
    }

    @Test
    @DisplayName("every node's canEdit is the answer the resolver gives for that node (phase 5 §5)")
    void canEditAgreesWithTheResolverNodeByNode() {
        // A branch-scoped editor: the case where the flag and the decision could plausibly diverge,
        // because one is computed in a batch and the other one node at a time.
        BranchId chiHai = fixtures.clanOrg.newBranch(admin, clan, null, "Chi Hai");
        UserId editor = fixtures.clanOrg.newUser("Người sửa chi hai");
        join(editor, chiHai);
        fixtures.clanOrg.grantService.grant(
                admin, clan, new GrantCommand(editor, "TREE_EDIT", "BRANCH", chiHai.value(), null));
        fixtures.personService.moveToBranch(admin, clan, doi3, chiHai);

        TreeView tree = fixtures.treeQueryService.tree(editor, clan, null, null);

        assertThat(tree.persons()).isNotEmpty();
        for (PersonSummaryView node : tree.persons()) {
            boolean decided =
                    fixtures.clanOrg
                            .resolver
                            .decide(
                                    editor,
                                    Permission.TREE_EDIT,
                                    Target.person(
                                            clan,
                                            node.id(),
                                            node.branchId() == null
                                                    ? null
                                                    : BranchId.of(node.branchId())))
                            .allowed();
            assertThat(node.canEdit())
                    .as("canEdit for %s", node.fullName())
                    .isEqualTo(decided);
        }
        assertThat(tree.persons())
                .as("the branch-scoped editor really does reach part of the tree and not all of it")
                .anyMatch(PersonSummaryView::canEdit)
                .anyMatch(node -> !node.canEdit());
    }

    @Test
    @DisplayName("a soft-deleted person is absent from the tree and comes back on restore")
    void softDeleteHidesFromTheTree() {
        fixtures.personService.softDelete(admin, clan, doi3);

        assertThat(fixtures.treeQueryService.tree(admin, clan, null, null).persons()).hasSize(3);

        fixtures.personService.restore(admin, clan, doi3);
        assertThat(fixtures.treeQueryService.tree(admin, clan, null, null).persons()).hasSize(4);
    }

    @Nested
    @DisplayName("the read validator (#49)")
    class ReadValidator {

        @Test
        @DisplayName("the version it carries is the clan's, and a lineage edit moves it")
        void versionFollowsTheTree() {
            long before = fixtures.treeQueryService.treeValidator(admin, clan).version();

            fixtures.childOf(admin, clan, doi3, "Nguyễn Văn Cường", Gender.MALE);

            assertThat(fixtures.treeQueryService.treeValidator(admin, clan).version())
                    .isGreaterThan(before);
        }

        @Test
        @DisplayName("two members whose canEdit differs do not share a validator")
        void callersWithDifferentRightsDiffer() {
            UserId onlooker = fixtures.clanOrg.newUser("Người xem");
            join(onlooker, root);

            TreeQueryService.TreeValidator forAdmin =
                    fixtures.treeQueryService.treeValidator(admin, clan);
            TreeQueryService.TreeValidator forOnlooker =
                    fixtures.treeQueryService.treeValidator(onlooker, clan);

            assertThat(forOnlooker.version())
                    .as("the rows are the same for both of them")
                    .isEqualTo(forAdmin.version());
            assertThat(forOnlooker.editRights())
                    .as("the flags are not, so the validator must separate them")
                    .isNotEqualTo(forAdmin.editRights());
        }

        @Test
        @DisplayName("two members holding the same scopes do share one")
        void callersWithTheSameRightsMatch() {
            UserId first = fixtures.clanOrg.newUser("Người xem một");
            UserId second = fixtures.clanOrg.newUser("Người xem hai");
            join(first, root);
            join(second, root);

            assertThat(fixtures.treeQueryService.treeValidator(first, clan).editRights())
                    .as("a representation shared is a revalidation saved")
                    .isEqualTo(fixtures.treeQueryService.treeValidator(second, clan).editRights());
        }

        @Test
        @DisplayName("a grant changes it although the tree has not changed")
        void grantingChangesItWithoutTouchingTheTree() {
            BranchId chiHai = fixtures.clanOrg.newBranch(admin, clan, null, "Chi Hai");
            UserId editor = fixtures.clanOrg.newUser("Người sửa chi hai");
            join(editor, chiHai);

            TreeQueryService.TreeValidator before =
                    fixtures.treeQueryService.treeValidator(editor, clan);

            fixtures.clanOrg.grantService.grant(
                    admin,
                    clan,
                    new GrantCommand(editor, "TREE_EDIT", "BRANCH", chiHai.value(), null));

            TreeQueryService.TreeValidator after =
                    fixtures.treeQueryService.treeValidator(editor, clan);

            // This is the case a version counter cannot see: nobody was born, nobody moved, and
            // every canEdit in this caller's payload just changed.
            assertThat(after.version()).isEqualTo(before.version());
            assertThat(after.editRights()).isNotEqualTo(before.editRights());
        }

        @Test
        @DisplayName("it is stable across reads, so a client's cached tag keeps working")
        void stableAcrossReads() {
            assertThat(fixtures.treeQueryService.treeValidator(admin, clan))
                    .isEqualTo(fixtures.treeQueryService.treeValidator(admin, clan));
        }

        @Nested
        @DisplayName("the branch block of the payload (#54)")
        class BranchStructure {

            @Test
            @DisplayName("a rename changes the validator, with no lineage edit to move the version")
            void renameChangesIt() {
                BranchId chiHai = fixtures.clanOrg.newBranch(admin, clan, null, "Chi Hai");
                TreeQueryService.TreeValidator before =
                        fixtures.treeQueryService.treeValidator(admin, clan);

                rename(chiHai, "Chi Thứ Hai");

                TreeQueryService.TreeValidator after =
                        fixtures.treeQueryService.treeValidator(admin, clan);

                assertThat(after.version())
                        .as("nobody was born; the counter cannot see this")
                        .isEqualTo(before.version());
                assertThat(after.branchStructure()).isNotEqualTo(before.branchStructure());
            }

            @Test
            @DisplayName("so does a new branch, a move, and a root person being assigned")
            void everyBranchMutationChangesIt() {
                BranchId chiHai = fixtures.clanOrg.newBranch(admin, clan, null, "Chi Hai");
                String afterCreate = structure();

                BranchId chiBa = fixtures.clanOrg.newBranch(admin, clan, null, "Chi Ba");
                String afterSecondCreate = structure();
                assertThat(afterSecondCreate).isNotEqualTo(afterCreate);

                fixtures.clanOrg.branchService.move(admin, clan, chiBa, chiHai);
                String afterMove = structure();
                assertThat(afterMove)
                        .as("the path and depth it carries both changed")
                        .isNotEqualTo(afterSecondCreate);

                fixtures.clanOrg.branchService.assignRootPerson(admin, clan, chiHai, doi2.value());
                assertThat(structure())
                        .as("rootPersonId is in the payload too")
                        .isNotEqualTo(afterMove);
            }

            @Test
            @DisplayName("the tag and the body are built from one mapping, so they cannot disagree")
            void tagAndBodyAgree() {
                BranchId chiHai = fixtures.clanOrg.newBranch(admin, clan, null, "Chi Hai");
                String before = structure();

                rename(chiHai, "Chi Thứ Hai");

                // The invariant worth having: the fingerprint moved *and* the payload really does
                // carry the new name. A fingerprint over something the body does not contain would
                // pass the test above and still be wrong.
                assertThat(structure()).isNotEqualTo(before);
                assertThat(fixtures.treeQueryService.tree(admin, clan, null, null).branches())
                        .extracting(TreeView.TreeBranch::name)
                        .contains("Chi Thứ Hai");
            }

            @Test
            @DisplayName("a name carrying the separators cannot spell another branch's line")
            void aNameCannotForgeALine() {
                BranchId chiHai = fixtures.clanOrg.newBranch(admin, clan, null, "Chi Hai");
                String honest = structure();

                // The name is the one free-text field in the rendering, so it is the one that could
                // smuggle a delimiter. It is length-prefixed and last for exactly this reason.
                rename(chiHai, "Chi Hai;" + chiHai.value() + "|0|null|/forged/|1:z");

                assertThat(structure()).isNotEqualTo(honest);
            }

            private void rename(BranchId branchId, String name) {
                long version =
                        fixtures.clanOrg.branchService.detail(admin, clan, branchId).version();
                fixtures.clanOrg.branchService.update(
                        admin, clan, branchId, new UpdateBranchCommand(name, null, version));
            }

            private String structure() {
                return fixtures.treeQueryService.treeValidator(admin, clan).branchStructure();
            }
        }
    }

    /** Joins a second person to the clan through the ordinary flow, so their membership is real. */
    private void join(UserId user, BranchId homeBranch) {
        JoinRequestView request =
                fixtures.clanOrg.joinRequestService.submit(
                        user, new SubmitJoinRequestCommand(clanCode, null, null));
        fixtures.clanOrg.joinRequestService.approve(admin, clan, request.id(), homeBranch);
    }
}
