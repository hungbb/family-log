package app.giaky.linking.application;

import static org.assertj.core.api.Assertions.assertThat;

import app.giaky.clanorg.application.MemberView;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.domain.Gender;
import app.giaky.linking.support.LinkingTestFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link LinkedMemberNodes}: the member list knows who is already in the tree (doc 05 §4.5).
 *
 * <p>Asserted on the member list itself, over the real graph, rather than on the adapter in
 * isolation — the adapter is one line, and the thing that was broken was the list: it answered
 * {@code linkedPersonId: null} for everybody, so the assign-a-member picker (F6.6) offered people
 * who already held a node and the server then refused them.
 */
class LinkedMemberNodesTest {

    private LinkingTestFixtures fixtures;

    private UserId founder;
    private ClanId clanId;
    private BranchId rootBranch;

    private PersonId ancestor;
    private PersonId son;

    private UserId member;
    private UserId cousin;

    @BeforeEach
    void setUp() {
        fixtures = new LinkingTestFixtures();
        founder = fixtures.lineage.clanOrg.newUser("Nguyễn Văn Tổ");
        clanId = fixtures.lineage.clanOrg.newClan(founder, "Nguyễn Văn");
        rootBranch = fixtures.lineage.rootBranch(clanId);

        ancestor = fixtures.lineage.founder(founder, clanId, rootBranch, "Nguyễn Văn Ất");
        son = fixtures.lineage.childOf(founder, clanId, ancestor, "Nguyễn Văn An", Gender.MALE);

        member = fixtures.newMember(clanId, rootBranch, "Nguyễn Văn An");
        cousin = fixtures.newMember(clanId, rootBranch, "Nguyễn Văn Em");
    }

    private UUID linkedPersonOf(UserId user) {
        List<MemberView> members =
                fixtures
                        .lineage
                        .clanOrg
                        .membershipService
                        .list(founder, clanId, null, null, 0, 50)
                        .content();
        return members.stream()
                .filter(view -> view.userId().equals(user.value()))
                .findFirst()
                .orElseThrow()
                .linkedPersonId();
    }

    @Test
    @DisplayName("a linked member is listed with their node; everybody else with none")
    void linkedMemberCarriesTheNode() {
        fixtures.personLinkService.assign(founder, clanId, son, member);

        assertThat(linkedPersonOf(member)).isEqualTo(son.value());
        assertThat(linkedPersonOf(cousin)).isNull();
        assertThat(linkedPersonOf(founder)).isNull();
    }

    @Test
    @DisplayName("a link made by approving a claim shows up the same way")
    void approvedClaimShowsUp() {
        UUID claim =
                fixtures
                        .claimService
                        .submit(member, clanId, new SubmitClaimCommand(son, "Con của ông Ất"))
                        .id();
        fixtures.claimService.approve(founder, clanId, claim);

        assertThat(linkedPersonOf(member)).isEqualTo(son.value());
    }

    @Test
    @DisplayName("unlinking clears it")
    void unlinkClearsIt() {
        fixtures.personLinkService.assign(founder, clanId, son, member);
        fixtures.personLinkService.unlink(member, clanId, son);

        assertThat(linkedPersonOf(member)).isNull();
    }

    @Test
    @DisplayName("a soft-deleted node is not a node anybody holds")
    void deletedNodeDoesNotCount() {
        fixtures.personLinkService.assign(founder, clanId, son, member);
        fixtures.lineage.personService.softDelete(founder, clanId, son);

        assertThat(linkedPersonOf(member)).isNull();
    }
}
