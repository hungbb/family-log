package app.giaky.clanorg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.clanorg.application.event.ClanCreated;
import app.giaky.clanorg.domain.Branch;
import app.giaky.clanorg.domain.Clan;
import app.giaky.clanorg.support.ClanOrgTestFixtures;
import app.giaky.common.audit.AuditAction;
import app.giaky.common.error.ConflictException;
import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.error.PermissionDeniedException;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link ClanService}: founding a clan, and who may then do what with it. */
class ClanServiceTest {

    private ClanOrgTestFixtures fixtures;
    private UserId founder;
    private UserId outsider;

    @BeforeEach
    void setUp() {
        fixtures = new ClanOrgTestFixtures();
        founder = fixtures.newUser("Nguyễn Văn An");
        outsider = fixtures.newUser("Trần Thị Bình");
    }

    @Nested
    @DisplayName("founding")
    class Founding {

        @Test
        @DisplayName("creates the clan, its root branch and the founder's membership together")
        void createsAllThree() {
            ClanView clan =
                    fixtures.clanService.create(
                            founder, new CreateClanCommand("Nguyễn Văn", "Nguyễn", "Nam Định", null));

            assertThat(clan.name()).isEqualTo("Nguyễn Văn");
            assertThat(clan.clanCode()).startsWith("NGUYEN-");
            assertThat(clan.rootBranchId()).isNotNull();
            assertThat(clan.memberCount()).isEqualTo(1);

            ClanId clanId = ClanId.of(clan.id());
            List<Branch> branches = fixtures.branches.findAllByClan(clanId);
            assertThat(branches).hasSize(1);
            assertThat(branches.get(0).isRoot()).isTrue();
            assertThat(branches.get(0).id().value()).isEqualTo(clan.rootBranchId());

            // I-C2 from the other side: the clan points back at the branch that points at it.
            assertThat(fixtures.memberships.findActive(clanId, founder)).isPresent();
            assertThat(fixtures.memberships.findActive(clanId, founder).orElseThrow().homeBranchId())
                    .isEqualTo(branches.get(0).id());
        }

        @Test
        @DisplayName("the code is built from the surname when one is given, and the name otherwise")
        void codePrefix() {
            ClanView withSurname =
                    fixtures.clanService.create(
                            founder, new CreateClanCommand("Họ Lê Đình", "Lê", null, null));
            ClanView withoutSurname =
                    fixtures.clanService.create(
                            outsider, new CreateClanCommand("Phạm Gia", null, null, null));

            assertThat(withSurname.clanCode()).startsWith("LE-");
            assertThat(withoutSurname.clanCode()).startsWith("PHAMGIA-");
        }

        @Test
        @DisplayName("the founder is told they may administer it")
        void founderPermissions() {
            ClanView clan =
                    fixtures.clanService.create(
                            founder, new CreateClanCommand("Nguyễn Văn", null, null, null));

            assertThat(clan.myPermissions()).contains("CLAN_ADMIN", "CLAN_VIEW");
        }

        @Test
        @DisplayName("creation is audited and announced")
        void createIsAuditedAndPublished() {
            ClanView clan =
                    fixtures.clanService.create(
                            founder, new CreateClanCommand("Nguyễn Văn", null, null, null));

            assertThat(fixtures.audit.recorded(AuditAction.CLAN_CREATED)).isTrue();
            // Phase 3 consumes this to bootstrap the founder's CLAN_ADMIN grant (doc 04 §6).
            List<ClanCreated> published = fixtures.events.ofType(ClanCreated.class);
            assertThat(published).hasSize(1);
            assertThat(published.get(0).clanId().value()).isEqualTo(clan.id());
            assertThat(published.get(0).createdBy()).isEqualTo(founder);
        }
    }

    @Nested
    @DisplayName("reading")
    class Reading {

        @Test
        @DisplayName("a non-member gets 404, not 403 — a private clan does not confirm it exists")
        void nonMemberGets404() {
            ClanId clanId = fixtures.newClan(founder, "Nguyễn Văn");

            assertThatThrownBy(() -> fixtures.clanService.detail(outsider, clanId))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("an id that belongs to no clan is the same 404")
        void unknownClanGets404() {
            // Indistinguishable from the case above, which is the point (doc 04 §7).
            assertThatThrownBy(
                            () ->
                                    fixtures.clanService.detail(
                                            founder, ClanId.of(UUID.randomUUID())))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("the switcher lists only clans the caller is actively in")
        void listForUser() {
            ClanId mine = fixtures.newClan(founder, "Nguyễn Văn");
            fixtures.newClan(outsider, "Trần Gia");

            List<ClanSummaryView> listed = fixtures.clanService.listForUser(founder);

            assertThat(listed).hasSize(1);
            assertThat(listed.get(0).id()).isEqualTo(mine.value());
        }

        @Test
        @DisplayName("a soft-deleted clan drops out of the switcher")
        void deletedClansAreNotListed() {
            ClanId clanId = fixtures.newClan(founder, "Nguyễn Văn");
            fixtures.clanService.softDelete(founder, clanId);

            assertThat(fixtures.clanService.listForUser(founder)).isEmpty();
        }
    }

    @Nested
    @DisplayName("editing")
    class Editing {

        @Test
        @DisplayName("only the creator may edit, and a member who is not gets 403")
        void onlyCreatorMayEdit() {
            ClanId clanId = fixtures.newClan(founder, "Nguyễn Văn");
            joinAsMember(clanId, outsider);

            assertThatThrownBy(
                            () ->
                                    fixtures.clanService.update(
                                            outsider,
                                            clanId,
                                            new UpdateClanCommand("Khác", null, null, null, 0L)))
                    // A member already knows the clan exists, so they get a precise answer.
                    .isInstanceOf(PermissionDeniedException.class);
        }

        @Test
        @DisplayName("an absent field is left alone")
        void partialUpdate() {
            ClanId clanId = fixtures.newClan(founder, "Nguyễn Văn");
            ClanView before = fixtures.clanService.detail(founder, clanId);

            ClanView after =
                    fixtures.clanService.update(
                            founder,
                            clanId,
                            new UpdateClanCommand(null, "Gia phả họ Nguyễn", null, null, before.version()));

            assertThat(after.name()).isEqualTo("Nguyễn Văn");
            assertThat(after.description()).isEqualTo("Gia phả họ Nguyễn");
            assertThat(after.originPlace()).isEqualTo(before.originPlace());
        }

        @Test
        @DisplayName("a stale version is refused rather than allowed to overwrite")
        void staleVersionIsRefused() {
            ClanId clanId = fixtures.newClan(founder, "Nguyễn Văn");

            assertThatThrownBy(
                            () ->
                                    fixtures.clanService.update(
                                            founder,
                                            clanId,
                                            new UpdateClanCommand("Khác", null, null, null, 41L)))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("the clan code cannot be changed — the command cannot even say it")
        void codeIsImmutable() {
            ClanId clanId = fixtures.newClan(founder, "Nguyễn Văn");
            ClanView before = fixtures.clanService.detail(founder, clanId);

            ClanView after =
                    fixtures.clanService.update(
                            founder,
                            clanId,
                            new UpdateClanCommand("Đổi tên", null, null, null, before.version()));

            assertThat(after.clanCode()).isEqualTo(before.clanCode());
        }

        @Test
        @DisplayName("deleting is soft: the row stays and the code stays reserved")
        void deleteIsSoft() {
            ClanId clanId = fixtures.newClan(founder, "Nguyễn Văn");
            String code = fixtures.clanService.detail(founder, clanId).clanCode();

            fixtures.clanService.softDelete(founder, clanId);

            assertThat(fixtures.clans.findById(clanId)).isEmpty();
            assertThat(fixtures.clans.findIncludingDeleted(clanId)).isPresent();
            // I-C1: never reissued, even to a clan that no longer exists.
            assertThat(fixtures.clans.isClanCodeTaken(code)).isTrue();
        }
    }

    @Nested
    @DisplayName("lookup by code")
    class Lookup {

        @Test
        @DisplayName("a clan code returns the minimal card")
        void byClanCode() {
            ClanId clanId = fixtures.newClan(founder, "Nguyễn Văn");
            String code = fixtures.clanService.detail(founder, clanId).clanCode();

            ClanCardView card = fixtures.clanService.lookupByCode(outsider, code);

            assertThat(card.name()).isEqualTo("Nguyễn Văn");
            assertThat(card.memberCount()).isEqualTo(1);
            assertThat(card.alreadyMember()).isFalse();
            assertThat(card.targetBranchId()).isNull();
        }

        @Test
        @DisplayName("a branch code names the branch the request would go to")
        void byBranchCode() {
            ClanId clanId = fixtures.newClan(founder, "Nguyễn Văn");
            BranchId chiHai = fixtures.newBranch(founder, clanId, null, "Chi Hai");
            String branchCode =
                    fixtures.branches.findById(clanId, chiHai).orElseThrow().branchCode().value();

            ClanCardView card = fixtures.clanService.lookupByCode(outsider, branchCode);

            assertThat(card.name()).isEqualTo("Nguyễn Văn");
            assertThat(card.targetBranchId()).isEqualTo(chiHai.value());
            assertThat(card.targetBranchName()).isEqualTo("Chi Hai");
        }

        @Test
        @DisplayName("the card says when the caller is already in")
        void memberSeesTheyAreIn() {
            ClanId clanId = fixtures.newClan(founder, "Nguyễn Văn");
            String code = fixtures.clanService.detail(founder, clanId).clanCode();

            assertThat(fixtures.clanService.lookupByCode(founder, code).alreadyMember()).isTrue();
        }

        @Test
        @DisplayName("a malformed code is a 400, an unknown one a 404")
        void malformedVersusUnknown() {
            // The distinction is safe: the first is about the string the caller typed, the second
            // discloses only that some code in a very large space is unused.
            assertThatThrownBy(() -> fixtures.clanService.lookupByCode(outsider, "not a code"))
                    .isInstanceOf(DomainRuleViolation.class);
            assertThatThrownBy(() -> fixtures.clanService.lookupByCode(outsider, "NGUYEN-ZZZZ"))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    /** Puts {@code user} into {@code clanId} the way the product does: by asking and being approved. */
    private void joinAsMember(ClanId clanId, UserId user) {
        Clan clan = fixtures.clans.findById(clanId).orElseThrow();
        JoinRequestView request =
                fixtures.joinRequestService.submit(
                        user, new SubmitJoinRequestCommand(clan.clanCode().value(), null, null));
        fixtures.joinRequestService.approve(founder, clanId, request.id(), null);
    }
}
