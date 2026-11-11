package app.giaky.authorization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.authorization.domain.Permission;
import app.giaky.authorization.domain.PermissionGrant;
import app.giaky.authorization.domain.PermissionScope;
import app.giaky.authorization.domain.ScopeType;
import app.giaky.authorization.domain.Target;
import app.giaky.clanorg.application.JoinRequestView;
import app.giaky.clanorg.application.SubmitJoinRequestCommand;
import app.giaky.clanorg.support.ClanOrgTestFixtures;
import app.giaky.common.audit.AuditAction;
import app.giaky.common.error.ApiException;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Grant management: the bootstrap, the four escalation conditions, and last-admin protection.
 *
 * <p>The fixture is the isolation-matrix clan of doc 04 §8 — a founder, an đời-13 root branch, and
 * two chi that must not be able to reach each other. Everything below is asked of the real services
 * over in-memory repositories, so what is under test is the code that runs in production, including
 * the listeners that turn clan creation and join approval into grants.
 */
class GrantServiceTest {

    private ClanOrgTestFixtures fixtures;
    private UserId admin;
    private UserId editorC;
    private UserId memberD;
    private UserId newcomer;
    private ClanId clanId;
    private String clanCode;
    private BranchId rootBranch;
    private BranchId chiHai;
    private BranchId chiBa;

    @BeforeEach
    void setUp() {
        fixtures = new ClanOrgTestFixtures();
        admin = fixtures.newUser("Nguyễn Văn An");
        editorC = fixtures.newUser("Nguyễn Văn Cường");
        memberD = fixtures.newUser("Nguyễn Văn Dũng");
        newcomer = fixtures.newUser("Nguyễn Thị Em");

        clanId = fixtures.newClan(admin, "Nguyễn Văn");
        clanCode = fixtures.clanService.detail(admin, clanId).clanCode();
        rootBranch = BranchId.of(fixtures.clanService.detail(admin, clanId).rootBranchId());
        chiHai = fixtures.newBranch(admin, clanId, null, "Chi Hai");
        chiBa = fixtures.newBranch(admin, clanId, null, "Chi Ba");

        join(editorC, chiHai);
        join(memberD, chiBa);
        join(newcomer, chiHai);
    }

    @Nested
    @DisplayName("bootstrap and lifecycle (doc 04 §6)")
    class Lifecycle {

        @Test
        @DisplayName("founding a clan makes the founder its administrator")
        void founderIsBootstrapped() {
            assertThat(fixtures.permissions.permissionNames(admin, clanId))
                    .containsExactlyInAnyOrder(
                            "CLAN_VIEW",
                            "TREE_EDIT",
                            "MEMBER_APPROVE",
                            "NODE_CLAIM_APPROVE",
                            "ROLE_MANAGE",
                            "CLAN_ADMIN");

            // Flagged as a bootstrap rather than as an ordinary grant: nobody had the power to
            // issue this one, which is a different fact from somebody having exercised it.
            assertThat(fixtures.audit.actions()).contains(AuditAction.PERMISSION_BOOTSTRAPPED);
        }

        @Test
        @DisplayName("an approved member gets CLAN_VIEW at the scope they were admitted into")
        void joinApprovalGrantsView() {
            List<GrantView> mine = fixtures.permissions.myPermissions(editorC, clanId).grants();

            assertThat(mine).hasSize(1);
            assertThat(mine.get(0).permission()).isEqualTo("CLAN_VIEW");
            assertThat(mine.get(0).scopeType()).isEqualTo("BRANCH");
            assertThat(mine.get(0).scopeRefId()).isEqualTo(chiHai.value());
            assertThat(mine.get(0).scopeLabel()).isEqualTo("Chi Hai");
        }

        @Test
        @DisplayName("reads stay clan-wide even for a branch-scoped member (doc 04 §5.5)")
        void branchScopedMemberStillReadsTheClan() {
            assertThat(fixtures.clanService.detail(editorC, clanId).name()).isEqualTo("Nguyễn Văn");
            assertThat(fixtures.branchService.tree(editorC, clanId)).isNotEmpty();
        }

        @Test
        @DisplayName("removing a member deactivates their grants in that clan")
        void removalRevokesGrants() {
            grant(admin, editorC, Permission.TREE_EDIT, ScopeType.BRANCH, chiHai.value());
            assertThat(fixtures.permissions.permissionNames(editorC, clanId)).contains("TREE_EDIT");

            fixtures.membershipService.remove(admin, clanId, editorC);

            assertThat(fixtures.permissions.permissionNames(editorC, clanId)).isEmpty();
            assertThat(fixtures.grants.all())
                    .filteredOn(grant -> grant.granteeUserId().equals(editorC))
                    .allMatch(PermissionGrant::isRevoked);
        }

        @Test
        @DisplayName("a lapsed grant stops working before the sweep runs, and is tidied by it")
        void expirySweep() {
            fixtures.grantService.grant(
                    admin,
                    clanId,
                    new GrantCommand(
                            editorC,
                            "TREE_EDIT",
                            "BRANCH",
                            chiHai.value(),
                            ClanOrgTestFixtures.NOW.plus(Duration.ofDays(7))));

            assertThat(fixtures.permissions.permissionNames(editorC, clanId)).contains("TREE_EDIT");

            fixtures.advance(Duration.ofDays(8));

            // Access stops at the expiry, not at the sweep — the sweep has not run yet.
            assertThat(fixtures.permissions.permissionNames(editorC, clanId))
                    .doesNotContain("TREE_EDIT");

            assertThat(fixtures.grantService.markExpiredRevoked()).isEqualTo(1);
            assertThat(fixtures.grantService.markExpiredRevoked())
                    .as("nothing left to sweep the second time")
                    .isZero();
            assertThat(fixtures.audit.actions()).contains(AuditAction.PERMISSION_EXPIRED);
        }
    }

    @Nested
    @DisplayName("escalation is blocked (doc 04 §5.4, FR-Z5)")
    class Escalation {

        @BeforeEach
        void makeEditorCABranchRoleManager() {
            // The demo of phase 3 §6: C edits Chi Hai and manages roles there. Nothing more.
            grant(admin, editorC, Permission.TREE_EDIT, ScopeType.BRANCH, chiHai.value());
            grant(admin, editorC, Permission.ROLE_MANAGE, ScopeType.BRANCH, chiHai.value());
        }

        @Test
        @DisplayName("granting TREE_EDIT on their own branch is allowed")
        void grantWithinScopeIsAllowed() {
            GrantView issued =
                    grant(editorC, newcomer, Permission.TREE_EDIT, ScopeType.BRANCH, chiHai.value());

            assertThat(issued.permission()).isEqualTo("TREE_EDIT");
            assertThat(issued.scopeRefId()).isEqualTo(chiHai.value());
            assertThat(issued.grantedByUserId()).isEqualTo(editorC.value());
            assertThat(fixtures.permissions.permissionNames(newcomer, clanId)).contains("TREE_EDIT");
        }

        @Test
        @DisplayName("granting CLAN_ADMIN is refused")
        void cannotGrantAdmin() {
            assertThatThrownBy(
                            () ->
                                    grant(
                                            editorC,
                                            newcomer,
                                            Permission.CLAN_ADMIN,
                                            ScopeType.CLAN,
                                            null))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.PERMISSION_ESCALATION_BLOCKED);
        }

        @Test
        @DisplayName("granting TREE_EDIT clan-wide is refused — the scope is above their own")
        void cannotWidenToClanScope() {
            assertThatThrownBy(
                            () -> grant(editorC, newcomer, Permission.TREE_EDIT, ScopeType.CLAN, null))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.PERMISSION_ESCALATION_BLOCKED);
        }

        @Test
        @DisplayName("granting TREE_EDIT on the chi next door is refused")
        void cannotReachAnotherBranch() {
            assertThatThrownBy(
                            () ->
                                    grant(
                                            editorC,
                                            memberD,
                                            Permission.TREE_EDIT,
                                            ScopeType.BRANCH,
                                            chiBa.value()))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.PERMISSION_ESCALATION_BLOCKED);
        }

        @Test
        @DisplayName("granting the root branch is refused — upwards is still escalation")
        void cannotReachUpwards() {
            assertThatThrownBy(
                            () ->
                                    grant(
                                            editorC,
                                            newcomer,
                                            Permission.TREE_EDIT,
                                            ScopeType.BRANCH,
                                            rootBranch.value()))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.PERMISSION_ESCALATION_BLOCKED);
        }

        @Test
        @DisplayName("granting a permission they do not hold themselves is refused")
        void cannotGrantWhatTheyDoNotHold() {
            // Condition 2 is the one that closes the back door: ROLE_MANAGE alone would otherwise
            // let a branch manager mint capabilities nobody ever gave them.
            assertThatThrownBy(
                            () ->
                                    grant(
                                            editorC,
                                            newcomer,
                                            Permission.MEMBER_APPROVE,
                                            ScopeType.BRANCH,
                                            chiHai.value()))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.PERMISSION_ESCALATION_BLOCKED);
        }

        @Test
        @DisplayName("once they hold it, they may pass it on within their scope")
        void mayPassOnWhatTheyDoHold() {
            grant(admin, editorC, Permission.MEMBER_APPROVE, ScopeType.BRANCH, chiHai.value());

            GrantView issued =
                    grant(
                            editorC,
                            newcomer,
                            Permission.MEMBER_APPROVE,
                            ScopeType.BRANCH,
                            chiHai.value());

            assertThat(issued.permission()).isEqualTo("MEMBER_APPROVE");
        }

        @Test
        @DisplayName("somebody who manages no roles cannot grant at all")
        void nonManagerCannotGrant() {
            assertThatThrownBy(
                            () ->
                                    grant(
                                            memberD,
                                            newcomer,
                                            Permission.CLAN_VIEW,
                                            ScopeType.BRANCH,
                                            chiBa.value()))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.PERMISSION_ESCALATION_BLOCKED);
        }

        @Test
        @DisplayName("a non-member is told the clan does not exist, not that they were refused")
        void nonMemberGetsNotFound() {
            UserId stranger = fixtures.newUser("Người lạ");

            assertThatThrownBy(
                            () ->
                                    grant(
                                            stranger,
                                            newcomer,
                                            Permission.CLAN_VIEW,
                                            ScopeType.CLAN,
                                            null))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.CLAN_NOT_FOUND);
        }

        @Test
        @DisplayName("the grantable list offers nothing the caller would be refused for")
        void grantableMatchesWhatIsActuallyAllowed() {
            GrantableView offered = fixtures.grantService.grantable(editorC, clanId);

            // Subtree scopes are not enumerated among the permissions below — a scope names a
            // person and a clan has thousands — so this flag is how the dialog knows whether to
            // offer a person search instead (F3.6, F4.2). A branch role manager is not offered it:
            // grant() refuses them a subtree scope even inside their own chi, and the title of
            // this test is a promise about exactly that.
            assertThat(offered.subtreeAvailable()).isFalse();
            assertThat(fixtures.grantService.grantable(admin, clanId).subtreeAvailable())
                    .as("somebody who manages roles clan-wide can, and is offered it")
                    .isTrue();
            assertThat(offered.permissions())
                    .extracting(GrantableView.GrantablePermission::permission)
                    .containsExactlyInAnyOrder("CLAN_VIEW", "TREE_EDIT", "ROLE_MANAGE");
            assertThat(offered.permissions())
                    .allSatisfy(
                            permission ->
                                    assertThat(permission.scopes())
                                            .extracting(GrantableView.GrantableScope::scopeRefId)
                                            .containsExactly(chiHai.value()));

            // And the promise the dialog rests on: every option it offers actually works.
            for (GrantableView.GrantablePermission permission : offered.permissions()) {
                for (GrantableView.GrantableScope scope : permission.scopes()) {
                    // Admitted clan-wide, so their automatic CLAN_VIEW does not collide with the
                    // branch-scoped one the dialog is offering (I-G5).
                    UserId fresh = fixtures.newUser("Người mới");
                    joinInto(fresh, null);
                    fixtures.grantService.grant(
                            editorC,
                            clanId,
                            new GrantCommand(
                                    fresh,
                                    permission.permission(),
                                    scope.scopeType(),
                                    scope.scopeRefId(),
                                    null));
                }
            }
        }

        @Test
        @DisplayName("an administrator may grant anything, anywhere in the clan")
        void administratorMayGrantEverything() {
            GrantableView offered = fixtures.grantService.grantable(admin, clanId);

            assertThat(offered.permissions())
                    .extracting(GrantableView.GrantablePermission::permission)
                    .containsExactlyInAnyOrder(
                            "CLAN_VIEW",
                            "TREE_EDIT",
                            "MEMBER_APPROVE",
                            "NODE_CLAIM_APPROVE",
                            "ROLE_MANAGE",
                            "CLAN_ADMIN");
            // CLAN_ADMIN is clan-scoped only (I-G2); the others are offered per branch as well.
            assertThat(scopesFor(offered, "CLAN_ADMIN")).containsExactly("CLAN");
            assertThat(scopesFor(offered, "TREE_EDIT")).contains("CLAN", "BRANCH");
        }
    }

    @Nested
    @DisplayName("refusals that are not escalation")
    class OtherRefusals {

        @Test
        @DisplayName("subtree scope naming nobody is a 404, not a grant on nothing")
        void subtreeScopeIsResolvedBeforeItIsStored() {
            // Phase 4 turned this scope on (B4.8). What replaced the old 422 is the rule branch
            // scope has always had: resolve the reference first, because a grant pointing at
            // nobody covers nobody and is a row an administrator cannot explain.
            assertThatThrownBy(
                            () ->
                                    fixtures.grantService.grant(
                                            admin,
                                            clanId,
                                            new GrantCommand(
                                                    editorC,
                                                    "TREE_EDIT",
                                                    "SUBTREE",
                                                    UUID.randomUUID(),
                                                    null)))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.PERSON_NOT_FOUND);
        }

        @Test
        @DisplayName("subtree scope on a person who exists is granted (B4.8)")
        void subtreeScopeIsLive() {
            UUID ancestor = UUID.randomUUID();
            fixtures.ancestry.withPerson(ancestor, chiHai, "Nguyễn Văn An");

            GrantView issued =
                    fixtures.grantService.grant(
                            admin,
                            clanId,
                            new GrantCommand(editorC, "TREE_EDIT", "SUBTREE", ancestor, null));

            assertThat(issued.scopeType()).isEqualTo("SUBTREE");
            assertThat(issued.scopeRefId()).isEqualTo(ancestor);
            assertThat(issued.scopeLabel())
                    .as("the permissions screen names the person, not a UUID")
                    .isEqualTo("Nguyễn Văn An");
        }

        @Test
        @DisplayName("CLAN_ADMIN cannot be scoped to a branch (I-G2)")
        void adminIsClanWideOnly() {
            assertThatThrownBy(
                            () ->
                                    grant(
                                            admin,
                                            editorC,
                                            Permission.CLAN_ADMIN,
                                            ScopeType.BRANCH,
                                            chiHai.value()))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.VALIDATION_FAILED);
        }

        @Test
        @DisplayName("a branch scope must name a branch, and a clan scope must not")
        void scopeReferenceIsValidated() {
            assertThatThrownBy(
                            () ->
                                    fixtures.grantService.grant(
                                            admin,
                                            clanId,
                                            new GrantCommand(
                                                    editorC, "TREE_EDIT", "BRANCH", null, null)))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.VALIDATION_FAILED);

            assertThatThrownBy(
                            () ->
                                    fixtures.grantService.grant(
                                            admin,
                                            clanId,
                                            new GrantCommand(
                                                    editorC,
                                                    "TREE_EDIT",
                                                    "CLAN",
                                                    chiHai.value(),
                                                    null)))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.VALIDATION_FAILED);
        }

        @Test
        @DisplayName("a branch from another clan is a 404, not a grant that covers nothing")
        void branchMustBelongToTheClan() {
            UserId otherFounder = fixtures.newUser("Trần Văn Bảy");
            ClanId otherClan = fixtures.newClan(otherFounder, "Trần Văn");
            BranchId theirBranch = fixtures.newBranch(otherFounder, otherClan, null, "Chi Một");

            assertThatThrownBy(
                            () ->
                                    grant(
                                            admin,
                                            editorC,
                                            Permission.TREE_EDIT,
                                            ScopeType.BRANCH,
                                            theirBranch.value()))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.BRANCH_NOT_FOUND);
        }

        @Test
        @DisplayName("an unknown permission name is a 422 naming the value")
        void unknownPermission() {
            assertThatThrownBy(
                            () ->
                                    fixtures.grantService.grant(
                                            admin,
                                            clanId,
                                            new GrantCommand(
                                                    editorC, "PATRIARCH", "CLAN", null, null)))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("PATRIARCH");
        }

        @Test
        @DisplayName("granting to somebody outside the clan is refused")
        void granteeMustBeAMember() {
            UserId stranger = fixtures.newUser("Người ngoài");

            assertThatThrownBy(
                            () -> grant(admin, stranger, Permission.TREE_EDIT, ScopeType.CLAN, null))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.VALIDATION_FAILED);
        }

        @Test
        @DisplayName("granting the same thing twice is a 409, not a duplicate row (I-G5)")
        void duplicateGrant() {
            grant(admin, editorC, Permission.TREE_EDIT, ScopeType.BRANCH, chiHai.value());

            assertThatThrownBy(
                            () ->
                                    grant(
                                            admin,
                                            editorC,
                                            Permission.TREE_EDIT,
                                            ScopeType.BRANCH,
                                            chiHai.value()))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.GRANT_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("the permissions screen needs ROLE_MANAGE somewhere in the clan")
        void listingNeedsRoleManagement() {
            assertThatThrownBy(() -> fixtures.grantService.list(memberD, clanId, null, null))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.PERMISSION_DENIED);

            assertThatThrownBy(
                            () ->
                                    fixtures.grantService.list(
                                            fixtures.newUser("Người lạ"), clanId, null, null))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.CLAN_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("last-administrator protection (I-M3, doc 04 §6)")
    class LastAdmin {

        @Test
        @DisplayName("the only administrator cannot revoke their own CLAN_ADMIN")
        void cannotSelfDemote() {
            UUID bootstrapGrantId = adminGrantId(admin);

            assertThatThrownBy(() -> fixtures.grantService.revoke(admin, clanId, bootstrapGrantId))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.LAST_ADMIN_PROTECTED);
        }

        @Test
        @DisplayName("the only administrator cannot be removed from the clan")
        void cannotBeRemoved() {
            assertThatThrownBy(() -> fixtures.membershipService.remove(admin, clanId, admin))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.LAST_ADMIN_PROTECTED);
        }

        @Test
        @DisplayName("the only administrator cannot leave")
        void cannotLeave() {
            assertThatThrownBy(() -> fixtures.membershipService.leave(admin, clanId))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.LAST_ADMIN_PROTECTED);
        }

        @Test
        @DisplayName("once there are two, either may step down")
        void secondAdminUnlocksTheFirst() {
            grant(admin, editorC, Permission.CLAN_ADMIN, ScopeType.CLAN, null);

            fixtures.grantService.revoke(admin, clanId, adminGrantId(admin));

            assertThat(fixtures.permissions.permissionNames(admin, clanId))
                    .doesNotContain("CLAN_ADMIN");
            assertThat(fixtures.permissions.permissionNames(editorC, clanId)).contains("CLAN_ADMIN");

            // …and the protection now applies to whoever is left.
            assertThatThrownBy(
                            () -> fixtures.grantService.revoke(editorC, clanId, adminGrantId(editorC)))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.LAST_ADMIN_PROTECTED);
        }

        @Test
        @DisplayName("an administrator who has left the clan does not count as one")
        void departedAdminsDoNotProtect() {
            grant(admin, editorC, Permission.CLAN_ADMIN, ScopeType.CLAN, null);
            fixtures.membershipService.remove(admin, clanId, editorC);

            // editorC's grant was revoked with their membership, so admin is alone again.
            assertThatThrownBy(() -> fixtures.membershipService.leave(admin, clanId))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.LAST_ADMIN_PROTECTED);
        }

        @Test
        @DisplayName("revoking is idempotent, and a second call is not an error")
        void revokeIsIdempotent() {
            GrantView issued =
                    grant(admin, editorC, Permission.TREE_EDIT, ScopeType.BRANCH, chiHai.value());

            fixtures.grantService.revoke(admin, clanId, issued.id());
            fixtures.grantService.revoke(admin, clanId, issued.id());

            assertThat(fixtures.permissions.permissionNames(editorC, clanId))
                    .doesNotContain("TREE_EDIT");
            assertThat(fixtures.audit.actions())
                    .filteredOn(action -> action == AuditAction.PERMISSION_REVOKED)
                    .hasSize(1);
        }

        @Test
        @DisplayName("revoking needs ROLE_MANAGE over the grant's own scope, not the revoker's")
        void revocationIsScopedToTheGrant() {
            grant(admin, editorC, Permission.ROLE_MANAGE, ScopeType.BRANCH, chiHai.value());
            GrantView inTheOtherChi =
                    grant(admin, memberD, Permission.TREE_EDIT, ScopeType.BRANCH, chiBa.value());

            assertThatThrownBy(
                            () -> fixtures.grantService.revoke(editorC, clanId, inTheOtherChi.id()))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo(ErrorCode.OUT_OF_SCOPE);
        }
    }

    // --- helpers -----------------------------------------------------------------------------

    private void join(UserId user, BranchId homeBranch) {
        joinInto(user, homeBranch);
    }

    private void joinInto(UserId user, BranchId homeBranch) {
        JoinRequestView request =
                fixtures.joinRequestService.submit(
                        user, new SubmitJoinRequestCommand(clanCode, null, null));
        fixtures.joinRequestService.approve(admin, clanId, request.id(), homeBranch);
    }

    private GrantView grant(
            UserId actor, UserId grantee, Permission permission, ScopeType scopeType, UUID scopeRef) {

        return fixtures.grantService.grant(
                actor,
                clanId,
                new GrantCommand(grantee, permission.name(), scopeType.name(), scopeRef, null));
    }

    /** The id of somebody's live {@code CLAN_ADMIN} grant. */
    private UUID adminGrantId(UserId user) {
        return fixtures.grants.all().stream()
                .filter(grant -> grant.granteeUserId().equals(user))
                .filter(grant -> grant.permission() == Permission.CLAN_ADMIN)
                .filter(grant -> !grant.isRevoked())
                .findFirst()
                .orElseThrow()
                .id();
    }

    private static List<String> scopesFor(GrantableView offered, String permission) {
        return offered.permissions().stream()
                .filter(entry -> entry.permission().equals(permission))
                .flatMap(entry -> entry.scopes().stream())
                .map(GrantableView.GrantableScope::scopeType)
                .distinct()
                .toList();
    }

    /** Kept so the import of {@link Target} and {@link PermissionScope} stays meaningful. */
    @Test
    @DisplayName("a grant's scope maps to the target the resolver is asked about")
    void scopeMapsToTarget() {
        assertThat(Target.forScope(clanId, PermissionScope.clan()))
                .isInstanceOf(Target.ClanTarget.class);
        assertThat(Target.forScope(clanId, PermissionScope.branch(chiHai)))
                .isEqualTo(Target.branch(clanId, chiHai));
        assertThat(Target.forScope(clanId, PermissionScope.subtree(UUID.randomUUID())))
                .isInstanceOf(Target.PersonTarget.class);
    }
}
