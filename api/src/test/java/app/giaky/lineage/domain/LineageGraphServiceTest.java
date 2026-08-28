package app.giaky.lineage.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.common.error.ApiException;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.application.AddParentageCommand;
import app.giaky.lineage.support.LineageTestFixtures;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link LineageGraphService} — I-R4 (no cycles) and I-R5 (generations cascade), B4.4.
 *
 * <p>Driven through the application services rather than by calling the domain service directly,
 * because what has to be true is that the <i>product</i> maintains generations, and the edge-writing
 * sequence is where that happens ({@code LineageEdges}).
 */
class LineageGraphServiceTest {

    private LineageTestFixtures fixtures;
    private UserId admin;
    private ClanId clan;
    private BranchId root;

    @BeforeEach
    void setUp() {
        fixtures = new LineageTestFixtures();
        admin = fixtures.clanOrg.newUser("Trưởng họ");
        clan = fixtures.clanOrg.newClan(admin, "Họ Nguyễn");
        root = fixtures.rootBranch(clan);
    }

    @Test
    @DisplayName("I-R5: a child is one đời below its parent, and the cascade runs deep")
    void generationsCascade() {
        PersonId doi1 = fixtures.founder(admin, clan, root, "Nguyễn Phúc Tổ");
        PersonId doi2 = fixtures.childOf(admin, clan, doi1, "Nguyễn Văn An", Gender.MALE);
        PersonId doi3 = fixtures.childOf(admin, clan, doi2, "Nguyễn Văn Bình", Gender.MALE);
        PersonId doi4 = fixtures.childOf(admin, clan, doi3, "Nguyễn Văn Cường", Gender.MALE);

        assertThat(generationOf(doi1)).isEqualTo(1);
        assertThat(generationOf(doi2)).isEqualTo(2);
        assertThat(generationOf(doi3)).isEqualTo(3);
        assertThat(generationOf(doi4)).isEqualTo(4);
    }

    @Test
    @DisplayName("attaching a whole subtree restamps every generation below it")
    void reparentingASubtreeRestampsIt() {
        PersonId founder = fixtures.founder(admin, clan, root, "Nguyễn Phúc Tổ");

        // A fragment transcribed on its own: nobody in it has a đời yet, because nothing above it
        // reaches the founding ancestor.
        PersonId orphanRoot = fixtures.unplaced(admin, clan, "Nguyễn Văn Xa");
        PersonId orphanChild = fixtures.childOf(admin, clan, orphanRoot, "Nguyễn Văn Gần", Gender.MALE);
        PersonId orphanGrandchild =
                fixtures.childOf(admin, clan, orphanChild, "Nguyễn Văn Út", Gender.MALE);

        assertThat(generationOf(orphanRoot)).isNull();
        assertThat(generationOf(orphanGrandchild)).isNull();

        fixtures.relationshipService.addParentage(
                admin,
                clan,
                new AddParentageCommand(founder, orphanRoot, ParentRole.FATHER, null, null));

        assertThat(generationOf(orphanRoot)).isEqualTo(2);
        assertThat(generationOf(orphanChild)).isEqualTo(3);
        assertThat(generationOf(orphanGrandchild))
                .as("the cascade does not stop at the node that changed")
                .isEqualTo(4);
    }

    @Test
    @DisplayName("removing the edge above a subtree takes its generations back to unknown")
    void removingAnEdgeUnsetsGenerations() {
        PersonId founder = fixtures.founder(admin, clan, root, "Nguyễn Phúc Tổ");
        PersonId child = fixtures.childOf(admin, clan, founder, "Nguyễn Văn An", Gender.MALE);
        PersonId grandchild = fixtures.childOf(admin, clan, child, "Nguyễn Văn Bình", Gender.MALE);

        var edge =
                fixtures.parentages.findByChild(clan, child).stream().findFirst().orElseThrow();
        fixtures.relationshipService.removeParentage(admin, clan, edge.id());

        assertThat(generationOf(child))
                .as("nothing above them now says which đời they are — and 1 would be a lie")
                .isNull();
        assertThat(generationOf(grandchild)).isNull();
    }

    @Test
    @DisplayName("I-R4: an edge that would close a loop is refused with 422 CYCLE_DETECTED")
    void cycleIsRefused() {
        PersonId founder = fixtures.founder(admin, clan, root, "Nguyễn Phúc Tổ");
        PersonId child = fixtures.childOf(admin, clan, founder, "Nguyễn Văn An", Gender.MALE);
        PersonId grandchild = fixtures.childOf(admin, clan, child, "Nguyễn Văn Bình", Gender.MALE);

        assertThatThrownBy(
                        () ->
                                fixtures.relationshipService.addParentage(
                                        admin,
                                        clan,
                                        new AddParentageCommand(
                                                grandchild, founder, ParentRole.FATHER, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.CYCLE_DETECTED);
    }

    @Test
    @DisplayName("I-R4: the degenerate loop — somebody made their own parent — is the same check")
    void selfParentIsACycle() {
        PersonId person = fixtures.founder(admin, clan, root, "Nguyễn Phúc Tổ");

        assertThatThrownBy(
                        () ->
                                fixtures.relationshipService.addParentage(
                                        admin,
                                        clan,
                                        new AddParentageCommand(
                                                person, person, ParentRole.FATHER, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.CYCLE_DETECTED);
    }

    @Test
    @DisplayName("with two parents at different đời, the child follows the nearer one to the founder")
    void generationTakesTheShortestPath() {
        PersonId founder = fixtures.founder(admin, clan, root, "Nguyễn Phúc Tổ");
        PersonId father = fixtures.childOf(admin, clan, founder, "Nguyễn Văn An", Gender.MALE);
        PersonId deeper = fixtures.childOf(admin, clan, father, "Nguyễn Văn Bình", Gender.MALE);

        // A wife who married in from further down another line — contrived, but it is exactly the
        // case where the two parents disagree and the rule has to say something.
        PersonId wife = fixtures.childOf(admin, clan, deeper, "Trần Thị Hoa", Gender.FEMALE);
        PersonId child = fixtures.childOf(admin, clan, father, "Nguyễn Văn Cường", Gender.MALE);

        fixtures.relationshipService.addParentage(
                admin, clan, new AddParentageCommand(wife, child, ParentRole.MOTHER, null, null));

        assertThat(generationOf(father)).isEqualTo(2);
        assertThat(generationOf(wife)).isEqualTo(4);
        assertThat(generationOf(child))
                .as("đời counts distance from the founding ancestor, so the nearer parent wins")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("siblings are everyone who shares at least one parent, in birth order")
    void siblingsShareAParent() {
        PersonId founder = fixtures.founder(admin, clan, root, "Nguyễn Phúc Tổ");
        PersonId first = childWithOrder(founder, "Nguyễn Văn Cả", 1);
        PersonId second = childWithOrder(founder, "Nguyễn Văn Hai", 2);
        PersonId third = childWithOrder(founder, "Nguyễn Văn Ba", 3);

        List<Person> siblings = fixtures.graph.siblingsOf(clan, second);

        assertThat(siblings).extracting(Person::id).containsExactly(first, third);
    }

    private PersonId childWithOrder(PersonId parent, String name, int order) {
        return PersonId.of(
                fixtures.personService
                        .create(
                                admin,
                                clan,
                                LineageTestFixtures.person(name, Gender.MALE)
                                        .birthOrder(order)
                                        .placement(
                                                app.giaky.lineage.application.PersonPlacement.childOf(
                                                        parent))
                                        .build())
                        .id());
    }

    private Integer generationOf(PersonId personId) {
        return fixtures.persons.findById(clan, personId).orElseThrow().generation();
    }
}
