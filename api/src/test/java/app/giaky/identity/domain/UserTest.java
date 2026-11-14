package app.giaky.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.common.types.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The account lifecycle of doc 02 §2.1, tested without Spring and without a database. */
class UserTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant LATER = NOW.plus(Duration.ofHours(3));
    private static final String NAME = "Nguyen Van An";

    private static User registered() {
        return User.register(
                UserId.of(UUID.randomUUID()),
                EmailAddress.of("an@example.com"),
                PasswordHash.of("$2a$12$hash"),
                NAME,
                LocaleTag.VI_VN,
                NOW);
    }

    private static User withStatus(UserStatus status) {
        return new User(
                UserId.of(UUID.randomUUID()),
                EmailAddress.of("an@example.com"),
                PasswordHash.of("$2a$12$hash"),
                NAME,
                null,
                LocaleTag.VI_VN,
                status,
                null,
                null,
                0L,
                NOW,
                NOW);
    }

    @Test
    @DisplayName("a new account starts unverified but able to sign in")
    void registrationStartsPendingVerification() {
        User user = registered();

        assertThat(user.status()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.canAuthenticate()).isTrue();
        assertThat(user.tokenVersion()).isZero();
        assertThat(user.lastLoginAt()).isNull();
        assertThat(user.avatarMediaId()).isNull();
    }

    @Test
    @DisplayName("the display name is trimmed")
    void displayNameIsTrimmed() {
        User user = registered().updateProfile("  An Nguyen  ", null, LATER);

        assertThat(user.displayName()).isEqualTo("An Nguyen");
    }

    @Test
    @DisplayName("a blank or overlong display name is refused")
    void displayNameIsValidated() {
        assertThatThrownBy(() -> registered().updateProfile("   ", null, LATER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                registered()
                                        .updateProfile(
                                                "x".repeat(User.DISPLAY_NAME_MAX_LENGTH + 1),
                                                null,
                                                LATER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Nested
    @DisplayName("verifying the address")
    class Verification {

        @Test
        @DisplayName("promotes a pending account to ACTIVE and stamps the time")
        void promotesToActive() {
            User verified = registered().verifyEmail(LATER);

            assertThat(verified.status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(verified.emailVerifiedAt()).isEqualTo(LATER);
            assertThat(verified.isEmailVerified()).isTrue();
            assertThat(verified.updatedAt()).isEqualTo(LATER);
        }

        @Test
        @DisplayName("is idempotent, so a link opened twice is not an error")
        void isIdempotent() {
            User once = registered().verifyEmail(LATER);

            assertThat(once.verifyEmail(LATER.plusSeconds(60))).isSameAs(once);
        }

        @Test
        @DisplayName("does not revive an account an operator suspended")
        void doesNotReviveSuspendedAccounts() {
            User verified = withStatus(UserStatus.SUSPENDED).verifyEmail(LATER);

            assertThat(verified.isEmailVerified()).isTrue();
            assertThat(verified.status()).isEqualTo(UserStatus.SUSPENDED);
            assertThat(verified.canAuthenticate()).isFalse();
        }
    }

    @Test
    @DisplayName("a successful login is stamped")
    void recordsLogin() {
        User user = registered().recordLogin(LATER);

        assertThat(user.lastLoginAt()).isEqualTo(LATER);
        assertThat(user.updatedAt()).isEqualTo(LATER);
        assertThat(user.tokenVersion()).isZero();
    }

    @Test
    @DisplayName("changing the password bumps tokenVersion, killing outstanding access tokens")
    void changingPasswordInvalidatesAccessTokens() {
        User user = registered().changePassword(PasswordHash.of("$2a$12$new"), LATER);

        assertThat(user.passwordHash().value()).isEqualTo("$2a$12$new");
        assertThat(user.tokenVersion()).isEqualTo(1L);
        assertThat(user.updatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("log out everywhere bumps tokenVersion without touching the password")
    void revokingSessionsInvalidatesAccessTokens() {
        User before = registered();
        User after = before.revokeAllSessions(LATER);

        assertThat(after.tokenVersion()).isEqualTo(1L);
        assertThat(after.passwordHash()).isEqualTo(before.passwordHash());
    }

    @Nested
    @DisplayName("updating the profile")
    class ProfileUpdate {

        @Test
        @DisplayName("changes only what was given")
        void appliesGivenFields() {
            User user = registered().updateProfile("An Nguyen", LocaleTag.EN_US, LATER);

            assertThat(user.displayName()).isEqualTo("An Nguyen");
            assertThat(user.locale()).isEqualTo(LocaleTag.EN_US);
            assertThat(user.updatedAt()).isEqualTo(LATER);
        }

        @Test
        @DisplayName("a null field means leave it alone")
        void nullMeansUnchanged() {
            User user = registered().updateProfile(null, LocaleTag.EN_US, LATER);

            assertThat(user.displayName()).isEqualTo(NAME);
            assertThat(user.locale()).isEqualTo(LocaleTag.EN_US);
        }

        @Test
        @DisplayName("an update that changes nothing returns the same instance, so nothing is audited")
        void noOpUpdateReturnsSameInstance() {
            User user = registered();

            assertThat(user.updateProfile(NAME, LocaleTag.VI_VN, LATER)).isSameAs(user);
            assertThat(user.updateProfile(null, null, LATER)).isSameAs(user);
        }
    }

    @Test
    @DisplayName("a negative tokenVersion is impossible")
    void rejectsNegativeTokenVersion() {
        assertThatThrownBy(
                        () ->
                                new User(
                                        UserId.of(UUID.randomUUID()),
                                        EmailAddress.of("an@example.com"),
                                        PasswordHash.of("$2a$12$hash"),
                                        NAME,
                                        null,
                                        LocaleTag.VI_VN,
                                        UserStatus.ACTIVE,
                                        null,
                                        null,
                                        -1L,
                                        NOW,
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Nested
    @DisplayName("suspension")
    class Suspension {

        @Test
        @DisplayName("bars sign-in and invalidates every outstanding access token")
        void suspends() {
            User active = withStatus(UserStatus.ACTIVE);

            User suspended = active.suspend(LATER);

            assertThat(suspended.status()).isEqualTo(UserStatus.SUSPENDED);
            assertThat(suspended.canAuthenticate()).isFalse();
            assertThat(suspended.tokenVersion()).isEqualTo(active.tokenVersion() + 1);
            assertThat(suspended.updatedAt()).isEqualTo(LATER);
        }

        @Test
        @DisplayName("an account that cannot sign in cannot be suspended again")
        void onlyFromASignInState() {
            assertThatThrownBy(() -> withStatus(UserStatus.SUSPENDED).suspend(LATER))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> withStatus(UserStatus.DEACTIVATED).suspend(LATER))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("reactivating an unverified account does not vouch for its address")
        void reactivatesToPendingWhenUnverified() {
            User reactivated = registered().suspend(LATER).reactivate(LATER);

            assertThat(reactivated.status()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        }

        @Test
        @DisplayName("reactivating a verified account restores ACTIVE, without reviving old tokens")
        void reactivatesToActiveWhenVerified() {
            User verified = registered().verifyEmail(NOW);
            User suspended = verified.suspend(LATER);

            User reactivated = suspended.reactivate(LATER);

            assertThat(reactivated.status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(reactivated.tokenVersion()).isEqualTo(suspended.tokenVersion());
        }

        @Test
        @DisplayName("only a suspended account can be reactivated")
        void reactivateOnlyFromSuspended() {
            assertThatThrownBy(() -> withStatus(UserStatus.ACTIVE).reactivate(LATER))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("I-U2: only ACTIVE and PENDING_VERIFICATION may authenticate")
    void statusGatesAuthentication() {
        assertThat(UserStatus.ACTIVE.canAuthenticate()).isTrue();
        assertThat(UserStatus.PENDING_VERIFICATION.canAuthenticate()).isTrue();
        assertThat(UserStatus.SUSPENDED.canAuthenticate()).isFalse();
        assertThat(UserStatus.DEACTIVATED.canAuthenticate()).isFalse();
    }

    @Test
    @DisplayName("I-U3: the hash never renders itself")
    void passwordHashIsNotPrintable() {
        assertThat(PasswordHash.of("$2a$12$hash")).hasToString("PasswordHash[redacted]");
        assertThat(registered().toString()).doesNotContain("$2a$12$hash");
    }

    @Test
    @DisplayName("locale tags round-trip, and an unknown one falls back rather than failing")
    void localeTagsRoundTrip() {
        assertThat(LocaleTag.fromTag("vi-VN")).contains(LocaleTag.VI_VN);
        assertThat(LocaleTag.fromTag("en-US")).contains(LocaleTag.EN_US);
        assertThat(LocaleTag.fromTag("fr-FR")).isEmpty();
        assertThat(LocaleTag.fromTagOrDefault("fr-FR")).isEqualTo(LocaleTag.VI_VN);
        assertThat(LocaleTag.EN_US.tag()).isEqualTo("en-US");
    }
}
