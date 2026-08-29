package app.giaky.linking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.authorization.application.GrantCommand;
import app.giaky.authorization.domain.Permission;
import app.giaky.common.audit.AuditAction;
import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.PermissionDeniedException;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.domain.Gender;
import app.giaky.linking.application.event.PersonUnlinked;
import app.giaky.linking.support.LinkingTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link PersonLinkService}: direct assignment, unlinking, and the candidate browser. */
class PersonLinkServiceTest {

    private LinkingTestFixtures fixtures;

    private UserId founder;
    private ClanId clanId;
    private BranchId rootBranch;
    private BranchId chiHai;

    private PersonId ancestor;
    private PersonId son;
    private PersonId daughter;

    private UserId member;
    private UserId approver;

    @BeforeEach
    void setUp() {
        fixtures = new LinkingTestFixtures();
        founder = fixtures.lineage.clanOrg.newUser("Nguyễn Văn Tổ");
        clanId = fixtures.lineage.clanOrg.newClan(founder, "Nguyễn Văn");
        rootBranch = fixtures.lineage.rootBranch(clanId);
        chiHai = fixtures.lineage.clanOrg.newBranch(founder, clanId, rootBranch, "Chi Hai");

        ancestor = fixtures.lineage.founder(founder, clanId, rootBranch, "Nguyễn Văn Ất");
        son = fixtures.lineage.childOf(founder, clanId, ancestor, "Nguyễn Văn An", Gender.MALE);
        daughter =
                fixtures.lineage.childOf(founder, clanId, ancestor, "Nguyễn Thị Bé", Gender.FEMALE);
        fixtures.lineage.personService.moveToBranch(founder, clanId, son, chiHai);

        member = fixtures.newMember(clanId, chiHai, "Nguyễn Văn An");
        approver = fixtures.newMember(clanId, chiHai, "Bác Hai");
        fixtures.lineage.clanOrg.grantService.grant(
                founder,
                clanId,
                new GrantCommand(
                        approver,
                        Permission.NODE_CLAIM_APPROVE.name(),
                        "BRANCH",
                        chiHai.value(),
                        null));
    }

    @Nested
    @DisplayName("direct assignment (FR-N4)")
    class Assigning {

        @Test
        @DisplayName("an approver in scope may link a member without a claim")
        void assign() {
            LinkedNodeView linked = fixtures.personLinkService.assign(approver, clanId, son, member);

            assertThat(linked.linkedUserId()).isEqualTo(member.value());
            assertThat(fixtures.lineage.clanOrg.audit.recorded(AuditAction.PERSON_LINKED)).isTrue();
        }

        @Test
        @DisplayName("assignment grants nothing either")
        void grantsNothing() {
            fixtures.personLinkService.assign(approver, clanId, son, member);

            assertThat(fixtures.lineage.clanOrg.resolver.permissionsOf(member, clanId))
                    .containsExactly(Permission.CLAN_VIEW);
        }

        @Test
        @DisplayName("a node outside the approver's scope is refused")
        void outOfScope() {
            assertThatThrownBy(
                            () -> fixtures.personLinkService.assign(approver, clanId, daughter, member))
                    .isInstanceOf(PermissionDeniedException.class);
        }

        @Test
        @DisplayName("assigning somebody who is not a member is 422, not 404")
        void nonMember() {
            UserId stranger = fixtures.lineage.clanOrg.newUser("Người lạ");

            assertThatThrownBy(
                            () -> fixtures.personLinkService.assign(approver, clanId, son, stranger))
                    .isInstanceOf(DomainRuleViolation.class)
                    .extracting(failure -> ((DomainRuleViolation) failure).code())
                    .isEqualTo(ErrorCode.VALIDATION_FAILED);
        }
    }

    @Nested
    @DisplayName("unlinking (FR-N5)")
    class Unlinking {

        @Test
        @DisplayName("the linked person may unlink themselves, holding nothing but membership")
        void selfUnlink() {
            fixtures.personLinkService.assign(approver, clanId, son, member);

            fixtures.personLinkService.unlink(member, clanId, son);

            assertThat(fixtures.personLinkService.myNode(member, clanId)).isEmpty();
            assertThat(fixtures.lineage.clanOrg.events.ofType(PersonUnlinked.class))
                    .singleElement()
                    .extracting(PersonUnlinked::selfService)
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("an approver in scope may unlink somebody else")
        void approverUnlink() {
            fixtures.personLinkService.assign(approver, clanId, son, member);

            fixtures.personLinkService.unlink(approver, clanId, son);

            assertThat(fixtures.personLinkService.linkOf(approver, clanId, son).linkedUserId())
                    .isNull();
        }

        @Test
        @DisplayName("a member who is neither cannot")
        void strangerCannotUnlink() {
            fixtures.personLinkService.assign(approver, clanId, son, member);
            UserId bystander = fixtures.newMember(clanId, chiHai, "Người ngoài");

            assertThatThrownBy(() -> fixtures.personLinkService.unlink(bystander, clanId, son))
                    .isInstanceOf(PermissionDeniedException.class);
        }

        @Test
        @DisplayName("the node returns to the unlinked pool")
        void backToThePool() {
            fixtures.personLinkService.assign(approver, clanId, son, member);
            fixtures.personLinkService.unlink(member, clanId, son);

            assertThat(
                            fixtures
                                    .personLinkService
                                    .unlinkedCandidates(member, clanId, null, null, null, 0, 20)
                                    .content())
                    .extracting(candidate -> candidate.person().id())
                    .contains(son.value());
        }

        @Test
        @DisplayName("unlinking forgets the propagation choices, so the next occupant starts at off")
        void clearsPropagation() {
            fixtures.personLinkService.assign(approver, clanId, son, member);
            fixtures.selfProfileService.update(
                    member, clanId, son, new SelfProfileCommand(null, null, true, true));

            fixtures.personLinkService.unlink(member, clanId, son);

            assertThat(fixtures.propagation.find(clanId, son).anyEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("the candidate browser (B6.6)")
    class Candidates {

        @Test
        @DisplayName("excludes nodes that already have an account")
        void excludesLinked() {
            fixtures.personLinkService.assign(approver, clanId, son, member);

            assertThat(
                            fixtures
                                    .personLinkService
                                    .unlinkedCandidates(member, clanId, null, null, null, 0, 20)
                                    .content())
                    .extracting(candidate -> candidate.person().id())
                    .doesNotContain(son.value());
        }

        @Test
        @DisplayName("carries the family around each candidate, which is what identifies them")
        void treeContext() {
            assertThat(
                            fixtures
                                    .personLinkService
                                    .unlinkedCandidates(member, clanId, "Nguyễn Văn An", null, null, 0, 20)
                                    .content())
                    .singleElement()
                    .satisfies(
                            candidate -> {
                                assertThat(candidate.person().fullName()).isEqualTo("Nguyễn Văn An");
                                assertThat(candidate.parentNames()).containsExactly("Nguyễn Văn Ất");
                            });
        }

        @Test
        @DisplayName("search is diacritic-insensitive, as the person search is")
        void unaccented() {
            assertThat(
                            fixtures
                                    .personLinkService
                                    .unlinkedCandidates(member, clanId, "nguyen van an", null, null, 0, 20)
                                    .content())
                    .isNotEmpty();
        }

        @Test
        @DisplayName("a branch filter narrows it to one chi")
        void byBranch() {
            assertThat(
                            fixtures
                                    .personLinkService
                                    .unlinkedCandidates(member, clanId, null, null, chiHai, 0, 20)
                                    .content())
                    .extracting(candidate -> candidate.person().id())
                    .containsExactly(son.value());
        }
    }
}
