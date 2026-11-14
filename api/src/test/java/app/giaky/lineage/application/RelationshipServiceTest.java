package app.giaky.lineage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.authorization.application.GrantCommand;
import app.giaky.clanorg.application.JoinRequestView;
import app.giaky.clanorg.application.SubmitJoinRequestCommand;
import app.giaky.common.error.ApiException;
import app.giaky.common.error.ConflictException;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PartialDate;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.domain.Gender;
import app.giaky.lineage.domain.ParentRole;
import app.giaky.lineage.domain.UnionStatus;
import app.giaky.lineage.domain.UnionType;
import app.giaky.lineage.support.LineageTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link RelationshipService} — the multi-target rule of doc 04 §5.2, and the union invariants.
 *
 * <p>The most important test in this file is {@code linkingAcrossBranchesIsRefused}: it is the
 * escape hatch the "all endpoints" rule exists to close, and the one a future refactor is most
 * likely to reopen by checking only the node being written.
 */
class RelationshipServiceTest {

    private LineageTestFixtures fixtures;
    private UserId admin;
    private UserId editorHai;
    private ClanId clan;
    private String clanCode;
    private BranchId chiHai;
    private BranchId chiBa;

    @BeforeEach
    void setUp() {
        fixtures = new LineageTestFixtures();
        admin = fixtures.clanOrg.newUser("Nguyễn Văn An");
        editorHai = fixtures.clanOrg.newUser("Nguyễn Văn Cường");

        clan = fixtures.clanOrg.newClan(admin, "Nguyễn Văn");
        clanCode = fixtures.clanOrg.clanService.detail(admin, clan).clanCode();
        chiHai = fixtures.clanOrg.newBranch(admin, clan, null, "Chi Hai");
        chiBa = fixtures.clanOrg.newBranch(admin, clan, null, "Chi Ba");

        JoinRequestView request =
                fixtures.clanOrg.joinRequestService.submit(
                        editorHai, new SubmitJoinRequestCommand(clanCode, null, null));
        fixtures.clanOrg.joinRequestService.approve(admin, clan, request.id(), chiHai);
        fixtures.clanOrg.grantService.grant(
                admin,
                clan,
                new GrantCommand(editorHai, "TREE_EDIT", "BRANCH", chiHai.value(), null));
    }

    @Nested
    @DisplayName("parentage")
    class Parentages {

        @Test
        @DisplayName("I-R3: a second father is refused with 409")
        void oneFatherPerChild() {
            PersonId child = personIn(chiHai, "Nguyễn Văn Út");
            PersonId father = personIn(chiHai, "Nguyễn Văn Cha");
            PersonId pretender = personIn(chiHai, "Nguyễn Văn Khác");

            fixtures.relationshipService.addParentage(admin, clan, link(father, child));

            assertThatThrownBy(
                            () ->
                                    fixtures.relationshipService.addParentage(
                                            admin, clan, link(pretender, child)))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.VERSION_CONFLICT);
        }

        @Test
        @DisplayName("doc 04 §5.2: linking a node in scope to one outside it is refused")
        void linkingAcrossBranchesIsRefused() {
            PersonId mine = personIn(chiHai, "Nguyễn Văn Hai");
            PersonId theirs = personIn(chiBa, "Nguyễn Văn Ba");

            assertThatThrownBy(
                            () ->
                                    fixtures.relationshipService.addParentage(
                                            editorHai, clan, link(theirs, mine)))
                    .as("otherwise a branch editor edits another chi by writing into their own")
                    .isInstanceOf(ApiException.class);

            assertThatThrownBy(
                            () ->
                                    fixtures.relationshipService.addParentage(
                                            editorHai, clan, link(mine, theirs)))
                    .as("and the same in the other direction")
                    .isInstanceOf(ApiException.class);
        }

        @Test
        @DisplayName("removing an edge needs permission on both endpoints too")
        void removalNeedsBothEnds() {
            PersonId mine = personIn(chiHai, "Nguyễn Văn Hai");
            PersonId theirs = personIn(chiBa, "Nguyễn Văn Ba");
            var edge =
                    fixtures.relationshipService.addParentage(admin, clan, link(theirs, mine));

            assertThatThrownBy(
                            () ->
                                    fixtures.relationshipService.removeParentage(
                                            editorHai, clan, edge.id()))
                    .isInstanceOf(ApiException.class);
        }

        @Test
        @DisplayName("nobody is recorded as both parents of the same child")
        void oneEdgePerParentAndChild() {
            PersonId parent = personIn(chiHai, "Nguyễn Văn Hai");
            PersonId child = personIn(chiHai, "Nguyễn Văn Con");
            fixtures.relationshipService.addParentage(admin, clan, link(parent, child));

            assertThatThrownBy(
                            () ->
                                    fixtures.relationshipService.addParentage(
                                            admin,
                                            clan,
                                            new AddParentageCommand(
                                                    parent, child, ParentRole.MOTHER, null, null)))
                    .as("the mother's role is free, but the person is already this child's father")
                    .isInstanceOf(ConflictException.class);
            assertThat(fixtures.parentages.findByChild(clan, child)).hasSize(1);
        }

        @Test
        @DisplayName("the parent's role defaults to their recorded gender")
        void roleFollowsGender() {
            PersonId child = personIn(chiHai, "Nguyễn Văn Út");
            PersonId mother = personIn(chiHai, "Trần Thị Hoa", Gender.FEMALE);

            var edge =
                    fixtures.relationshipService.addParentage(
                            admin,
                            clan,
                            new AddParentageCommand(mother, child, null, null, null));

            assertThat(edge.parentRole()).isEqualTo(ParentRole.MOTHER);
        }
    }

    @Nested
    @DisplayName("unions")
    class Unions {

        @Test
        @DisplayName("I-N2: the same couple cannot be recorded twice, in either order")
        void oneUnionPerPair() {
            PersonId husband = personIn(chiHai, "Nguyễn Văn Hai");
            PersonId wife = personIn(chiHai, "Trần Thị Hoa", Gender.FEMALE);

            fixtures.relationshipService.createUnion(admin, clan, union(husband, wife, UnionType.PRIMARY));

            assertThatThrownBy(
                            () ->
                                    fixtures.relationshipService.createUnion(
                                            admin, clan, union(wife, husband, UnionType.SECONDARY)))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.VERSION_CONFLICT);
        }

        @Test
        @DisplayName("I-N3: one principal marriage, but as many secondary ones as the book records")
        void secondWife() {
            PersonId husband = personIn(chiHai, "Nguyễn Văn Hai");
            PersonId first = personIn(chiHai, "Trần Thị Cả", Gender.FEMALE);
            PersonId second = personIn(chiHai, "Lê Thị Hai", Gender.FEMALE);
            PersonId third = personIn(chiHai, "Phạm Thị Ba", Gender.FEMALE);

            fixtures.relationshipService.createUnion(admin, clan, union(husband, first, UnionType.PRIMARY));
            fixtures.relationshipService.createUnion(
                    admin, clan, union(husband, second, UnionType.SECONDARY));

            assertThat(fixtures.unions.findByPartner(clan, husband)).hasSize(2);

            assertThatThrownBy(
                            () ->
                                    fixtures.relationshipService.createUnion(
                                            admin, clan, union(husband, third, UnionType.PRIMARY)))
                    .as("a second vợ cả is a contradiction; a vợ lẽ is not")
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.VERSION_CONFLICT);
        }

        @Test
        @DisplayName("I-N4: marrying an ancestor is refused, and an administrator may override")
        void ancestorMarriage() {
            PersonId ancestor = personIn(chiHai, "Nguyễn Văn Tổ");
            PersonId descendant =
                    fixtures.childOf(admin, clan, ancestor, "Nguyễn Văn Cháu", Gender.FEMALE);

            assertThatThrownBy(
                            () ->
                                    fixtures.relationshipService.createUnion(
                                            admin,
                                            clan,
                                            union(ancestor, descendant, UnionType.PRIMARY)))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.INVALID_RELATIONSHIP);

            UnionView forced =
                    fixtures.relationshipService.createUnion(
                            admin,
                            clan,
                            new CreateUnionCommand(
                                    ancestor,
                                    descendant,
                                    UnionType.PRIMARY,
                                    UnionStatus.UNKNOWN,
                                    PartialDate.unknown(),
                                    PartialDate.unknown(),
                                    null,
                                    true));

            assertThat(forced.id()).isNotNull();
        }

        @Test
        @DisplayName("only an administrator may force past I-N4")
        void forceNeedsAdmin() {
            PersonId ancestor = personIn(chiHai, "Nguyễn Văn Tổ");
            PersonId descendant =
                    fixtures.childOf(admin, clan, ancestor, "Nguyễn Văn Cháu", Gender.FEMALE);

            assertThatThrownBy(
                            () ->
                                    fixtures.relationshipService.createUnion(
                                            editorHai,
                                            clan,
                                            new CreateUnionCommand(
                                                    ancestor,
                                                    descendant,
                                                    UnionType.PRIMARY,
                                                    UnionStatus.UNKNOWN,
                                                    PartialDate.unknown(),
                                                    PartialDate.unknown(),
                                                    null,
                                                    true)))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.INVALID_RELATIONSHIP);
        }

        @Test
        @DisplayName("a union with children attached cannot be deleted")
        void deletingAUnionWithChildren() {
            PersonId husband = personIn(chiHai, "Nguyễn Văn Hai");
            PersonId wife = personIn(chiHai, "Trần Thị Hoa", Gender.FEMALE);
            UnionView created =
                    fixtures.relationshipService.createUnion(
                            admin, clan, union(husband, wife, UnionType.PRIMARY));
            PersonId child = personIn(chiHai, "Nguyễn Văn Con");

            fixtures.relationshipService.attachChildToUnion(admin, clan, created.id(), child);

            assertThatThrownBy(
                            () ->
                                    fixtures.relationshipService.deleteUnion(
                                            admin, clan, created.id()))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("1 child is recorded")
                    .hasMessageContaining("Nguyễn Văn Con")
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.INVALID_RELATIONSHIP);
        }

        @Test
        @DisplayName(
                "a removed child does not block deleting the marriage, and comes back to both"
                        + " parents (#50)")
        void deletingAUnionWhoseOnlyChildWasRemoved() {
            PersonId husband = personIn(chiHai, "Nguyễn Văn Hai");
            PersonId wife = personIn(chiHai, "Trần Thị Hoa", Gender.FEMALE);
            UnionView created =
                    fixtures.relationshipService.createUnion(
                            admin, clan, union(husband, wife, UnionType.PRIMARY));
            PersonId child = personIn(chiHai, "Nguyễn Văn Con");
            fixtures.relationshipService.attachChildToUnion(admin, clan, created.id(), child);
            fixtures.personService.softDelete(admin, clan, child);

            fixtures.relationshipService.deleteUnion(admin, clan, created.id());

            assertThat(fixtures.unions.findById(clan, created.id())).isEmpty();
            assertThat(fixtures.parentages.findByUnion(clan, created.id())).isEmpty();

            fixtures.personService.restore(admin, clan, child);
            assertThat(fixtures.parentages.findByChild(clan, child))
                    .allSatisfy(edge -> assertThat(edge.unionId()).isNull())
                    .extracting(edge -> edge.parentPersonId())
                    .containsExactlyInAnyOrder(husband, wife);
        }

        @Test
        @DisplayName("attaching a child writes an edge per partner, both naming the marriage")
        void attachChild() {
            PersonId husband = personIn(chiHai, "Nguyễn Văn Hai");
            PersonId wife = personIn(chiHai, "Trần Thị Hoa", Gender.FEMALE);
            UnionView created =
                    fixtures.relationshipService.createUnion(
                            admin, clan, union(husband, wife, UnionType.PRIMARY));
            PersonId child = personIn(chiHai, "Nguyễn Văn Con");

            var edges =
                    fixtures.relationshipService.attachChildToUnion(
                            admin, clan, created.id(), child);

            assertThat(edges).hasSize(2);
            assertThat(fixtures.parentages.findByChild(clan, child))
                    .extracting(edge -> edge.parentRole().name())
                    .containsExactlyInAnyOrder("FATHER", "MOTHER");

            // Idempotent enough to be usable: attaching again completes rather than conflicts.
            assertThat(
                            fixtures.relationshipService.attachChildToUnion(
                                    admin, clan, created.id(), child))
                    .isEmpty();
        }

        @Test
        @DisplayName("attaching a child records the man as father even when his wife is stored first")
        void attachChildWhenTheWifeIsPartnerA() {
            // She is created first, so hers is the smaller id and she is stored as partner A — the
            // arrangement in which a role picked by storage order alone would pick the wife.
            PersonId wife = personIn(chiHai, "Trần Thị Hoa", Gender.FEMALE);
            PersonId husband = personIn(chiHai, "Nguyễn Văn Hai", Gender.MALE);
            UnionView created =
                    fixtures.relationshipService.createUnion(
                            admin, clan, union(husband, wife, UnionType.PRIMARY));
            assertThat(created.partnerAId()).as("the precondition").isEqualTo(wife.value());
            PersonId child = personIn(chiHai, "Nguyễn Văn Con");

            fixtures.relationshipService.attachChildToUnion(admin, clan, created.id(), child);

            assertThat(fixtures.parentages.findEdge(clan, child, ParentRole.FATHER))
                    .get()
                    .extracting(edge -> edge.parentPersonId())
                    .isEqualTo(husband);
            assertThat(fixtures.parentages.findEdge(clan, child, ParentRole.MOTHER))
                    .get()
                    .extracting(edge -> edge.parentPersonId())
                    .isEqualTo(wife);
        }
    }

    // --- helpers ---------------------------------------------------------------------------------

    private PersonId personIn(BranchId branch, String name) {
        return personIn(branch, name, Gender.MALE);
    }

    /**
     * A person placed in a branch but attached to nobody.
     *
     * <p>Created unplaced and then moved, which is the only route the product offers: BRANCH_ROOT
     * places one person per chi (and is refused for a second), and every other placement hangs the
     * new node off somebody. That is the model working as intended — a chi has one ancestor and
     * everybody else descends from them — but a test that needs two unrelated people in one branch
     * has to go the long way round, and both steps need clan-wide TREE_EDIT.
     */
    private PersonId personIn(BranchId branch, String name, Gender gender) {
        PersonId person =
                PersonId.of(
                        fixtures.personService
                                .create(
                                        admin,
                                        clan,
                                        LineageTestFixtures.person(name, gender)
                                                .placement(PersonPlacement.unplaced())
                                                .build())
                                .id());
        fixtures.personService.moveToBranch(admin, clan, person, branch);
        return person;
    }

    private static AddParentageCommand link(PersonId parent, PersonId child) {
        return new AddParentageCommand(parent, child, ParentRole.FATHER, null, null);
    }

    private static CreateUnionCommand union(PersonId one, PersonId other, UnionType type) {
        return new CreateUnionCommand(
                one,
                other,
                type,
                UnionStatus.UNKNOWN,
                PartialDate.unknown(),
                PartialDate.unknown(),
                null,
                false);
    }
}
