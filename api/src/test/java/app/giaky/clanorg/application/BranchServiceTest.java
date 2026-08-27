package app.giaky.clanorg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.clanorg.application.event.BranchMoved;
import app.giaky.clanorg.domain.Branch;
import app.giaky.clanorg.support.ClanOrgTestFixtures;
import app.giaky.common.audit.AuditAction;
import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link BranchService}: the tree, and every way of asking for a shape it may not have. */
class BranchServiceTest {

    private ClanOrgTestFixtures fixtures;
    private UserId founder;
    private ClanId clanId;
    private BranchId root;

    @BeforeEach
    void setUp() {
        fixtures = new ClanOrgTestFixtures();
        founder = fixtures.newUser("Nguyễn Văn An");
        clanId = fixtures.newClan(founder, "Nguyễn Văn");
        root = BranchId.of(fixtures.clanService.detail(founder, clanId).rootBranchId());
    }

    @Nested
    @DisplayName("creating")
    class Creating {

        @Test
        @DisplayName("a branch with no parent named hangs from the root")
        void defaultsToRoot() {
            BranchView chi =
                    fixtures.branchService.create(
                            founder, clanId, new CreateBranchCommand(null, "Chi Trưởng", null));

            assertThat(chi.parentBranchId()).isEqualTo(root.value());
            assertThat(chi.depth()).isEqualTo(1);
            assertThat(fixtures.audit.recorded(AuditAction.BRANCH_CREATED)).isTrue();
        }

        @Test
        @DisplayName("each branch gets its own shareable code")
        void branchesGetCodes() {
            BranchView first =
                    fixtures.branchService.create(
                            founder, clanId, new CreateBranchCommand(null, "Chi Trưởng", null));
            BranchView second =
                    fixtures.branchService.create(
                            founder, clanId, new CreateBranchCommand(null, "Chi Hai", null));

            assertThat(first.branchCode()).isNotEqualTo(second.branchCode());
            assertThat(first.branchCode()).startsWith("CHITRUONG-");
        }

        @Test
        @DisplayName("nesting stops at ten (I-C7)")
        void depthCapIsEnforced() {
            BranchId parent = root;
            for (int depth = 1; depth <= Branch.MAX_DEPTH; depth++) {
                parent =
                        BranchId.of(
                                fixtures.branchService
                                        .create(
                                                founder,
                                                clanId,
                                                new CreateBranchCommand(parent, "Đời " + depth, null))
                                        .id());
            }

            BranchId deepest = parent;
            assertThatThrownBy(
                            () ->
                                    fixtures.branchService.create(
                                            founder,
                                            clanId,
                                            new CreateBranchCommand(deepest, "one too many", null)))
                    .isInstanceOf(DomainRuleViolation.class);
        }

        @Test
        @DisplayName("nothing can be added under an archived branch")
        void noChildrenUnderArchived() {
            BranchId chi = fixtures.newBranch(founder, clanId, null, "Chi Hai");
            fixtures.branchService.archive(founder, clanId, chi);

            assertThatThrownBy(
                            () ->
                                    fixtures.branchService.create(
                                            founder, clanId, new CreateBranchCommand(chi, "Nhánh", null)))
                    .isInstanceOf(DomainRuleViolation.class);
        }

        @Test
        @DisplayName("a parent id from another clan is a 404, not somebody else's branch")
        void crossClanParentIs404() {
            UserId other = fixtures.newUser("Trần Thị Bình");
            ClanId otherClan = fixtures.newClan(other, "Trần Gia");
            BranchId theirBranch = fixtures.newBranch(other, otherClan, null, "Chi của họ");

            assertThatThrownBy(
                            () ->
                                    fixtures.branchService.create(
                                            founder,
                                            clanId,
                                            new CreateBranchCommand(theirBranch, "Chi Hai", null)))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("the tree")
    class Tree {

        @Test
        @DisplayName("comes back nested from the root, with member counts")
        void nestedTree() {
            BranchId chiHai = fixtures.newBranch(founder, clanId, null, "Chi Hai");
            fixtures.newBranch(founder, clanId, chiHai, "Nhánh An Giang");
            fixtures.newBranch(founder, clanId, null, "Chi Trưởng");

            List<BranchView> tree = fixtures.branchService.tree(founder, clanId);

            assertThat(tree).hasSize(1);
            BranchView rootView = tree.get(0);
            assertThat(rootView.children()).hasSize(2);
            // The founder's home branch is the root, so that is where the one member sits.
            assertThat(rootView.memberCount()).isEqualTo(1);

            BranchView chiHaiView =
                    rootView.children().stream()
                            .filter(child -> child.name().equals("Chi Hai"))
                            .findFirst()
                            .orElseThrow();
            assertThat(chiHaiView.children()).hasSize(1);
            assertThat(chiHaiView.children().get(0).name()).isEqualTo("Nhánh An Giang");
        }

        @Test
        @DisplayName("a non-member cannot read it at all")
        void nonMemberCannotReadTree() {
            UserId outsider = fixtures.newUser("Trần Thị Bình");

            assertThatThrownBy(() -> fixtures.branchService.tree(outsider, clanId))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("moving")
    class Moving {

        @Test
        @DisplayName("a move rewrites the path of every branch below it (I-C4)")
        void moveRewritesDescendants() {
            BranchId chiTruong = fixtures.newBranch(founder, clanId, null, "Chi Trưởng");
            BranchId chiHai = fixtures.newBranch(founder, clanId, null, "Chi Hai");
            BranchId anGiang = fixtures.newBranch(founder, clanId, chiHai, "Nhánh An Giang");
            BranchId longXuyen = fixtures.newBranch(founder, clanId, anGiang, "Nhánh Long Xuyên");

            fixtures.branchService.move(founder, clanId, anGiang, chiTruong);

            Branch moved = fixtures.branches.findById(clanId, anGiang).orElseThrow();
            Branch child = fixtures.branches.findById(clanId, longXuyen).orElseThrow();
            Branch newParent = fixtures.branches.findById(clanId, chiTruong).orElseThrow();

            assertThat(moved.parentBranchId()).isEqualTo(chiTruong);
            assertThat(moved.path().isStrictDescendantOf(newParent.path())).isTrue();
            // The descendant came with it, and its depth moved by the same amount.
            assertThat(child.path().isStrictDescendantOf(moved.path())).isTrue();
            assertThat(child.depth()).isEqualTo(moved.depth() + 1);

            List<BranchMoved> events = fixtures.events.ofType(BranchMoved.class);
            assertThat(events).hasSize(1);
            assertThat(events.get(0).descendantsRewritten()).isEqualTo(1);
            assertThat(fixtures.audit.recorded(AuditAction.BRANCH_MOVED)).isTrue();
        }

        @Test
        @DisplayName("moving a branch under its own descendant is refused as a cycle (I-C3)")
        void cycleIsRefused() {
            BranchId chiHai = fixtures.newBranch(founder, clanId, null, "Chi Hai");
            BranchId anGiang = fixtures.newBranch(founder, clanId, chiHai, "Nhánh An Giang");

            assertThatThrownBy(() -> fixtures.branchService.move(founder, clanId, chiHai, anGiang))
                    .isInstanceOf(DomainRuleViolation.class)
                    .extracting(failure -> ((DomainRuleViolation) failure).code())
                    .isEqualTo(ErrorCode.CYCLE_DETECTED);
        }

        @Test
        @DisplayName("a move that would push a descendant past the depth cap is refused")
        void subtreeDepthIsChecked() {
            // A two-deep subtree hanging at depth 1, and a chain nine deep to move it onto: the
            // moved branch itself would fit, and its leaf would not.
            BranchId subtreeRoot = fixtures.newBranch(founder, clanId, null, "Chi Hai");
            BranchId leaf = fixtures.newBranch(founder, clanId, subtreeRoot, "Nhánh");

            BranchId deep = root;
            for (int depth = 1; depth <= Branch.MAX_DEPTH - 1; depth++) {
                deep = fixtures.newBranch(founder, clanId, deep, "Đời " + depth);
            }

            BranchId destination = deep;
            assertThatThrownBy(
                            () -> fixtures.branchService.move(founder, clanId, subtreeRoot, destination))
                    .isInstanceOf(DomainRuleViolation.class)
                    .hasMessageContaining("descendant");

            // And nothing moved: the check happens before anything is written.
            assertThat(fixtures.branches.findById(clanId, leaf).orElseThrow().depth()).isEqualTo(2);
        }

        @Test
        @DisplayName("the root branch cannot be moved")
        void rootCannotMove() {
            BranchId chiHai = fixtures.newBranch(founder, clanId, null, "Chi Hai");

            assertThatThrownBy(() -> fixtures.branchService.move(founder, clanId, root, chiHai))
                    .isInstanceOf(DomainRuleViolation.class);
        }

        @Test
        @DisplayName("a branch id from another clan is a 404 on both ends of the move")
        void crossClanMoveIs404() {
            UserId other = fixtures.newUser("Trần Thị Bình");
            ClanId otherClan = fixtures.newClan(other, "Trần Gia");
            BranchId theirBranch = fixtures.newBranch(other, otherClan, null, "Chi của họ");
            BranchId mine = fixtures.newBranch(founder, clanId, null, "Chi Hai");

            assertThatThrownBy(() -> fixtures.branchService.move(founder, clanId, mine, theirBranch))
                    .isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> fixtures.branchService.move(founder, clanId, theirBranch, mine))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("archiving")
    class Archiving {

        @Test
        @DisplayName("a branch with live children cannot be archived (I-C6)")
        void liveChildrenBlockArchiving() {
            BranchId chiHai = fixtures.newBranch(founder, clanId, null, "Chi Hai");
            fixtures.newBranch(founder, clanId, chiHai, "Nhánh An Giang");

            assertThatThrownBy(() -> fixtures.branchService.archive(founder, clanId, chiHai))
                    .isInstanceOf(DomainRuleViolation.class)
                    .hasMessageContaining("sub-branches");
        }

        @Test
        @DisplayName("archiving the children first unblocks it")
        void archivingBottomUpWorks() {
            BranchId chiHai = fixtures.newBranch(founder, clanId, null, "Chi Hai");
            BranchId anGiang = fixtures.newBranch(founder, clanId, chiHai, "Nhánh An Giang");

            fixtures.branchService.archive(founder, clanId, anGiang);
            BranchView archived = fixtures.branchService.archive(founder, clanId, chiHai);

            assertThat(archived.archived()).isTrue();
            assertThat(archived.archivedAt()).isNotNull();
            assertThat(fixtures.audit.recorded(AuditAction.BRANCH_ARCHIVED)).isTrue();
        }

        @Test
        @DisplayName("the root branch is the clan and cannot be archived")
        void rootCannotBeArchived() {
            assertThatThrownBy(() -> fixtures.branchService.archive(founder, clanId, root))
                    .isInstanceOf(DomainRuleViolation.class);
        }
    }

    @Test
    @DisplayName("a member who did not create the clan cannot restructure it")
    void onlyCreatorMayRestructure() {
        UserId member = fixtures.newUser("Lê Văn Cường");
        var request =
                fixtures.joinRequestService.submit(
                        member,
                        new SubmitJoinRequestCommand(
                                fixtures.clanService.detail(founder, clanId).clanCode(), null, null));
        fixtures.joinRequestService.approve(founder, clanId, request.id(), null);

        assertThatThrownBy(
                        () ->
                                fixtures.branchService.create(
                                        member, clanId, new CreateBranchCommand(null, "Chi Ba", null)))
                .isInstanceOf(app.giaky.common.error.PermissionDeniedException.class);
    }

    @Test
    @DisplayName("an unknown branch id is a 404")
    void unknownBranchIs404() {
        assertThatThrownBy(
                        () ->
                                fixtures.branchService.detail(
                                        founder, clanId, BranchId.of(UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);
    }
}
