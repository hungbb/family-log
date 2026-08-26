package app.giaky.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.common.audit.AuditAction;
import app.giaky.common.error.AuthenticationRequiredException;
import app.giaky.common.error.ConflictException;
import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.TooManyRequestsException;
import app.giaky.common.security.HmacJwtKeyProvider;
import app.giaky.common.security.JwtService;
import app.giaky.common.types.IdGenerator;
import app.giaky.common.web.RateLimitPolicy;
import app.giaky.common.web.RateLimiter;
import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.LocaleTag;
import app.giaky.identity.domain.TokenPurpose;
import app.giaky.identity.domain.User;
import app.giaky.identity.domain.UserStatus;
import app.giaky.identity.support.FakePasswordHasher;
import app.giaky.identity.support.IdentityTestFixtures;
import app.giaky.identity.support.InMemoryRefreshTokenRepository;
import app.giaky.identity.support.InMemoryUserRepository;
import app.giaky.identity.support.InMemoryVerificationTokenRepository;
import app.giaky.identity.support.RecordingAuditWriter;
import app.giaky.identity.support.RecordingTokenDelivery;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The use cases of FR-A1, A3, A5 and A6, driven against in-memory ports.
 *
 * <p>The assertions that matter most are the negative ones: that a failure says nothing about
 * whether an address has an account, and that revoking sessions really revokes them.
 */
class AuthServiceTest {

    private static final String EMAIL = "an@example.com";
    private static final String PASSWORD = IdentityTestFixtures.VALID_PASSWORD;
    private static final ClientContext CLIENT = new ClientContext("203.0.113.7", "Firefox");

    private InMemoryUserRepository users;
    private InMemoryRefreshTokenRepository refreshTokens;
    private InMemoryVerificationTokenRepository verificationTokens;
    private RecordingTokenDelivery delivery;
    private RecordingAuditWriter audit;
    private FakePasswordHasher hasher;
    private RateLimiter rateLimiter;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        refreshTokens = new InMemoryRefreshTokenRepository();
        verificationTokens = new InMemoryVerificationTokenRepository();
        delivery = new RecordingTokenDelivery();
        audit = new RecordingAuditWriter();
        hasher = new FakePasswordHasher();
        rateLimiter = new RateLimiter();
        authService = build(IdentityTestFixtures.fixedClock());
    }

    private AuthService build(Clock clock) {
        IdGenerator ids = IdentityTestFixtures.idGenerator();
        var properties = IdentityTestFixtures.securityProperties();
        return new AuthService(
                users,
                hasher,
                IdentityTestFixtures.passwordPolicy(),
                new RefreshTokenService(refreshTokens, audit, ids, clock, properties),
                new VerificationTokenService(verificationTokens, delivery, ids, clock, properties),
                new JwtService(new HmacJwtKeyProvider(properties), clock, properties),
                audit,
                rateLimiter,
                ids,
                clock);
    }

    private Registration registerAn() {
        return authService.register(
                new RegisterCommand(EMAIL, PASSWORD, "An Nguyen", LocaleTag.VI_VN, CLIENT));
    }

    // --- registration ---------------------------------------------------------------------------

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("creates a pending account, audits it, and issues a verification token")
        void createsAccount() {
            Registration registration = registerAn();

            assertThat(registration.status()).isEqualTo(UserStatus.PENDING_VERIFICATION);
            assertThat(users.findById(registration.userId())).isPresent();
            assertThat(delivery.latest(TokenPurpose.EMAIL_VERIFY)).isPresent();
            assertThat(audit.recorded(AuditAction.USER_REGISTERED)).isTrue();
        }

        @Test
        @DisplayName("normalizes the address, so two spellings cannot become two accounts")
        void normalizesEmail() {
            authService.register(
                    new RegisterCommand("  An@Example.COM ", PASSWORD, "An", null, CLIENT));

            assertThat(users.findByEmail(EmailAddress.of(EMAIL))).isPresent();
            assertThatThrownBy(
                            () ->
                                    authService.register(
                                            new RegisterCommand(
                                                    "AN@EXAMPLE.COM", PASSWORD, "An", null, CLIENT)))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("defaults the locale to Vietnamese")
        void defaultsLocale() {
            Registration registration =
                    authService.register(new RegisterCommand(EMAIL, PASSWORD, "An", null, CLIENT));

            assertThat(users.findById(registration.userId()).orElseThrow().locale())
                    .isEqualTo(LocaleTag.VI_VN);
        }

        @Test
        @DisplayName("a duplicate address is 409 EMAIL_ALREADY_REGISTERED")
        void duplicateEmailConflicts() {
            registerAn();

            assertThatThrownBy(AuthServiceTest.this::registerAn)
                    .isInstanceOf(ConflictException.class)
                    .satisfies(
                            thrown ->
                                    assertThat(((ConflictException) thrown).code())
                                            .isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED));
            assertThat(users.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("a password the policy refuses is 422, with the reason on the password field")
        void weakPasswordIsRejectedWithFieldErrors() {
            assertThatThrownBy(
                            () ->
                                    authService.register(
                                            new RegisterCommand(EMAIL, "short", "An", null, CLIENT)))
                    .isInstanceOf(DomainRuleViolation.class)
                    .satisfies(
                            thrown -> {
                                DomainRuleViolation violation = (DomainRuleViolation) thrown;
                                assertThat(violation.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                                assertThat(violation.errors())
                                        .isNotEmpty()
                                        .allSatisfy(
                                                error ->
                                                        assertThat(error.field())
                                                                .isEqualTo("password"));
                            });
            assertThat(users.count()).isZero();
        }

        @Test
        @DisplayName("a password on the deny list is refused")
        void deniedPasswordIsRejected() {
            assertThatThrownBy(
                            () ->
                                    authService.register(
                                            new RegisterCommand(
                                                    EMAIL, "password123", "An", null, CLIENT)))
                    .isInstanceOf(DomainRuleViolation.class);
        }
    }

    // --- login ----------------------------------------------------------------------------------

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("returns a token pair and the profile, and stamps the login")
        void succeedsWithCorrectCredentials() {
            Registration registration = registerAn();

            SessionTokens tokens = authService.login(new LoginCommand(EMAIL, PASSWORD, CLIENT));

            assertThat(tokens.accessToken()).isNotBlank();
            assertThat(tokens.refreshToken()).isNotBlank();
            assertThat(tokens.user().email()).isEqualTo(EMAIL);
            assertThat(users.findById(registration.userId()).orElseThrow().lastLoginAt())
                    .isEqualTo(IdentityTestFixtures.NOW);
            assertThat(audit.recorded(AuditAction.USER_LOGIN)).isTrue();
        }

        @Test
        @DisplayName("an unverified account may still sign in (phase doc §8)")
        void pendingVerificationCanSignIn() {
            registerAn();

            assertThat(authService.login(new LoginCommand(EMAIL, PASSWORD, CLIENT)).user().status())
                    .isEqualTo(UserStatus.PENDING_VERIFICATION);
        }

        @Test
        @DisplayName("a wrong password and an unknown address fail identically")
        void failuresAreIndistinguishable() {
            registerAn();

            Throwable wrongPassword =
                    org.assertj.core.api.Assertions.catchThrowable(
                            () -> authService.login(new LoginCommand(EMAIL, "wrong-password-x", CLIENT)));
            Throwable unknownAddress =
                    org.assertj.core.api.Assertions.catchThrowable(
                            () ->
                                    authService.login(
                                            new LoginCommand("nobody@example.com", PASSWORD, CLIENT)));

            assertThat(wrongPassword).isInstanceOf(AuthenticationRequiredException.class);
            assertThat(unknownAddress).isInstanceOf(AuthenticationRequiredException.class);
            assertThat(wrongPassword.getMessage()).isEqualTo(unknownAddress.getMessage());
            assertThat(((AuthenticationRequiredException) wrongPassword).code())
                    .isEqualTo(((AuthenticationRequiredException) unknownAddress).code())
                    .isEqualTo(ErrorCode.AUTH_REQUIRED);
        }

        @Test
        @DisplayName("an unknown address still costs a hash verification, so timing says nothing either")
        void unknownAddressStillVerifiesAgainstADecoy() {
            int before = hasher.matchCalls();

            assertThatThrownBy(
                            () ->
                                    authService.login(
                                            new LoginCommand("nobody@example.com", PASSWORD, CLIENT)))
                    .isInstanceOf(AuthenticationRequiredException.class);

            assertThat(hasher.matchCalls()).isEqualTo(before + 1);
        }

        @Test
        @DisplayName("an address that is not an address fails the same way, not with a 500")
        void malformedAddressFailsLikeAnyOther() {
            assertThatThrownBy(
                            () -> authService.login(new LoginCommand("not-an-address", PASSWORD, CLIENT)))
                    .isInstanceOf(AuthenticationRequiredException.class)
                    .hasMessage("Email or password is incorrect.");
        }

        @Test
        @DisplayName("a suspended account cannot sign in, and is not told so (I-U2)")
        void suspendedAccountCannotSignIn() {
            Registration registration = registerAn();
            User suspended = users.findById(registration.userId()).orElseThrow();
            users.save(
                    new User(
                            suspended.id(),
                            suspended.email(),
                            suspended.passwordHash(),
                            suspended.displayName(),
                            null,
                            suspended.locale(),
                            UserStatus.SUSPENDED,
                            null,
                            null,
                            suspended.tokenVersion(),
                            suspended.createdAt(),
                            suspended.updatedAt()));

            assertThatThrownBy(() -> authService.login(new LoginCommand(EMAIL, PASSWORD, CLIENT)))
                    .isInstanceOf(AuthenticationRequiredException.class)
                    .hasMessage("Email or password is incorrect.");
        }

        @Test
        @DisplayName("every rejected attempt is audited, including one for an address nobody owns")
        void failedAttemptsAreAudited() {
            assertThatThrownBy(
                            () ->
                                    authService.login(
                                            new LoginCommand("nobody@example.com", PASSWORD, CLIENT)))
                    .isInstanceOf(AuthenticationRequiredException.class);

            assertThat(audit.recorded(AuditAction.LOGIN_FAILED)).isTrue();
            assertThat(audit.written().getFirst().actorUserId()).isNull();
            assertThat(audit.written().getFirst().ipAddress()).isEqualTo("203.0.113.7");
        }

        @Test
        @DisplayName("attempts against one account are limited, whatever address they come from")
        void perAccountRateLimitApplies() {
            registerAn();

            for (int attempt = 0; attempt < RateLimitPolicy.LOGIN_PER_ACCOUNT.permits(); attempt++) {
                assertThatThrownBy(
                                () ->
                                        authService.login(
                                                new LoginCommand(EMAIL, "wrong-password-x", CLIENT)))
                        .isInstanceOf(AuthenticationRequiredException.class);
            }

            assertThatThrownBy(() -> authService.login(new LoginCommand(EMAIL, PASSWORD, CLIENT)))
                    .isInstanceOf(TooManyRequestsException.class)
                    .satisfies(
                            thrown ->
                                    assertThat(((TooManyRequestsException) thrown).retryAfter())
                                            .isPositive());
        }

        @Test
        @DisplayName("the rate-limit key ignores case and padding")
        void rateLimitKeyIsNormalized() {
            for (int attempt = 0; attempt < RateLimitPolicy.LOGIN_PER_ACCOUNT.permits(); attempt++) {
                assertThatThrownBy(
                                () -> authService.login(new LoginCommand(EMAIL, PASSWORD, CLIENT)))
                        .isInstanceOf(AuthenticationRequiredException.class);
            }

            assertThatThrownBy(
                            () -> authService.login(new LoginCommand("  AN@EXAMPLE.COM ", PASSWORD, CLIENT)))
                    .isInstanceOf(TooManyRequestsException.class);
        }
    }

    // --- sessions -------------------------------------------------------------------------------

    @Nested
    @DisplayName("sessions")
    class Sessions {

        @Test
        @DisplayName("refreshing returns a new pair for the same account")
        void refreshRotates() {
            registerAn();
            SessionTokens first = authService.login(new LoginCommand(EMAIL, PASSWORD, CLIENT));

            SessionTokens second = authService.refresh(first.refreshToken(), CLIENT);

            assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
            assertThat(second.user().email()).isEqualTo(EMAIL);
        }

        @Test
        @DisplayName("refreshing a suspended account's session fails")
        void refreshRechecksTheAccount() {
            Registration registration = registerAn();
            SessionTokens tokens = authService.login(new LoginCommand(EMAIL, PASSWORD, CLIENT));
            User user = users.findById(registration.userId()).orElseThrow();
            users.save(
                    new User(
                            user.id(),
                            user.email(),
                            user.passwordHash(),
                            user.displayName(),
                            null,
                            user.locale(),
                            UserStatus.DEACTIVATED,
                            null,
                            null,
                            user.tokenVersion(),
                            user.createdAt(),
                            user.updatedAt()));

            assertThatThrownBy(() -> authService.refresh(tokens.refreshToken(), CLIENT))
                    .isInstanceOf(AuthenticationRequiredException.class);
        }

        @Test
        @DisplayName("signing out ends that session only")
        void logoutEndsOneSession() {
            Registration registration = registerAn();
            SessionTokens phone = authService.login(new LoginCommand(EMAIL, PASSWORD, CLIENT));
            SessionTokens laptop = authService.login(new LoginCommand(EMAIL, PASSWORD, CLIENT));

            authService.logout(phone.refreshToken(), registration.userId());

            assertThatThrownBy(() -> authService.refresh(phone.refreshToken(), CLIENT))
                    .isInstanceOf(AuthenticationRequiredException.class);
            assertThat(authService.refresh(laptop.refreshToken(), CLIENT)).isNotNull();
            assertThat(audit.recorded(AuditAction.USER_LOGGED_OUT)).isTrue();
        }

        @Test
        @DisplayName("logging out everywhere ends every session and bumps tokenVersion")
        void logoutAllEndsEverythingIncludingAccessTokens() {
            Registration registration = registerAn();
            SessionTokens phone = authService.login(new LoginCommand(EMAIL, PASSWORD, CLIENT));
            SessionTokens laptop = authService.login(new LoginCommand(EMAIL, PASSWORD, CLIENT));

            authService.logoutAll(registration.userId());

            assertThatThrownBy(() -> authService.refresh(phone.refreshToken(), CLIENT))
                    .isInstanceOf(AuthenticationRequiredException.class);
            assertThatThrownBy(() -> authService.refresh(laptop.refreshToken(), CLIENT))
                    .isInstanceOf(AuthenticationRequiredException.class);
            // The half that a refresh-token revocation alone would miss: outstanding access tokens
            // now carry a stale version and stop working on their next request.
            assertThat(users.findById(registration.userId()).orElseThrow().tokenVersion())
                    .isEqualTo(1L);
            assertThat(audit.recorded(AuditAction.ALL_SESSIONS_REVOKED)).isTrue();
        }
    }

    // --- verification and recovery ----------------------------------------------------------------

    @Nested
    @DisplayName("verification and recovery")
    class Recovery {

        @Test
        @DisplayName("verifying promotes the account to ACTIVE")
        void verifyEmailActivates() {
            Registration registration = registerAn();
            String token = delivery.latest(TokenPurpose.EMAIL_VERIFY).orElseThrow();

            authService.verifyEmail(token);

            User user = users.findById(registration.userId()).orElseThrow();
            assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.isEmailVerified()).isTrue();
            assertThat(audit.recorded(AuditAction.EMAIL_VERIFIED)).isTrue();
        }

        @Test
        @DisplayName("an invalid verification token is rejected")
        void verifyEmailRejectsUnknownToken() {
            assertThatThrownBy(() -> authService.verifyEmail("never-issued"))
                    .isInstanceOf(AuthenticationRequiredException.class);
        }

        @Test
        @DisplayName("forgot-password issues a reset token for an account that exists")
        void forgotPasswordIssuesToken() {
            registerAn();

            authService.forgotPassword(EMAIL, CLIENT);

            assertThat(delivery.latest(TokenPurpose.PASSWORD_RESET)).isPresent();
            assertThat(audit.recorded(AuditAction.PASSWORD_RESET_REQUESTED)).isTrue();
        }

        @Test
        @DisplayName("forgot-password is silent about an address with no account")
        void forgotPasswordIsSilentForUnknownAddresses() {
            authService.forgotPassword("nobody@example.com", CLIENT);
            authService.forgotPassword("not-an-address", CLIENT);

            // No exception, no token, nothing the caller can tell apart from the success case.
            assertThat(delivery.all()).isEmpty();
        }

        @Test
        @DisplayName("resetting sets the new password and ends every session")
        void resetPasswordEndsEverySession() {
            Registration registration = registerAn();
            SessionTokens session = authService.login(new LoginCommand(EMAIL, PASSWORD, CLIENT));
            authService.forgotPassword(EMAIL, CLIENT);
            String token = delivery.latest(TokenPurpose.PASSWORD_RESET).orElseThrow();

            authService.resetPassword(token, "a-brand-new-passphrase");

            // The point of the reset: whoever was in the account is out of it.
            assertThatThrownBy(() -> authService.refresh(session.refreshToken(), CLIENT))
                    .isInstanceOf(AuthenticationRequiredException.class);
            assertThat(users.findById(registration.userId()).orElseThrow().tokenVersion())
                    .isEqualTo(1L);
            assertThat(audit.recorded(AuditAction.PASSWORD_RESET)).isTrue();

            assertThat(authService.login(new LoginCommand(EMAIL, "a-brand-new-passphrase", CLIENT)))
                    .isNotNull();
        }

        @Test
        @DisplayName("the old password stops working after a reset")
        void oldPasswordStopsWorking() {
            registerAn();
            authService.forgotPassword(EMAIL, CLIENT);
            String token = delivery.latest(TokenPurpose.PASSWORD_RESET).orElseThrow();
            authService.resetPassword(token, "a-brand-new-passphrase");
            rateLimiter.reset();

            assertThatThrownBy(() -> authService.login(new LoginCommand(EMAIL, PASSWORD, CLIENT)))
                    .isInstanceOf(AuthenticationRequiredException.class);
        }

        @Test
        @DisplayName("a reset token is single-use")
        void resetTokenIsSingleUse() {
            registerAn();
            authService.forgotPassword(EMAIL, CLIENT);
            String token = delivery.latest(TokenPurpose.PASSWORD_RESET).orElseThrow();
            authService.resetPassword(token, "a-brand-new-passphrase");

            assertThatThrownBy(() -> authService.resetPassword(token, "another-new-passphrase"))
                    .isInstanceOf(AuthenticationRequiredException.class);
        }

        @Test
        @DisplayName("a reset is refused if the new password fails the policy")
        void resetChecksThePolicy() {
            registerAn();
            authService.forgotPassword(EMAIL, CLIENT);
            String token = delivery.latest(TokenPurpose.PASSWORD_RESET).orElseThrow();

            assertThatThrownBy(() -> authService.resetPassword(token, "short"))
                    .isInstanceOf(DomainRuleViolation.class);

            // And the token survives, so the person can try again with a better password.
            authService.resetPassword(token, "a-brand-new-passphrase");
        }
    }
}
