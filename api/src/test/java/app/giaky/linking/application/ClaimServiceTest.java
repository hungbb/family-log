package app.giaky.linking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.authorization.application.GrantCommand;
import app.giaky.authorization.domain.Permission;
import app.giaky.common.audit.AuditAction;
import app.giaky.common.error.ConflictException;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.error.PermissionDeniedException;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.domain.Gender;
import app.giaky.linking.application.event.ClaimDecided;
import app.giaky.linking.application.event.ClaimSubmitted;
import app.giaky.linking.application.event.PersonLinked;
import app.giaky.linking.domain.ClaimStatus;
import app.giaky.linking.support.LinkingTestFixtures;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ClaimService}: the claim workflow end to end, over the real object graph.
 *
 * <p>The fixture clan is the demo one: a founding ancestor, two chi, a clan administrator, and a
 * branch-scoped approver for Chi Hai. That shape is what makes the scope questions in here mean
 * something — an approver who covers everything cannot demonstrate that routing works.
 */
class ClaimServiceTest {

    private LinkingTestFixtures fixtures;

    private UserId founder;
    private ClanId clanId;
    private BranchId rootBranch;
    private BranchId chiHai;
    private BranchId chiBa;

    private PersonId ancestor;
    private PersonId sonInChiHai;
    private PersonId sonInChiBa;

    private UserId member;
    private UserId chiHaiApprover;

    @BeforeEach
    void setUp() {
        fixtures = new LinkingTestFixtures();
        founder = fixtures.lineage.clanOrg.newUser("Nguyễn Văn Tổ");
        clanId = fixtures.lineage.clanOrg.newClan(founder, "Nguyễn Văn");
        rootBranch = fixtures.lineage.rootBranch(clanId);
        chiHai = fixtures.lineage.clanOrg.newBranch(founder, clanId, rootBranch, "Chi Hai");
        chiBa = fixtures.lineage.clanOrg.newBranch(founder, clanId, rootBranch, "Chi Ba");

        ancestor = fixtures.lineage.founder(founder, clanId, rootBranch, "Nguyễn Văn Ất");
        sonInChiHai =
                fixtures.lineage.childOf(founder, clanId, ancestor, "Nguyễn Văn An", Gender.MALE);
        sonInChiBa =
                fixtures.lineage.childOf(founder, clanId, ancestor, "Nguyễn Văn Bảo", Gender.MALE);
        fixtures.lineage.personService.moveToBranch(founder, clanId, sonInChiHai, chiHai);
        fixtures.lineage.personService.moveToBranch(founder, clanId, sonInChiBa, chiBa);

        member = fixtures.newMember(clanId, chiHai, "Nguyễn Văn An");
        chiHaiApprover = fixtures.newMember(clanId, chiHai, "Bác Hai");
        grant(chiHaiApprover, Permission.NODE_CLAIM_APPROVE, "BRANCH", chiHai);
    }

    private void grant(UserId grantee, Permission permission, String scopeType, BranchId scopeRef) {
        fixtures.lineage.clanOrg.grantService.grant(
                founder,
                clanId,
                new GrantCommand(
                        grantee,
                        permission.name(),
                        scopeType,
                        scopeRef == null ? null : scopeRef.value(),
                        null));
    }

    private NodeClaimView claim(UserId caller, PersonId personId) {
        return fixtures.claimService.submit(
                caller, clanId, new SubmitClaimCommand(personId, "Con của ông Ất"));
    }

    @Nested
    @DisplayName("submitting")
    class Submitting {

        @Test
        @DisplayName("a member may claim an unlinked node, and the claim is pending")
        void submit() {
            NodeClaimView view = claim(member, sonInChiHai);

            assertThat(view.status()).isEqualTo(ClaimStatus.PENDING.name());
            assertThat(view.personId()).isEqualTo(sonInChiHai.value());
            assertThat(view.evidence()).isEqualTo("Con của ông Ất");
            assertThat(fixtures.lineage.clanOrg.audit.recorded(AuditAction.CLAIM_SUBMITTED)).isTrue();
            assertThat(fixtures.lineage.clanOrg.events.ofType(ClaimSubmitted.class)).hasSize(1);
        }

        @Test
        @DisplayName("I-K3: a second claim on the same node is 409")
        void duplicatePending() {
            claim(member, sonInChiHai);

            assertThatThrownBy(() -> claim(member, sonInChiHai))
                    .isInstanceOf(ConflictException.class)
                    .extracting(failure -> ((ConflictException) failure).code())
                    .isEqualTo(ErrorCode.DUPLICATE_PENDING_REQUEST);
        }

        @Test
        @DisplayName("I-P4: claiming a second node once linked is 409 USER_ALREADY_LINKED_IN_CLAN")
        void alreadyLinkedInClan() {
            NodeClaimView first = claim(member, sonInChiHai);
            fixtures.claimService.approve(chiHaiApprover, clanId, first.id());

            assertThatThrownBy(() -> claim(member, sonInChiBa))
                    .isInstanceOf(ConflictException.class)
                    .extracting(failure -> ((ConflictException) failure).code())
                    .isEqualTo(ErrorCode.USER_ALREADY_LINKED_IN_CLAN);
        }

        @Test
        @DisplayName("I-K1: claiming a node somebody already holds is 409 NODE_ALREADY_LINKED")
        void nodeAlreadyLinked() {
            UserId other = fixtures.newMember(clanId, chiHai, "Người khác");
            fixtures.personLinkService.assign(founder, clanId, sonInChiHai, other);

            assertThatThrownBy(() -> claim(member, sonInChiHai))
                    .isInstanceOf(ConflictException.class)
                    .extracting(failure -> ((ConflictException) failure).code())
                    .isEqualTo(ErrorCode.NODE_ALREADY_LINKED);
        }

        @Test
        @DisplayName("I-K2: somebody who is not a member is told the clan does not exist")
        void nonMember() {
            UserId stranger = fixtures.lineage.clanOrg.newUser("Người lạ");

            assertThatThrownBy(() -> claim(stranger, sonInChiHai))
                    .isInstanceOf(NotFoundException.class)
                    .extracting(failure -> ((NotFoundException) failure).code())
                    .isEqualTo(ErrorCode.CLAN_NOT_FOUND);
        }

        @Test
        @DisplayName("the requester may withdraw their own claim")
        void cancel() {
            NodeClaimView view = claim(member, sonInChiHai);

            fixtures.claimService.cancel(member, view.id());

            assertThat(fixtures.claimService.myClaims(member))
                    .singleElement()
                    .extracting(NodeClaimView::status)
                    .isEqualTo(ClaimStatus.CANCELLED.name());
        }

        @Test
        @DisplayName("somebody else's claim is a 404 to cancel, not a 403")
        void cancelSomebodyElses() {
            NodeClaimView view = claim(member, sonInChiHai);

            assertThatThrownBy(() -> fixtures.claimService.cancel(chiHaiApprover, view.id()))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("the inbox")
    class Inbox {

        @Test
        @DisplayName("an approver sees claims in their scope")
        void inScope() {
            claim(member, sonInChiHai);

            assertThat(
                            fixtures
                                    .claimService
                                    .inbox(chiHaiApprover, clanId, ClaimStatus.PENDING, 0, 20)
                                    .content())
                    .singleElement()
                    .extracting(NodeClaimView::personId)
                    .isEqualTo(sonInChiHai.value());
        }

        @Test
        @DisplayName("and not claims on another chi, which they could not decide anyway")
        void outOfScope() {
            UserId other = fixtures.newMember(clanId, chiBa, "Người Chi Ba");
            claim(other, sonInChiBa);

            assertThat(
                            fixtures
                                    .claimService
                                    .inbox(chiHaiApprover, clanId, ClaimStatus.PENDING, 0, 20)
                                    .content())
                    .isEmpty();
        }

        @Test
        @DisplayName("each row carries the node's parents, which is what verifies a claim")
        void treeContext() {
            claim(member, sonInChiHai);

            assertThat(
                            fixtures
                                    .claimService
                                    .inbox(chiHaiApprover, clanId, ClaimStatus.PENDING, 0, 20)
                                    .content())
                    .singleElement()
                    .extracting(NodeClaimView::parentNames)
                    .isEqualTo(List.of("Nguyễn Văn Ất"));
        }

        @Test
        @DisplayName("a member who approves nothing gets a 403 rather than an empty page")
        void notAnApprover() {
            assertThatThrownBy(
                            () -> fixtures.claimService.inbox(member, clanId, null, 0, 20))
                    .isInstanceOf(PermissionDeniedException.class);
        }
    }

    @Nested
    @DisplayName("deciding")
    class Deciding {

        @Test
        @DisplayName("approval links the node and says so in the audit trail")
        void approve() {
            NodeClaimView view = claim(member, sonInChiHai);

            NodeClaimView decided =
                    fixtures.claimService.approve(chiHaiApprover, clanId, view.id());

            assertThat(decided.status()).isEqualTo(ClaimStatus.APPROVED.name());
            assertThat(fixtures.personLinkService.myNode(member, clanId))
                    .get()
                    .extracting(LinkedNodeView::personId)
                    .isEqualTo(sonInChiHai.value());
            assertThat(fixtures.lineage.clanOrg.audit.recorded(AuditAction.PERSON_LINKED)).isTrue();
            assertThat(fixtures.lineage.clanOrg.events.ofType(PersonLinked.class))
                    .singleElement()
                    .extracting(PersonLinked::viaClaim)
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("approval grants nothing — doc 04 §6, and the likeliest mistake of this phase")
        void approvalGrantsNothing() {
            NodeClaimView view = claim(member, sonInChiHai);
            fixtures.claimService.approve(chiHaiApprover, clanId, view.id());

            assertThat(fixtures.lineage.clanOrg.resolver.permissionsOf(member, clanId))
                    .as("being in the tree is not permission to edit it")
                    .containsExactly(Permission.CLAN_VIEW);
        }

        @Test
        @DisplayName("an approver outside the node's scope is refused")
        void outOfScope() {
            UserId claimant = fixtures.newMember(clanId, chiBa, "Người Chi Ba");
            NodeClaimView view = claim(claimant, sonInChiBa);

            assertThatThrownBy(
                            () -> fixtures.claimService.approve(chiHaiApprover, clanId, view.id()))
                    .isInstanceOf(PermissionDeniedException.class);
        }

        @Test
        @DisplayName("I-K4: nobody decides their own claim")
        void noSelfApproval() {
            grant(member, Permission.NODE_CLAIM_APPROVE, "BRANCH", chiHai);
            NodeClaimView view = claim(member, sonInChiHai);

            assertThatThrownBy(() -> fixtures.claimService.approve(member, clanId, view.id()))
                    .isInstanceOf(PermissionDeniedException.class)
                    .hasMessageContaining("your own claim");
        }

        @Test
        @DisplayName("I-K4: unless they administer the clan — the founder's bootstrap")
        void selfApprovalByAdmin() {
            NodeClaimView view = claim(founder, sonInChiHai);

            NodeClaimView decided = fixtures.claimService.approve(founder, clanId, view.id());

            assertThat(decided.status()).isEqualTo(ClaimStatus.APPROVED.name());
        }

        @Test
        @DisplayName("a node taken between submission and approval answers 409")
        void takenInTheMeantime() {
            NodeClaimView view = claim(member, sonInChiHai);
            UserId other = fixtures.newMember(clanId, chiHai, "Người khác");
            fixtures.personLinkService.assign(founder, clanId, sonInChiHai, other);

            assertThatThrownBy(
                            () -> fixtures.claimService.approve(chiHaiApprover, clanId, view.id()))
                    .isInstanceOf(ConflictException.class)
                    .extracting(failure -> ((ConflictException) failure).code())
                    .isEqualTo(ErrorCode.NODE_ALREADY_LINKED);
        }

        @Test
        @DisplayName("approving one claim refuses the others on the same node, with a reason")
        void closesRivalClaims() {
            UserId rival = fixtures.newMember(clanId, chiHai, "Người thứ hai");
            NodeClaimView mine = claim(member, sonInChiHai);
            NodeClaimView theirs = claim(rival, sonInChiHai);

            fixtures.claimService.approve(chiHaiApprover, clanId, mine.id());

            assertThat(fixtures.claimService.myClaims(rival))
                    .singleElement()
                    .satisfies(
                            view -> {
                                assertThat(view.id()).isEqualTo(theirs.id());
                                assertThat(view.status()).isEqualTo(ClaimStatus.REJECTED.name());
                                assertThat(view.decisionReason()).contains("Another claim");
                            });
        }

        @Test
        @DisplayName("a second approval of the same claim is 409, not a second link")
        void doubleClick() {
            NodeClaimView view = claim(member, sonInChiHai);
            fixtures.claimService.approve(chiHaiApprover, clanId, view.id());

            assertThatThrownBy(
                            () -> fixtures.claimService.approve(chiHaiApprover, clanId, view.id()))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("rejection carries its reason to the requester's own list")
        void reject() {
            NodeClaimView view = claim(member, sonInChiHai);

            fixtures.claimService.reject(chiHaiApprover, clanId, view.id(), "Đây là bác Hai");

            assertThat(fixtures.claimService.myClaims(member))
                    .singleElement()
                    .extracting(NodeClaimView::decisionReason)
                    .isEqualTo("Đây là bác Hai");
            assertThat(fixtures.lineage.clanOrg.events.ofType(ClaimDecided.class))
                    .extracting(ClaimDecided::outcome)
                    .contains(ClaimDecided.Outcome.REJECTED);
        }

        @Test
        @DisplayName("a rejected claim leaves the node in the unlinked pool")
        void rejectionLinksNothing() {
            NodeClaimView view = claim(member, sonInChiHai);

            fixtures.claimService.reject(chiHaiApprover, clanId, view.id(), "Không phải");

            assertThat(fixtures.personLinkService.myNode(member, clanId)).isEmpty();
        }
    }
}
