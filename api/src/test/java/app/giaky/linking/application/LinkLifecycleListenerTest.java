package app.giaky.linking.application;

import static org.assertj.core.api.Assertions.assertThat;

import app.giaky.common.audit.AuditAction;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.domain.Gender;
import app.giaky.linking.application.event.ClaimDecided;
import app.giaky.linking.application.event.PersonUnlinked;
import app.giaky.linking.domain.ClaimStatus;
import app.giaky.linking.support.LinkingTestFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link LinkLifecycleListener}: a place in the tree does not outlive the membership (I-P5, I-K2).
 *
 * <p>Driven through {@code MembershipService} rather than by handing the listener an event, so what
 * is asserted is the thing an administrator actually does — remove somebody — and what the tree says
 * afterwards.
 */
class LinkLifecycleListenerTest {

    private LinkingTestFixtures fixtures;

    private UserId founder;
    private ClanId clanId;
    private BranchId rootBranch;

    private PersonId ancestor;
    private PersonId son;
    private PersonId daughter;

    private UserId member;

    @BeforeEach
    void setUp() {
        fixtures = new LinkingTestFixtures();
        founder = fixtures.lineage.clanOrg.newUser("Nguyễn Văn Tổ");
        clanId = fixtures.lineage.clanOrg.newClan(founder, "Nguyễn Văn");
        rootBranch = fixtures.lineage.rootBranch(clanId);

        ancestor = fixtures.lineage.founder(founder, clanId, rootBranch, "Nguyễn Văn Ất");
        son = fixtures.lineage.childOf(founder, clanId, ancestor, "Nguyễn Văn An", Gender.MALE);
        daughter =
                fixtures.lineage.childOf(founder, clanId, ancestor, "Nguyễn Thị Bé", Gender.FEMALE);

        member = fixtures.newMember(clanId, rootBranch, "Nguyễn Văn An");
    }

    private UUID claim(UserId caller, PersonId personId) {
        return fixtures
                .claimService
                .submit(caller, clanId, new SubmitClaimCommand(personId, "Con của ông Ất"))
                .id();
    }

    private UserId linkedUserOf(PersonId personId) {
        UUID linked = fixtures.personLinkService.linkOf(founder, clanId, personId).linkedUserId();
        return linked == null ? null : UserId.of(linked);
    }

    @Nested
    @DisplayName("the node they held")
    class TheNode {

        @Test
        @DisplayName("a removed member's node goes back to the unlinked pool")
        void removedMemberIsUnlinked() {
            fixtures.personLinkService.assign(founder, clanId, son, member);
            fixtures.lineage.clanOrg.audit.clear();

            fixtures.lineage.clanOrg.membershipService.remove(founder, clanId, member);

            assertThat(linkedUserOf(son)).isNull();
            assertThat(
                            fixtures
                                    .personLinkService
                                    .unlinkedCandidates(founder, clanId, null, null, null, 0, 20)
                                    .content())
                    .extracting(candidate -> candidate.person().id())
                    .contains(son.value());
            assertThat(fixtures.lineage.clanOrg.audit.recorded(AuditAction.PERSON_UNLINKED))
                    .isTrue();
        }

        @Test
        @DisplayName("they are told, because somebody else did it")
        void removalIsNotSelfService() {
            fixtures.personLinkService.assign(founder, clanId, son, member);

            fixtures.lineage.clanOrg.membershipService.remove(founder, clanId, member);

            assertThat(fixtures.lineage.clanOrg.events.ofType(PersonUnlinked.class))
                    .singleElement()
                    .satisfies(
                            event -> {
                                assertThat(event.personId()).isEqualTo(son);
                                assertThat(event.previousUserId()).isEqualTo(member);
                                assertThat(event.actorUserId()).isEqualTo(founder);
                                assertThat(event.selfService()).isFalse();
                            });
        }

        @Test
        @DisplayName("a member who leaves gives the node up too, and is not notified of their own act")
        void leavingUnlinks() {
            fixtures.personLinkService.assign(founder, clanId, son, member);

            fixtures.lineage.clanOrg.membershipService.leave(member, clanId);

            assertThat(linkedUserOf(son)).isNull();
            assertThat(fixtures.lineage.clanOrg.events.ofType(PersonUnlinked.class))
                    .singleElement()
                    .extracting(PersonUnlinked::selfService)
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("the propagation choices go with it, so the next occupant starts at off")
        void clearsPropagation() {
            fixtures.personLinkService.assign(founder, clanId, son, member);
            fixtures.selfProfileService.update(
                    member, clanId, son, new SelfProfileCommand(null, null, true, true));

            fixtures.lineage.clanOrg.membershipService.remove(founder, clanId, member);

            assertThat(fixtures.propagation.find(clanId, son).anyEnabled()).isFalse();
        }

        @Test
        @DisplayName("somebody re-admitted later comes back unlinked, and the node can be claimed")
        void readmittedStartsOver() {
            fixtures.personLinkService.assign(founder, clanId, son, member);
            fixtures.lineage.clanOrg.membershipService.remove(founder, clanId, member);
            UserId cousin = fixtures.newMember(clanId, rootBranch, "Nguyễn Văn Em");

            fixtures.personLinkService.assign(founder, clanId, son, cousin);

            assertThat(linkedUserOf(son)).isEqualTo(cousin);
        }

        @Test
        @DisplayName("nobody else's link is touched")
        void othersKeepTheirNodes() {
            UserId sister = fixtures.newMember(clanId, rootBranch, "Nguyễn Thị Bé");
            fixtures.personLinkService.assign(founder, clanId, son, member);
            fixtures.personLinkService.assign(founder, clanId, daughter, sister);

            fixtures.lineage.clanOrg.membershipService.remove(founder, clanId, member);

            assertThat(linkedUserOf(daughter)).isEqualTo(sister);
        }

        @Test
        @DisplayName("a member who held no node leaves no trace in the audit trail")
        void nothingToRelease() {
            fixtures.lineage.clanOrg.audit.clear();

            fixtures.lineage.clanOrg.membershipService.remove(founder, clanId, member);

            assertThat(fixtures.lineage.clanOrg.audit.recorded(AuditAction.PERSON_UNLINKED))
                    .isFalse();
            assertThat(fixtures.lineage.clanOrg.events.ofType(PersonUnlinked.class)).isEmpty();
        }
    }

    @Nested
    @DisplayName("the claims they still had open")
    class TheirClaims {

        @Test
        @DisplayName("are withdrawn, so no approver is left holding a question with one answer")
        void pendingClaimsAreWithdrawn() {
            UUID onSon = claim(member, son);
            UUID onDaughter = claim(member, daughter);

            fixtures.lineage.clanOrg.membershipService.remove(founder, clanId, member);

            assertThat(fixtures.claims.findById(clanId, onSon))
                    .get()
                    .extracting(claim -> claim.status())
                    .isEqualTo(ClaimStatus.CANCELLED);
            assertThat(fixtures.claims.findById(clanId, onDaughter))
                    .get()
                    .extracting(claim -> claim.status())
                    .isEqualTo(ClaimStatus.CANCELLED);
            assertThat(fixtures.claims.countByClanAndStatus(clanId, ClaimStatus.PENDING)).isZero();
            assertThat(fixtures.lineage.clanOrg.events.ofType(ClaimDecided.class))
                    .hasSize(2)
                    .allSatisfy(
                            event ->
                                    assertThat(event.outcome())
                                            .isEqualTo(ClaimDecided.Outcome.CANCELLED));
        }

        @Test
        @DisplayName("a claim that was already answered keeps its answer")
        void decidedClaimsAreHistory() {
            UUID rejected = claim(member, son);
            fixtures.claimService.reject(founder, clanId, rejected, "Chưa đủ căn cứ");

            fixtures.lineage.clanOrg.membershipService.remove(founder, clanId, member);

            assertThat(fixtures.claims.findById(clanId, rejected))
                    .get()
                    .extracting(claim -> claim.status())
                    .isEqualTo(ClaimStatus.REJECTED);
        }

        @Test
        @DisplayName("another member's claim on the same node stays open")
        void othersKeepTheirClaims() {
            UserId cousin = fixtures.newMember(clanId, rootBranch, "Nguyễn Văn Em");
            claim(member, son);
            UUID cousins = claim(cousin, son);

            fixtures.lineage.clanOrg.membershipService.remove(founder, clanId, member);

            assertThat(fixtures.claims.findById(clanId, cousins))
                    .get()
                    .extracting(claim -> claim.status())
                    .isEqualTo(ClaimStatus.PENDING);
        }

        @Test
        @DisplayName("their claims in another family are none of this clan's business")
        void otherClansAreUntouched() {
            UserId elder = fixtures.lineage.clanOrg.newUser("Trần Văn Trưởng");
            ClanId other = fixtures.lineage.clanOrg.newClan(elder, "Trần Văn");
            BranchId otherRoot = fixtures.lineage.rootBranch(other);
            PersonId theirs = fixtures.lineage.founder(elder, other, otherRoot, "Trần Văn Giáp");
            // The same account, a member of both families.
            fixtures.lineage.clanOrg.memberships.save(
                    app.giaky.clanorg.domain.ClanMembership.grant(
                            fixtures.lineage.clanOrg.ids.newId(),
                            other,
                            member,
                            otherRoot,
                            null,
                            fixtures.lineage.clanOrg.clock.instant()));
            UUID elsewhere =
                    fixtures
                            .claimService
                            .submit(member, other, new SubmitClaimCommand(theirs, "Cháu nội"))
                            .id();

            fixtures.lineage.clanOrg.membershipService.remove(founder, clanId, member);

            assertThat(fixtures.claims.findById(other, elsewhere))
                    .get()
                    .extracting(claim -> claim.status())
                    .isEqualTo(ClaimStatus.PENDING);
        }
    }
}
