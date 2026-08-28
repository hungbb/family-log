package app.giaky.lineage.application;

import static org.assertj.core.api.Assertions.assertThat;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.domain.Gender;
import app.giaky.lineage.support.LineageTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link TreeQueryService} — the flat payload of doc 05 §4.9 (B4.9). */
class TreeQueryServiceTest {

    private LineageTestFixtures fixtures;
    private UserId admin;
    private ClanId clan;
    private BranchId root;

    private PersonId doi1;
    private PersonId doi2;
    private PersonId doi3;

    @BeforeEach
    void setUp() {
        fixtures = new LineageTestFixtures();
        admin = fixtures.clanOrg.newUser("Trưởng họ");
        clan = fixtures.clanOrg.newClan(admin, "Họ Nguyễn");
        root = fixtures.rootBranch(clan);

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

    @Test
    @DisplayName("a soft-deleted person is absent from the tree and comes back on restore")
    void softDeleteHidesFromTheTree() {
        fixtures.personService.softDelete(admin, clan, doi3);

        assertThat(fixtures.treeQueryService.tree(admin, clan, null, null).persons()).hasSize(3);

        fixtures.personService.restore(admin, clan, doi3);
        assertThat(fixtures.treeQueryService.tree(admin, clan, null, null).persons()).hasSize(4);
    }
}
