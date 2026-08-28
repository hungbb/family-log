package app.giaky.clanorg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.clanorg.application.event.MembershipRevoked;
import app.giaky.clanorg.domain.ClanMembership;
import app.giaky.clanorg.domain.MembershipStatus;
import app.giaky.clanorg.support.ClanOrgTestFixtures;
import app.giaky.common.audit.AuditAction;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.error.PermissionDeniedException;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link MembershipService}: the member list, and the two ways out of a clan. */
class MembershipServiceTest {

    private ClanOrgTestFixtures fixtures;
    private UserId founder;
    private UserId member;
    private ClanId clanId;
    private String clanCode;

    @BeforeEach
    void setUp() {
        fixtures = new ClanOrgTestFixtures();
        founder = fixtures.newUser("Nguyễn Văn An");
        member = fixtures.newUser("Nguyễn Thị Bé");
        clanId = fixtures.newClan(founder, "Nguyễn Văn");
        clanCode = fixtures.clanService.detail(founder, clanId).clanCode();
    }

    /** Puts {@code user} in the clan the way the product does, optionally into a branch. */
    private void join(UserId user, BranchId homeBranch) {
        JoinRequestView request =
                fixtures.joinRequestService.submit(
                        user, new SubmitJoinRequestCommand(clanCode, null, null));
        fixtures.joinRequestService.approve(founder, clanId, request.id(), homeBranch);
    }

    @Test
    @DisplayName("the list carries names from Identity, which ClanOrg does not store")
    void listResolvesNames() {
        join(member, null);

        Paged<MemberView> page = fixtures.membershipService.list(founder, clanId, null, null, 0, 20);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).extracting(MemberView::displayName)
                .containsExactlyInAnyOrder("Nguyễn Văn An", "Nguyễn Thị Bé");
        // The founder holds the clan's bootstrap CLAN_ADMIN and the new member holds only
        // CLAN_VIEW, so the list names exactly one person to ask.
        assertThat(page.content()).filteredOn(MemberView::isAdministrator).hasSize(1);
        // Phase 6 fills this in.
        assertThat(page.content()).allSatisfy(view -> assertThat(view.linkedPersonId()).isNull());
    }

    @Test
    @DisplayName("filtering by branch narrows to that branch, and names it")
    void filterByBranch() {
        BranchId chiHai = fixtures.newBranch(founder, clanId, null, "Chi Hai");
        join(member, chiHai);

        Paged<MemberView> page =
                fixtures.membershipService.list(founder, clanId, null, chiHai, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().get(0).userId()).isEqualTo(member.value());
        assertThat(page.content().get(0).homeBranchName()).isEqualTo("Chi Hai");
    }

    @Test
    @DisplayName("searching intersects the clan roll with what Identity matched")
    void searchByName() {
        join(member, null);

        Paged<MemberView> page = fixtures.membershipService.list(founder, clanId, "Bé", null, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().get(0).displayName()).isEqualTo("Nguyễn Thị Bé");
    }

    @Test
    @DisplayName("a search that matches somebody in another clan returns nobody")
    void searchDoesNotLeakAcrossClans() {
        // The intersection is what makes this safe: Identity searches every account, and only the
        // ones that are also members of this clan survive.
        UserId stranger = fixtures.newUser("Người Ngoài");
        fixtures.newClan(stranger, "Trần Gia");

        Paged<MemberView> page =
                fixtures.membershipService.list(founder, clanId, "Người Ngoài", null, 0, 20);

        assertThat(page.totalElements()).isZero();
    }

    @Test
    @DisplayName("a non-member cannot read the list")
    void nonMemberCannotReadTheList() {
        UserId outsider = fixtures.newUser("Người lạ");

        assertThatThrownBy(
                        () -> fixtures.membershipService.list(outsider, clanId, null, null, 0, 20))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("removing a member ends the membership but keeps the row")
    void removeKeepsHistory() {
        join(member, null);

        fixtures.membershipService.remove(founder, clanId, member);

        assertThat(fixtures.memberships.findActive(clanId, member)).isEmpty();
        List<ClanMembership> all =
                fixtures.memberships.all().stream()
                        .filter(m -> m.userId().equals(member))
                        .toList();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).status()).isEqualTo(MembershipStatus.REMOVED);
        assertThat(all.get(0).leftAt()).isNotNull();

        assertThat(fixtures.audit.recorded(AuditAction.MEMBERSHIP_REVOKED)).isTrue();
        MembershipRevoked event = fixtures.events.ofType(MembershipRevoked.class).get(0);
        assertThat(event.selfInitiated()).isFalse();
    }

    @Test
    @DisplayName("leaving is recorded as leaving, not as being removed")
    void leaveIsDistinctFromRemoval() {
        join(member, null);

        fixtures.membershipService.leave(member, clanId);

        assertThat(fixtures.memberships.all().stream().filter(m -> m.userId().equals(member)))
                .allSatisfy(m -> assertThat(m.status()).isEqualTo(MembershipStatus.LEFT));
        assertThat(fixtures.audit.recorded(AuditAction.MEMBERSHIP_LEFT)).isTrue();
        assertThat(fixtures.events.ofType(MembershipRevoked.class).get(0).selfInitiated()).isTrue();
    }

    @Test
    @DisplayName("the founder cannot leave — the clan would have nobody to administer it (I-M3)")
    void founderCannotLeave() {
        assertThatThrownBy(() -> fixtures.membershipService.leave(founder, clanId))
                .isInstanceOf(PermissionDeniedException.class)
                .extracting(failure -> ((PermissionDeniedException) failure).code())
                .isEqualTo(ErrorCode.LAST_ADMIN_PROTECTED);
    }

    @Test
    @DisplayName("the founder cannot be removed either")
    void founderCannotBeRemoved() {
        assertThatThrownBy(() -> fixtures.membershipService.remove(founder, clanId, founder))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("a member cannot remove another member, for now")
    void onlyCreatorMayRemove() {
        UserId other = fixtures.newUser("Lê Văn Cường");
        join(member, null);
        join(other, null);

        assertThatThrownBy(() -> fixtures.membershipService.remove(member, clanId, other))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("removing somebody who is not a member is a 404")
    void removingANonMemberIs404() {
        UserId stranger = fixtures.newUser("Người lạ");

        assertThatThrownBy(() -> fixtures.membershipService.remove(founder, clanId, stranger))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("after leaving, the clan disappears from the switcher and reads become 404")
    void leavingRevokesAccess() {
        join(member, null);

        fixtures.membershipService.leave(member, clanId);

        assertThat(fixtures.clanService.listForUser(member)).isEmpty();
        assertThatThrownBy(() -> fixtures.clanService.detail(member, clanId))
                .isInstanceOf(NotFoundException.class);
    }
}
