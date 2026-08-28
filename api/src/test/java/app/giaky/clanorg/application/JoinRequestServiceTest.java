package app.giaky.clanorg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.clanorg.application.event.JoinRequestDecided;
import app.giaky.clanorg.application.event.MembershipGranted;
import app.giaky.clanorg.domain.ClanJoinRequest;
import app.giaky.clanorg.domain.ClanMembership;
import app.giaky.clanorg.domain.JoinRequestStatus;
import app.giaky.clanorg.support.ClanOrgTestFixtures;
import app.giaky.common.audit.AuditAction;
import app.giaky.common.error.ConflictException;
import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.error.PermissionDeniedException;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.Paged;
import app.giaky.common.types.UserId;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link JoinRequestService}: asking to join, and the decision that creates a membership. */
class JoinRequestServiceTest {

    private ClanOrgTestFixtures fixtures;
    private UserId founder;
    private UserId applicant;
    private ClanId clanId;
    private String clanCode;

    @BeforeEach
    void setUp() {
        fixtures = new ClanOrgTestFixtures();
        founder = fixtures.newUser("Nguyễn Văn An");
        applicant = fixtures.newUser("Nguyễn Thị Bé");
        clanId = fixtures.newClan(founder, "Nguyễn Văn");
        clanCode = fixtures.clanService.detail(founder, clanId).clanCode();
    }

    private JoinRequestView submitByClanCode() {
        return fixtures.joinRequestService.submit(
                applicant, new SubmitJoinRequestCommand(clanCode, null, "Cháu nội cụ Ất"));
    }

    @Nested
    @DisplayName("submitting")
    class Submitting {

        @Test
        @DisplayName("a clan code produces a pending request against the clan as a whole")
        void submitByCode() {
            JoinRequestView request = submitByClanCode();

            assertThat(request.status()).isEqualTo(JoinRequestStatus.PENDING.name());
            assertThat(request.clanId()).isEqualTo(clanId.value());
            assertThat(request.targetBranchId()).isNull();
            assertThat(request.message()).isEqualTo("Cháu nội cụ Ất");
            assertThat(fixtures.audit.recorded(AuditAction.JOIN_REQUEST_SUBMITTED)).isTrue();
        }

        @Test
        @DisplayName("a branch code aims the request at that branch")
        void submitByBranchCode() {
            BranchId chiHai = fixtures.newBranch(founder, clanId, null, "Chi Hai");
            String branchCode =
                    fixtures.branches.findById(clanId, chiHai).orElseThrow().branchCode().value();

            JoinRequestView request =
                    fixtures.joinRequestService.submit(
                            applicant, new SubmitJoinRequestCommand(null, branchCode, null));

            assertThat(request.targetBranchId()).isEqualTo(chiHai.value());
            assertThat(request.targetBranchName()).isEqualTo("Chi Hai");
        }

        @Test
        @DisplayName("a second pending request for the same target is 409 DUPLICATE_PENDING_REQUEST")
        void duplicateIsRefused() {
            submitByClanCode();

            assertThatThrownBy(JoinRequestServiceTest.this::submitByClanCode)
                    .isInstanceOf(ConflictException.class)
                    .extracting(failure -> ((ConflictException) failure).code())
                    .isEqualTo(ErrorCode.DUPLICATE_PENDING_REQUEST);
        }

        @Test
        @DisplayName("a request for a different branch is not a duplicate (I-J1)")
        void differentTargetsAreNotDuplicates() {
            BranchId chiHai = fixtures.newBranch(founder, clanId, null, "Chi Hai");
            String branchCode =
                    fixtures.branches.findById(clanId, chiHai).orElseThrow().branchCode().value();

            submitByClanCode();
            JoinRequestView second =
                    fixtures.joinRequestService.submit(
                            applicant, new SubmitJoinRequestCommand(null, branchCode, null));

            assertThat(second.status()).isEqualTo(JoinRequestStatus.PENDING.name());
        }

        @Test
        @DisplayName("an existing member cannot ask to join again (I-J2)")
        void memberCannotReapply() {
            assertThatThrownBy(
                            () ->
                                    fixtures.joinRequestService.submit(
                                            founder, new SubmitJoinRequestCommand(clanCode, null, null)))
                    .isInstanceOf(ConflictException.class)
                    .extracting(failure -> ((ConflictException) failure).code())
                    .isEqualTo(ErrorCode.MEMBERSHIP_EXISTS);
        }

        @Test
        @DisplayName("giving both codes, or neither, is refused")
        void exactlyOneCode() {
            assertThatThrownBy(
                            () ->
                                    fixtures.joinRequestService.submit(
                                            applicant,
                                            new SubmitJoinRequestCommand(clanCode, "CHI-AAAA", null)))
                    .isInstanceOf(DomainRuleViolation.class);
            assertThatThrownBy(
                            () ->
                                    fixtures.joinRequestService.submit(
                                            applicant, new SubmitJoinRequestCommand(null, null, null)))
                    .isInstanceOf(DomainRuleViolation.class);
        }

        @Test
        @DisplayName("an archived branch is not accepting members")
        void archivedBranchRefusesRequests() {
            BranchId chiHai = fixtures.newBranch(founder, clanId, null, "Chi Hai");
            String branchCode =
                    fixtures.branches.findById(clanId, chiHai).orElseThrow().branchCode().value();
            fixtures.branchService.archive(founder, clanId, chiHai);

            assertThatThrownBy(
                            () ->
                                    fixtures.joinRequestService.submit(
                                            applicant,
                                            new SubmitJoinRequestCommand(null, branchCode, null)))
                    .isInstanceOf(DomainRuleViolation.class);
        }
    }

    @Nested
    @DisplayName("deciding")
    class Deciding {

        @Test
        @DisplayName("approval creates exactly one active membership")
        void approvalCreatesOneMembership() {
            JoinRequestView request = submitByClanCode();

            JoinRequestView decided =
                    fixtures.joinRequestService.approve(founder, clanId, request.id(), null);

            assertThat(decided.status()).isEqualTo(JoinRequestStatus.APPROVED.name());
            List<ClanMembership> active = fixtures.memberships.findAllActive(clanId);
            assertThat(active).hasSize(2);
            assertThat(fixtures.memberships.findActive(clanId, applicant)).isPresent();

            assertThat(fixtures.audit.recorded(AuditAction.MEMBERSHIP_GRANTED)).isTrue();
            assertThat(fixtures.events.ofType(MembershipGranted.class)).hasSize(1);
        }

        @Test
        @DisplayName("a second approval of the same request is 409, not a second membership")
        void doubleApprovalIsRefused() {
            JoinRequestView request = submitByClanCode();
            fixtures.joinRequestService.approve(founder, clanId, request.id(), null);

            // The case this exists for is a double-clicked button.
            assertThatThrownBy(
                            () ->
                                    fixtures.joinRequestService.approve(
                                            founder, clanId, request.id(), null))
                    .isInstanceOf(ConflictException.class);

            assertThat(fixtures.memberships.findAllActive(clanId)).hasSize(2);
        }

        @Test
        @DisplayName("the approver may place the new member in a branch")
        void approvalPlacesTheMember() {
            BranchId chiHai = fixtures.newBranch(founder, clanId, null, "Chi Hai");
            JoinRequestView request = submitByClanCode();

            fixtures.joinRequestService.approve(founder, clanId, request.id(), chiHai);

            assertThat(fixtures.memberships.findActive(clanId, applicant).orElseThrow().homeBranchId())
                    .isEqualTo(chiHai);
        }

        @Test
        @DisplayName("with no branch given, the one they asked for is used")
        void approvalFallsBackToTheRequestedBranch() {
            BranchId chiHai = fixtures.newBranch(founder, clanId, null, "Chi Hai");
            String branchCode =
                    fixtures.branches.findById(clanId, chiHai).orElseThrow().branchCode().value();
            JoinRequestView request =
                    fixtures.joinRequestService.submit(
                            applicant, new SubmitJoinRequestCommand(null, branchCode, null));

            fixtures.joinRequestService.approve(founder, clanId, request.id(), null);

            assertThat(fixtures.memberships.findActive(clanId, applicant).orElseThrow().homeBranchId())
                    .isEqualTo(chiHai);
        }

        @Test
        @DisplayName("a home branch from another clan is a 404")
        void crossClanHomeBranchIs404() {
            UserId other = fixtures.newUser("Trần Thị Bình");
            ClanId otherClan = fixtures.newClan(other, "Trần Gia");
            BranchId theirBranch = fixtures.newBranch(other, otherClan, null, "Chi của họ");
            JoinRequestView request = submitByClanCode();

            assertThatThrownBy(
                            () ->
                                    fixtures.joinRequestService.approve(
                                            founder, clanId, request.id(), theirBranch))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("rejection keeps the reason and creates no membership")
        void rejection() {
            JoinRequestView request = submitByClanCode();

            JoinRequestView decided =
                    fixtures.joinRequestService.reject(
                            founder, clanId, request.id(), "Không rõ là ai");

            assertThat(decided.status()).isEqualTo(JoinRequestStatus.REJECTED.name());
            assertThat(decided.decisionReason()).isEqualTo("Không rõ là ai");
            assertThat(fixtures.memberships.findActive(clanId, applicant)).isEmpty();
        }

        @Test
        @DisplayName("only the clan creator may decide, for now")
        void onlyCreatorMayDecide() {
            JoinRequestView request = submitByClanCode();
            UserId member = fixtures.newUser("Lê Văn Cường");
            JoinRequestView theirs =
                    fixtures.joinRequestService.submit(
                            member, new SubmitJoinRequestCommand(clanCode, null, null));
            fixtures.joinRequestService.approve(founder, clanId, theirs.id(), null);

            assertThatThrownBy(
                            () ->
                                    fixtures.joinRequestService.approve(
                                            member, clanId, request.id(), null))
                    .isInstanceOf(PermissionDeniedException.class);
        }

        @Test
        @DisplayName("a request id from another clan is a 404")
        void crossClanRequestIs404() {
            UserId other = fixtures.newUser("Trần Thị Bình");
            ClanId otherClan = fixtures.newClan(other, "Trần Gia");
            String otherCode = fixtures.clanService.detail(other, otherClan).clanCode();
            JoinRequestView theirRequest =
                    fixtures.joinRequestService.submit(
                            applicant, new SubmitJoinRequestCommand(otherCode, null, null));

            assertThatThrownBy(
                            () ->
                                    fixtures.joinRequestService.approve(
                                            founder, clanId, theirRequest.id(), null))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("the requester's own view")
    class RequesterView {

        @Test
        @DisplayName("cancelling withdraws it and frees the duplicate check")
        void cancelThenReapply() {
            JoinRequestView request = submitByClanCode();

            fixtures.joinRequestService.cancel(applicant, request.id());

            assertThat(fixtures.joinRequests.findPending(clanId, null, applicant)).isEmpty();
            // Withdrawing means the applicant may think again, which is the point of withdrawing.
            assertThat(submitByClanCode().status()).isEqualTo(JoinRequestStatus.PENDING.name());
        }

        @Test
        @DisplayName("somebody else's request id is a 404, not an authorization question")
        void cannotCancelSomebodyElsesRequest() {
            JoinRequestView request = submitByClanCode();
            UserId stranger = fixtures.newUser("Người lạ");

            assertThatThrownBy(() -> fixtures.joinRequestService.cancel(stranger, request.id()))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("my requests span clans I am not a member of")
        void myRequests() {
            UserId other = fixtures.newUser("Trần Thị Bình");
            ClanId otherClan = fixtures.newClan(other, "Trần Gia");
            String otherCode = fixtures.clanService.detail(other, otherClan).clanCode();

            submitByClanCode();
            fixtures.joinRequestService.submit(
                    applicant, new SubmitJoinRequestCommand(otherCode, null, null));

            List<JoinRequestView> mine = fixtures.joinRequestService.myRequests(applicant);

            assertThat(mine).hasSize(2);
            assertThat(mine).allSatisfy(view -> assertThat(view.clanName()).isNotBlank());
            // The applicant is in neither clan, so no other person's details come back.
            assertThat(mine).allSatisfy(view -> assertThat(view.requesterEmail()).isNull());
        }

        @Test
        @DisplayName("an approver sees who is asking; a requester sees which family they asked")
        void inboxCarriesTheRequesterDetails() {
            submitByClanCode();

            Paged<JoinRequestView> inbox =
                    fixtures.joinRequestService.inbox(
                            founder, clanId, JoinRequestStatus.PENDING, 0, 20);

            assertThat(inbox.totalElements()).isEqualTo(1);
            assertThat(inbox.content().get(0).requesterName()).isEqualTo("Nguyễn Thị Bé");
            assertThat(inbox.content().get(0).requesterEmail()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("expiry")
    class Expiry {

        @Test
        @DisplayName("a request nobody answers is closed after ninety days")
        void sweepClosesOverdueRequests() {
            JoinRequestView request = submitByClanCode();

            assertThat(fixtures.joinRequestService.expireOverdue()).isZero();

            fixtures.advance(ClanJoinRequest.LIFETIME.plus(Duration.ofSeconds(1)));
            assertThat(fixtures.joinRequestService.expireOverdue()).isEqualTo(1);

            assertThat(fixtures.joinRequests.findById(clanId, request.id()).orElseThrow().status())
                    .isEqualTo(JoinRequestStatus.EXPIRED);
            assertThat(fixtures.audit.recorded(AuditAction.JOIN_REQUEST_EXPIRED)).isTrue();
        }

        @Test
        @DisplayName("expiry has no actor, because nobody decided anything")
        void expiryHasNoDecider() {
            submitByClanCode();
            fixtures.advance(ClanJoinRequest.LIFETIME.plus(Duration.ofSeconds(1)));
            fixtures.joinRequestService.expireOverdue();

            List<JoinRequestDecided> decided =
                    fixtures.events.ofType(JoinRequestDecided.class).stream()
                            .filter(e -> e.outcome() == JoinRequestDecided.Outcome.EXPIRED)
                            .toList();

            assertThat(decided).hasSize(1);
            assertThat(decided.get(0).decidedBy()).isNull();
        }

        @Test
        @DisplayName("an already-decided request is not swept up again")
        void decidedRequestsAreLeftAlone() {
            JoinRequestView request = submitByClanCode();
            fixtures.joinRequestService.reject(founder, clanId, request.id(), "no");

            fixtures.advance(ClanJoinRequest.LIFETIME.plus(Duration.ofSeconds(1)));

            assertThat(fixtures.joinRequestService.expireOverdue()).isZero();
            assertThat(fixtures.joinRequests.findById(clanId, request.id()).orElseThrow().status())
                    .isEqualTo(JoinRequestStatus.REJECTED);
        }
    }

    @Test
    @DisplayName("an unknown request id is a 404")
    void unknownRequestIs404() {
        assertThatThrownBy(
                        () ->
                                fixtures.joinRequestService.approve(
                                        founder, clanId, UUID.randomUUID(), null))
                .isInstanceOf(NotFoundException.class);
    }
}
