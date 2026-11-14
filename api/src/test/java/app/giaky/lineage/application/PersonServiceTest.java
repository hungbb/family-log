package app.giaky.lineage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.authorization.application.GrantCommand;
import app.giaky.clanorg.application.JoinRequestView;
import app.giaky.clanorg.application.SubmitJoinRequestCommand;
import app.giaky.common.audit.AuditAction;
import app.giaky.common.error.ApiException;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PartialDate;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.domain.Gender;
import app.giaky.lineage.domain.LifeStatus;
import app.giaky.lineage.domain.PersonRepository;
import app.giaky.lineage.support.LineageTestFixtures;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link PersonService} — placement, editing, soft delete, restore and search (B4.6).
 *
 * <p>The fixture is the isolation clan of doc 04 §8 again: an administrator, Chi Hai and Chi Ba, and
 * an editor who holds {@code TREE_EDIT} over one of them. Most of what is worth asserting about this
 * service is what an editor of one chi <i>cannot</i> do to the other.
 */
class PersonServiceTest {

    private LineageTestFixtures fixtures;
    private UserId admin;
    private UserId editorHai;
    private ClanId clan;
    private String clanCode;
    private BranchId rootBranch;
    private BranchId chiHai;
    private BranchId chiBa;

    @BeforeEach
    void setUp() {
        fixtures = new LineageTestFixtures();
        admin = fixtures.clanOrg.newUser("Nguyễn Văn An");
        editorHai = fixtures.clanOrg.newUser("Nguyễn Văn Cường");

        clan = fixtures.clanOrg.newClan(admin, "Nguyễn Văn");
        clanCode = fixtures.clanOrg.clanService.detail(admin, clan).clanCode();
        rootBranch = fixtures.rootBranch(clan);
        chiHai = fixtures.clanOrg.newBranch(admin, clan, null, "Chi Hai");
        chiBa = fixtures.clanOrg.newBranch(admin, clan, null, "Chi Ba");

        join(editorHai, chiHai);
        grantTreeEdit(editorHai, chiHai);
    }

    @Nested
    @DisplayName("placement decides both the branch and the permission (doc 05 §4.7)")
    class Placement {

        @Test
        @DisplayName("a child inherits the parent's branch")
        void childInheritsBranch() {
            PersonId parent = personIn(chiHai, "Nguyễn Văn Hai");
            PersonId child = fixtures.childOf(admin, clan, parent, "Nguyễn Văn Út", Gender.MALE);

            assertThat(branchOf(child)).isEqualTo(chiHai);
        }

        @Test
        @DisplayName("a branch editor may add under their own chi")
        void editorMayAddInsideTheirBranch() {
            PersonId parent = personIn(chiHai, "Nguyễn Văn Hai");

            PersonDetailView added =
                    fixtures.personService.create(
                            editorHai,
                            clan,
                            LineageTestFixtures.person("Nguyễn Văn Mới", Gender.MALE)
                                    .placement(PersonPlacement.childOf(parent))
                                    .build());

            assertThat(added.branchId()).isEqualTo(chiHai.value());
            assertThat(added.canEdit()).isTrue();
        }

        @Test
        @DisplayName("and not under somebody else's — the check names the anchor, not the new node")
        void editorMayNotAddInsideAnotherBranch() {
            PersonId stranger = personIn(chiBa, "Nguyễn Văn Ba");

            assertThatThrownBy(
                            () ->
                                    fixtures.personService.create(
                                            editorHai,
                                            clan,
                                            LineageTestFixtures.person(
                                                            "Nguyễn Văn Trộm", Gender.MALE)
                                                    .placement(PersonPlacement.childOf(stranger))
                                                    .build()))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isIn(ErrorCode.OUT_OF_SCOPE, ErrorCode.PERMISSION_DENIED);
        }

        @Test
        @DisplayName("an unplaced node needs clan-wide TREE_EDIT (phase 4 §8)")
        void unplacedNeedsClanScope() {
            assertThatThrownBy(
                            () ->
                                    fixtures.personService.create(
                                            editorHai,
                                            clan,
                                            LineageTestFixtures.person("Vô danh", Gender.MALE)
                                                    .placement(PersonPlacement.unplaced())
                                                    .build()))
                    .as("otherwise 'create loose, then attach' is a route into any chi")
                    .isInstanceOf(ApiException.class);

            PersonId byAdmin = fixtures.unplaced(admin, clan, "Vô danh");
            assertThat(branchOf(byAdmin)).isNull();
        }

        @Test
        @DisplayName("a spouse placement records the marriage and no parentage")
        void spousePlacement() {
            PersonId husband = personIn(chiHai, "Nguyễn Văn Hai");
            PersonId wife = fixtures.spouseOf(admin, clan, husband, "Trần Thị Hoa", Gender.FEMALE);

            assertThat(fixtures.unions.findByPartner(clan, husband)).hasSize(1);
            assertThat(fixtures.parentages.findByChild(clan, wife)).isEmpty();
            assertThat(branchOf(wife))
                    .as("a wife joins the chi she married into")
                    .isEqualTo(chiHai);
        }

        @Test
        @DisplayName("a child of a union gets two parentage edges, both naming the marriage")
        void childOfUnion() {
            PersonId husband = personIn(chiHai, "Nguyễn Văn Hai");
            fixtures.spouseOf(admin, clan, husband, "Trần Thị Hoa", Gender.FEMALE);
            var union = fixtures.unions.findByPartner(clan, husband).get(0);

            PersonDetailView child =
                    fixtures.personService.create(
                            admin,
                            clan,
                            LineageTestFixtures.person("Nguyễn Văn Con", Gender.MALE)
                                    .placement(PersonPlacement.childOfUnion(union.id()))
                                    .build());

            assertThat(child.parents()).hasSize(2);
            assertThat(child.parents())
                    .allSatisfy(
                            parent ->
                                    assertThat(parent.parentageId()).isNotNull());
            assertThat(fixtures.parentages.findByChild(clan, PersonId.of(child.id())))
                    .allSatisfy(edge -> assertThat(edge.unionId()).isEqualTo(union.id()));
        }

        @Test
        @DisplayName("a BRANCH_ROOT placement names the person the chi descends from (I-C5)")
        void branchRootPlacement() {
            PersonId ancestor =
                    PersonId.of(
                            fixtures.personService
                                    .create(
                                            admin,
                                            clan,
                                            LineageTestFixtures.person(
                                                            "Nguyễn Văn Chi", Gender.MALE)
                                                    .placement(PersonPlacement.branchRoot(chiHai))
                                                    .build())
                                    .id());

            assertThat(fixtures.clanOrg.clanDirectory.branch(clan, chiHai).orElseThrow().rootPersonId())
                    .isEqualTo(ancestor.value());
        }

        @Test
        @DisplayName("I-P6: a clan has one founding ancestor")
        void oneFoundingAncestor() {
            fixtures.founder(admin, clan, rootBranch, "Nguyễn Phúc Tổ");

            assertThatThrownBy(
                            () ->
                                    fixtures.personService.create(
                                            admin,
                                            clan,
                                            LineageTestFixtures.person("Kẻ mạo nhận", Gender.MALE)
                                                    .foundingAncestor(true)
                                                    .placement(
                                                            PersonPlacement.branchRoot(rootBranch))
                                                    .build()))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.VERSION_CONFLICT);
        }
    }

    @Nested
    @DisplayName("editing")
    class Editing {

        @Test
        @DisplayName("a stale version is 409 VERSION_CONFLICT, not a silent overwrite")
        void staleVersionConflicts() {
            PersonId person = personIn(chiHai, "Nguyễn Văn Hai");
            PersonDetailView loaded = fixtures.personService.detail(admin, clan, person);

            fixtures.personService.patch(
                    admin, clan, person, update("Nguyễn Văn Hai (sửa)", loaded.version()));

            assertThatThrownBy(
                            () ->
                                    fixtures.personService.patch(
                                            admin,
                                            clan,
                                            person,
                                            update("Ghi đè", loaded.version())))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.VERSION_CONFLICT);
        }

        @Test
        @DisplayName("PATCH leaves untouched fields alone; PUT clears them")
        void patchMergesAndPutReplaces() {
            PersonId person = personIn(chiHai, "Nguyễn Văn Hai");
            fixtures.personService.patch(
                    admin,
                    clan,
                    person,
                    new UpdatePersonCommand(
                            null, null, null, null, null, null, null,
                            PartialDate.ofYear(1901), null, "Nam Định", null, null,
                            version(person)));

            assertThat(fixtures.persons.findById(clan, person).orElseThrow().burialPlace())
                    .isEqualTo("Nam Định");
            assertThat(fixtures.persons.findById(clan, person).orElseThrow().fullName())
                    .as("PATCH said nothing about the name")
                    .isEqualTo("Nguyễn Văn Hai");

            fixtures.personService.replace(
                    admin,
                    clan,
                    person,
                    new UpdatePersonCommand(
                            "Nguyễn Văn Hai", null, null, null, null, Gender.MALE,
                            LifeStatus.DECEASED, null, null, null, null, null, version(person)));

            assertThat(fixtures.persons.findById(clan, person).orElseThrow().burialPlace())
                    .as("PUT means what it says")
                    .isNull();
        }

        @Test
        @DisplayName("a person is created with a gender or not at all")
        void createRequiresGender() {
            assertThatThrownBy(
                            () ->
                                    fixtures.personService.create(
                                            admin,
                                            clan,
                                            LineageTestFixtures.person("Vô danh", null)
                                                    .placement(PersonPlacement.branchRoot(chiHai))
                                                    .build()))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.VALIDATION_FAILED);
        }

        @Test
        @DisplayName("PATCH without a gender keeps it; PUT without one is refused")
        void genderCannotBeCleared() {
            PersonId person = personIn(chiHai, "Trần Thị Hoa");
            fixtures.personService.replace(
                    admin,
                    clan,
                    person,
                    new UpdatePersonCommand(
                            "Trần Thị Hoa", null, null, null, null, Gender.FEMALE,
                            null, null, null, null, null, null, version(person)));

            fixtures.personService.patch(
                    admin, clan, person, update("Trần Thị Hoa (sửa)", version(person)));
            assertThat(fixtures.persons.findById(clan, person).orElseThrow().gender())
                    .isEqualTo(Gender.FEMALE);

            assertThatThrownBy(
                            () ->
                                    fixtures.personService.replace(
                                            admin,
                                            clan,
                                            person,
                                            new UpdatePersonCommand(
                                                    "Trần Thị Hoa", null, null, null, null, null,
                                                    null, null, null, null, null, null,
                                                    version(person))))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.VALIDATION_FAILED);
        }

        @Test
        @DisplayName("notes are sanitized on the way in, and a plain projection is stored with them")
        void notesAreSanitized() {
            PersonDetailView person =
                    fixtures.personService.create(
                            admin,
                            clan,
                            LineageTestFixtures.person("Nguyễn Văn Hai", Gender.MALE)
                                    .notesHtml(
                                            "<p>Di cư vào Nam năm 1954.</p>"
                                                    + "<script>alert('xss')</script>")
                                    .placement(PersonPlacement.branchRoot(chiHai))
                                    .build());

            assertThat(person.notesHtml()).doesNotContain("script").contains("Di cư");
            assertThat(fixtures.persons.findById(clan, PersonId.of(person.id())).orElseThrow()
                            .notesText())
                    .isEqualTo("Di cư vào Nam năm 1954.");
        }

        @Test
        @DisplayName("moving a branch needs permission on the destination too (doc 04 §5.2)")
        void moveNeedsBothEnds() {
            PersonId person = personIn(chiHai, "Nguyễn Văn Hai");

            assertThatThrownBy(
                            () ->
                                    fixtures.personService.moveToBranch(
                                            editorHai, clan, person, chiBa))
                    .isInstanceOf(ApiException.class);

            assertThat(fixtures.personService.moveToBranch(admin, clan, person, chiBa).branchId())
                    .isEqualTo(chiBa.value());
        }

        @Test
        @DisplayName("every mutation bumps the clan's tree version (B4.12)")
        void mutationsBumpTheTreeVersion() {
            long before = fixtures.treeVersions.current(clan);
            personIn(chiHai, "Nguyễn Văn Hai");

            assertThat(fixtures.treeVersions.current(clan)).isGreaterThan(before);
        }

        @Test
        @DisplayName("every mutation is audited with before and after")
        void mutationsAreAudited() {
            PersonId person = personIn(chiHai, "Nguyễn Văn Hai");
            fixtures.personService.patch(admin, clan, person, update("Đổi tên", version(person)));

            assertThat(fixtures.clanOrg.audit.actions())
                    .contains(AuditAction.PERSON_CREATED, AuditAction.PERSON_UPDATED);
        }
    }

    @Nested
    @DisplayName("soft delete and restore (FR-P10)")
    class Removal {

        @Test
        @DisplayName("a deleted person leaves the tree and comes back unchanged")
        void deleteThenRestore() {
            PersonId person = personIn(chiHai, "Nguyễn Văn Hai");

            fixtures.personService.softDelete(admin, clan, person);
            assertThat(fixtures.persons.findById(clan, person)).isEmpty();
            assertThat(fixtures.personService.recentlyDeleted(admin, clan))
                    .extracting(PersonSummaryView::id)
                    .contains(person.value());

            fixtures.personService.restore(admin, clan, person);
            assertThat(fixtures.persons.findById(clan, person)).isPresent();
        }

        @Test
        @DisplayName("restoring past the window is refused")
        void restoreWindow() {
            PersonId person = personIn(chiHai, "Nguyễn Văn Hai");
            fixtures.personService.softDelete(admin, clan, person);
            fixtures.clanOrg.advance(PersonService.RESTORE_WINDOW.plus(Duration.ofDays(1)));

            assertThatThrownBy(() -> fixtures.personService.restore(admin, clan, person))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.VALIDATION_FAILED);
        }

        @Test
        @DisplayName("deleting is refused when it would strand children outside the actor's scope")
        void strandingSomebodyElsesChildren() {
            PersonId parent = personIn(chiHai, "Nguyễn Văn Hai");
            PersonId child = fixtures.childOf(admin, clan, parent, "Nguyễn Văn Út", Gender.MALE);
            fixtures.personService.moveToBranch(admin, clan, child, chiBa);

            assertThatThrownBy(() -> fixtures.personService.softDelete(editorHai, clan, parent))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.INVALID_RELATIONSHIP);

            // The administrator can reach the child, so for them it is an ordinary removal.
            fixtures.personService.softDelete(admin, clan, parent);
            assertThat(fixtures.persons.findById(clan, parent)).isEmpty();
        }
    }

    @Nested
    @DisplayName("search (FR-P11)")
    class Search {

        @Test
        @DisplayName("diacritics are optional in the query and in the record")
        void diacriticInsensitive() {
            personIn(chiHai, "Nguyễn Văn An");

            assertThat(search("nguyen van an")).extracting(PersonSummaryView::fullName)
                    .containsExactly("Nguyễn Văn An");
            assertThat(search("NGUYỄN")).hasSize(1);
        }

        @Test
        @DisplayName("filters narrow by branch, life status and whether a node has an account")
        void filters() {
            personIn(chiHai, "Nguyễn Văn Hai");
            personIn(chiBa, "Nguyễn Văn Ba");

            assertThat(
                            fixtures.personService
                                    .search(
                                            admin,
                                            clan,
                                            new PersonRepository.PersonQuery(
                                                    null, null, null, chiBa, null, 0, 20))
                                    .content())
                    .extracting(PersonSummaryView::fullName)
                    .containsExactly("Nguyễn Văn Ba");

            assertThat(
                            fixtures.personService
                                    .search(
                                            admin,
                                            clan,
                                            new PersonRepository.PersonQuery(
                                                    null, null, null, null, true, 0, 20))
                                    .content())
                    .as("nobody is linked to an account yet, which is the point of G1")
                    .isEmpty();
        }

        @Test
        @DisplayName("a soft-deleted person is gone from search until they are restored")
        void deletedAreHidden() {
            PersonId person = personIn(chiHai, "Nguyễn Văn Hai");
            fixtures.personService.softDelete(admin, clan, person);

            assertThat(search("Nguyễn")).isEmpty();

            fixtures.personService.restore(admin, clan, person);
            assertThat(search("Nguyễn")).hasSize(1);
        }

        @Test
        @DisplayName("canEdit is stamped per node from the caller's own scope")
        void canEditIsPerCaller() {
            personIn(chiHai, "Nguyễn Văn Hai");
            personIn(chiBa, "Nguyễn Văn Ba");

            assertThat(fixtures.personService.search(editorHai, clan, all()).content())
                    .filteredOn(PersonSummaryView::canEdit)
                    .extracting(PersonSummaryView::fullName)
                    .containsExactly("Nguyễn Văn Hai");
        }

        private java.util.List<PersonSummaryView> search(String text) {
            return fixtures.personService
                    .search(
                            admin,
                            clan,
                            new PersonRepository.PersonQuery(text, null, null, null, null, 0, 20))
                    .content();
        }

        private PersonRepository.PersonQuery all() {
            return new PersonRepository.PersonQuery(null, null, null, null, null, 0, 50);
        }
    }

    // --- helpers ---------------------------------------------------------------------------------

    private PersonId personIn(BranchId branch, String name) {
        return PersonId.of(
                fixtures.personService
                        .create(
                                admin,
                                clan,
                                LineageTestFixtures.person(name, Gender.MALE)
                                        .placement(PersonPlacement.branchRoot(branch))
                                        .build())
                        .id());
    }

    private UpdatePersonCommand update(String fullName, long version) {
        return new UpdatePersonCommand(
                fullName, null, null, null, null, null, null, null, null, null, null, null,
                version);
    }

    private long version(PersonId person) {
        return fixtures.persons.findById(clan, person).orElseThrow().version();
    }

    private BranchId branchOf(PersonId person) {
        return fixtures.persons.findById(clan, person).orElseThrow().branchId();
    }

    private void join(UserId user, BranchId homeBranch) {
        JoinRequestView request =
                fixtures.clanOrg.joinRequestService.submit(
                        user, new SubmitJoinRequestCommand(clanCode, null, null));
        fixtures.clanOrg.joinRequestService.approve(admin, clan, request.id(), homeBranch);
    }

    private void grantTreeEdit(UserId user, BranchId branch) {
        fixtures.clanOrg.grantService.grant(
                admin,
                clan,
                new GrantCommand(user, "TREE_EDIT", "BRANCH", branch.value(), null));
    }
}
