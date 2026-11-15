package app.giaky.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.common.audit.AuditAction;
import app.giaky.common.audit.AuditRecord;
import app.giaky.common.error.ApiException;
import app.giaky.common.error.ConflictException;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.error.PermissionDeniedException;
import app.giaky.common.types.Paged;
import app.giaky.common.types.UserId;
import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.LocaleTag;
import app.giaky.identity.domain.PasswordHash;
import app.giaky.identity.domain.RefreshToken;
import app.giaky.identity.domain.TokenPurpose;
import app.giaky.identity.domain.User;
import app.giaky.identity.domain.UserStatus;
import app.giaky.identity.support.IdentityTestFixtures;
import app.giaky.identity.support.InMemoryRefreshTokenRepository;
import app.giaky.identity.support.InMemoryUserRepository;
import app.giaky.identity.support.InMemoryVerificationTokenRepository;
import app.giaky.identity.support.RecordingAuditWriter;
import app.giaky.identity.support.RecordingTokenDelivery;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserAdministrationServiceTest {

    private static final String OPERATOR_EMAIL = "operator@example.com";
    private static final String SECOND_OPERATOR_EMAIL = "deputy@example.com";

    private InMemoryUserRepository users;
    private InMemoryRefreshTokenRepository refreshTokens;
    private RecordingTokenDelivery delivery;
    private RecordingAuditWriter audit;
    private RefreshTokenService refreshTokenService;
    private SuperAdminGuard guard;
    private UserAdministrationService service;

    private User operator;
    private User deputy;
    private User an;

    @BeforeEach
    void setUp() {
        Clock clock = IdentityTestFixtures.fixedClock();
        users = new InMemoryUserRepository();
        refreshTokens = new InMemoryRefreshTokenRepository();
        delivery = new RecordingTokenDelivery();
        audit = new RecordingAuditWriter();
        refreshTokenService =
                new RefreshTokenService(
                        refreshTokens,
                        audit,
                        IdentityTestFixtures.idGenerator(),
                        clock,
                        IdentityTestFixtures.securityProperties());
        guard = new SuperAdminGuard(users, List.of(" Operator@Example.com ", SECOND_OPERATOR_EMAIL, ""));
        service =
                new UserAdministrationService(
                        guard,
                        users,
                        refreshTokenService,
                        new VerificationTokenService(
                                new InMemoryVerificationTokenRepository(),
                                delivery,
                                IdentityTestFixtures.idGenerator(),
                                clock,
                                IdentityTestFixtures.securityProperties()),
                        audit,
                        clock);

        operator = users.save(verified(account(OPERATOR_EMAIL, "Operator", 0)));
        deputy = users.save(verified(account(SECOND_OPERATOR_EMAIL, "Deputy", 1)));
        an = users.save(account("an@example.com", "An Nguyen", 2));
    }

    @Nested
    @DisplayName("who is a super admin")
    class Recognition {

        @Test
        @DisplayName("a configured, verified, active account is one; matching ignores case and padding")
        void configuredVerifiedAccount() {
            assertThat(guard.isSuperAdmin(operator.id())).isTrue();
        }

        @Test
        @DisplayName("an ordinary account is not")
        void ordinaryAccount() {
            assertThat(guard.isSuperAdmin(an.id())).isFalse();
            assertThatThrownBy(() -> guard.require(an.id()))
                    .isInstanceOf(PermissionDeniedException.class)
                    .extracting(e -> ((ApiException) e).code())
                    .isEqualTo(ErrorCode.PERMISSION_DENIED);
        }

        @Test
        @DisplayName("a configured address that is not verified is not, so registering it first wins nothing")
        void unverifiedConfiguredAddress() {
            User squatter = users.save(account("squatter@example.com", "Squatter", 3));
            SuperAdminGuard squatted =
                    new SuperAdminGuard(users, List.of("squatter@example.com"));

            assertThat(squatted.isSuperAdmin(squatter.id())).isFalse();
        }

        @Test
        @DisplayName("an account that does not exist is not")
        void missingAccount() {
            assertThat(guard.isSuperAdmin(UserId.of(UUID.randomUUID()))).isFalse();
        }
    }

    @Nested
    @DisplayName("every operation is refused to a non-admin")
    class Refusals {

        @Test
        void list() {
            assertThatThrownBy(() -> service.list(an.id(), null, null, 0, 20))
                    .isInstanceOf(PermissionDeniedException.class);
        }

        @Test
        void suspend() {
            assertThatThrownBy(() -> service.suspend(an.id(), deputy.id()))
                    .isInstanceOf(PermissionDeniedException.class);
            assertThat(users.findById(deputy.id()).orElseThrow().status()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        void sendPasswordReset() {
            assertThatThrownBy(() -> service.sendPasswordReset(an.id(), operator.id()))
                    .isInstanceOf(PermissionDeniedException.class);
            assertThat(delivery.all()).isEmpty();
        }
    }

    @Nested
    @DisplayName("listing")
    class Listing {

        @Test
        @DisplayName("lists every account, newest first, marking the super admins")
        void listsEveryone() {
            Paged<AdminUserView> page = service.list(operator.id(), null, null, 0, 20);

            assertThat(page.content())
                    .extracting(AdminUserView::email)
                    .containsExactly("an@example.com", SECOND_OPERATOR_EMAIL, OPERATOR_EMAIL);
            assertThat(page.content())
                    .extracting(AdminUserView::superAdmin)
                    .containsExactly(false, true, true);
        }

        @Test
        @DisplayName("filters by name or address, and by status")
        void filters() {
            assertThat(service.list(operator.id(), "nguyen", null, 0, 20).content())
                    .extracting(AdminUserView::email)
                    .containsExactly("an@example.com");
            assertThat(
                            service.list(operator.id(), null, UserStatus.PENDING_VERIFICATION, 0, 20)
                                    .content())
                    .extracting(AdminUserView::email)
                    .containsExactly("an@example.com");
        }

        @Test
        @DisplayName("a page size beyond the cap is clamped rather than honoured")
        void clampsPageSize() {
            assertThat(service.list(operator.id(), null, null, 0, 10_000).size())
                    .isEqualTo(UserAdministrationService.MAX_PAGE_SIZE);
        }
    }

    @Nested
    @DisplayName("suspension")
    class Suspension {

        @Test
        @DisplayName("bars the account, ends every session, bumps the token version, and is audited")
        void suspends() {
            refreshTokenService.open(an.id(), new ClientContext("10.0.0.1", "agent"));
            long versionBefore = an.tokenVersion();

            AdminUserView result = service.suspend(operator.id(), an.id());

            User stored = users.findById(an.id()).orElseThrow();
            assertThat(result.status()).isEqualTo(UserStatus.SUSPENDED);
            assertThat(stored.status()).isEqualTo(UserStatus.SUSPENDED);
            assertThat(stored.canAuthenticate()).isFalse();
            assertThat(stored.tokenVersion()).isEqualTo(versionBefore + 1);
            assertThat(refreshTokens.all())
                    .allSatisfy(
                            token ->
                                    assertThat(token.revokedReason())
                                            .isEqualTo(RefreshToken.RevocationReason.SUSPENDED));

            AuditRecord entry = lastAudit(AuditAction.USER_SUSPENDED);
            assertThat(entry.actorUserId()).isEqualTo(operator.id());
            assertThat(entry.targetId()).isEqualTo(an.id().value());
        }

        @Test
        @DisplayName("a super admin cannot suspend themselves")
        void notSelf() {
            assertThatThrownBy(() -> service.suspend(operator.id(), operator.id()))
                    .isInstanceOf(PermissionDeniedException.class)
                    .extracting(e -> ((ApiException) e).code())
                    .isEqualTo(ErrorCode.SUPER_ADMIN_PROTECTED);
        }

        @Test
        @DisplayName("nor another super admin")
        void notAnotherSuperAdmin() {
            assertThatThrownBy(() -> service.suspend(operator.id(), deputy.id()))
                    .extracting(e -> ((ApiException) e).code())
                    .isEqualTo(ErrorCode.SUPER_ADMIN_PROTECTED);
        }

        @Test
        @DisplayName("suspending twice is a conflict, not a second audit entry")
        void twiceIsAConflict() {
            service.suspend(operator.id(), an.id());
            audit.clear();

            assertThatThrownBy(() -> service.suspend(operator.id(), an.id()))
                    .isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ApiException) e).code())
                    .isEqualTo(ErrorCode.USER_STATUS_CONFLICT);
            assertThat(audit.written()).isEmpty();
        }

        @Test
        @DisplayName("an unknown account is a 404")
        void unknown() {
            assertThatThrownBy(() -> service.suspend(operator.id(), UserId.of(UUID.randomUUID())))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("reactivating restores the state the account would otherwise have")
        void reactivates() {
            service.suspend(operator.id(), an.id());

            AdminUserView result = service.reactivate(operator.id(), an.id());

            // An was never verified, so reactivation does not vouch for the address.
            assertThat(result.status()).isEqualTo(UserStatus.PENDING_VERIFICATION);
            assertThat(audit.recorded(AuditAction.USER_REACTIVATED)).isTrue();
        }

        @Test
        @DisplayName("reactivating an account that is not suspended is a conflict")
        void reactivateActive() {
            assertThatThrownBy(() -> service.reactivate(operator.id(), an.id()))
                    .extracting(e -> ((ApiException) e).code())
                    .isEqualTo(ErrorCode.USER_STATUS_CONFLICT);
        }
    }

    @Nested
    @DisplayName("verification and recovery")
    class Recovery {

        @Test
        @DisplayName("marking verified activates the account, once, and says who vouched")
        void marksVerified() {
            AdminUserView result = service.markEmailVerified(operator.id(), an.id());

            assertThat(result.emailVerified()).isTrue();
            assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(lastAudit(AuditAction.EMAIL_VERIFIED_BY_ADMIN).actorUserId())
                    .isEqualTo(operator.id());

            audit.clear();
            service.markEmailVerified(operator.id(), an.id());
            assertThat(audit.written()).as("idempotent: nothing happened the second time").isEmpty();
        }

        @Test
        @DisplayName("resending verification issues a fresh link to the account's own address")
        void resendsVerification() {
            service.resendVerification(operator.id(), an.id());

            assertThat(delivery.all())
                    .singleElement()
                    .satisfies(
                            sent -> {
                                assertThat(sent.recipient().value()).isEqualTo("an@example.com");
                                assertThat(sent.purpose()).isEqualTo(TokenPurpose.EMAIL_VERIFY);
                            });
            assertThat(audit.recorded(AuditAction.VERIFICATION_RESENT_BY_ADMIN)).isTrue();
        }

        @Test
        @DisplayName("resending to a verified address is a conflict")
        void resendToVerified() {
            assertThatThrownBy(() -> service.resendVerification(operator.id(), deputy.id()))
                    .extracting(e -> ((ApiException) e).code())
                    .isEqualTo(ErrorCode.USER_STATUS_CONFLICT);
            assertThat(delivery.all()).isEmpty();
        }

        @Test
        @DisplayName("a reset link goes to the holder; the operator never sets the password")
        void sendsReset() {
            PasswordHash before = an.passwordHash();

            service.sendPasswordReset(operator.id(), an.id());

            assertThat(delivery.latest(TokenPurpose.PASSWORD_RESET)).isPresent();
            assertThat(users.findById(an.id()).orElseThrow().passwordHash()).isEqualTo(before);
            assertThat(audit.recorded(AuditAction.PASSWORD_RESET_SENT_BY_ADMIN)).isTrue();
        }

        @Test
        @DisplayName("no reset link for a suspended account, which could not use it")
        void noResetWhenSuspended() {
            service.suspend(operator.id(), an.id());

            assertThatThrownBy(() -> service.sendPasswordReset(operator.id(), an.id()))
                    .extracting(e -> ((ApiException) e).code())
                    .isEqualTo(ErrorCode.USER_STATUS_CONFLICT);
        }
    }

    private AuditRecord lastAudit(AuditAction action) {
        return audit.written().stream()
                .filter(entry -> entry.action() == action)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("no " + action + " entry"));
    }

    /** Created {@code minutesLater} after the fixed clock, so "newest first" has an order. */
    private static User account(String email, String name, int minutesLater) {
        return User.register(
                UserId.of(UUID.randomUUID()),
                EmailAddress.of(email),
                PasswordHash.of("$2a$12$hash-" + name),
                name,
                LocaleTag.VI_VN,
                IdentityTestFixtures.NOW.plus(Duration.ofMinutes(minutesLater)));
    }

    private static User verified(User user) {
        return user.verifyEmail(user.createdAt());
    }
}
