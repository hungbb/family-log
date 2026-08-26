package app.giaky.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.common.error.AuthenticationRequiredException;
import app.giaky.common.types.UserId;
import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.TokenPurpose;
import app.giaky.identity.domain.VerificationToken;
import app.giaky.identity.support.IdentityTestFixtures;
import app.giaky.identity.support.InMemoryVerificationTokenRepository;
import app.giaky.identity.support.RecordingTokenDelivery;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Single-use tokens with a purpose and an expiry (FR-A6, task B1.9). */
class VerificationTokenServiceTest {

    private static final UserId USER = UserId.of(UUID.randomUUID());
    private static final EmailAddress EMAIL = EmailAddress.of("an@example.com");

    private InMemoryVerificationTokenRepository repository;
    private RecordingTokenDelivery delivery;
    private VerificationTokenService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryVerificationTokenRepository();
        delivery = new RecordingTokenDelivery();
        service = serviceAt(IdentityTestFixtures.fixedClock());
    }

    private VerificationTokenService serviceAt(Clock clock) {
        return new VerificationTokenService(
                repository,
                delivery,
                IdentityTestFixtures.idGenerator(),
                clock,
                IdentityTestFixtures.securityProperties());
    }

    @Test
    @DisplayName("issuing stores the hash and delivers the token itself")
    void issuesAndDelivers() {
        service.issue(USER, EMAIL, TokenPurpose.EMAIL_VERIFY);

        String raw = delivery.latest(TokenPurpose.EMAIL_VERIFY).orElseThrow();
        assertThat(repository.all()).singleElement();
        assertThat(repository.all().getFirst().tokenHash())
                .isEqualTo(OpaqueTokens.hash(raw))
                .isNotEqualTo(raw);
        assertThat(delivery.all().getFirst().recipient()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("each purpose gets its own lifetime — 24 hours to verify, one hour to reset")
    void lifetimesFollowThePurpose() {
        service.issue(USER, EMAIL, TokenPurpose.EMAIL_VERIFY);
        service.issue(USER, EMAIL, TokenPurpose.PASSWORD_RESET);

        assertThat(delivery.all().get(0).expiresAt())
                .isEqualTo(IdentityTestFixtures.NOW.plus(Duration.ofHours(24)));
        assertThat(delivery.all().get(1).expiresAt())
                .isEqualTo(IdentityTestFixtures.NOW.plus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("issuing a second reset token invalidates the first")
    void issuingRetiresOutstandingTokensOfThatPurpose() {
        service.issue(USER, EMAIL, TokenPurpose.PASSWORD_RESET);
        String first = delivery.latest(TokenPurpose.PASSWORD_RESET).orElseThrow();

        service.issue(USER, EMAIL, TokenPurpose.PASSWORD_RESET);

        // Otherwise three "forgot password" clicks would leave three live takeover credentials in
        // the same inbox, and the oldest is the one a historical mailbox breach already has.
        assertThatThrownBy(() -> service.consume(first, TokenPurpose.PASSWORD_RESET))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    @DisplayName("issuing a reset token leaves an outstanding verification token alone")
    void purposesAreRetiredIndependently() {
        service.issue(USER, EMAIL, TokenPurpose.EMAIL_VERIFY);
        String verify = delivery.latest(TokenPurpose.EMAIL_VERIFY).orElseThrow();

        service.issue(USER, EMAIL, TokenPurpose.PASSWORD_RESET);

        assertThat(service.consume(verify, TokenPurpose.EMAIL_VERIFY)).isEqualTo(USER);
    }

    @Test
    @DisplayName("consuming returns the account and spends the token")
    void consumingSpendsTheToken() {
        service.issue(USER, EMAIL, TokenPurpose.EMAIL_VERIFY);
        String raw = delivery.latest(TokenPurpose.EMAIL_VERIFY).orElseThrow();

        assertThat(service.consume(raw, TokenPurpose.EMAIL_VERIFY)).isEqualTo(USER);

        assertThat(repository.all()).allMatch(VerificationToken::isConsumed);
        assertThatThrownBy(() -> service.consume(raw, TokenPurpose.EMAIL_VERIFY))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    @DisplayName("a verification token cannot be spent as a password reset")
    void purposeIsChecked() {
        service.issue(USER, EMAIL, TokenPurpose.EMAIL_VERIFY);
        String raw = delivery.latest(TokenPurpose.EMAIL_VERIFY).orElseThrow();

        // Otherwise a forwarded welcome email would be an account takeover.
        assertThatThrownBy(() -> service.consume(raw, TokenPurpose.PASSWORD_RESET))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    @DisplayName("an expired token is rejected")
    void expiredTokenIsRejected() {
        service.issue(USER, EMAIL, TokenPurpose.PASSWORD_RESET);
        String raw = delivery.latest(TokenPurpose.PASSWORD_RESET).orElseThrow();
        VerificationTokenService afterExpiry =
                serviceAt(IdentityTestFixtures.clockAt(Duration.ofHours(2)));

        assertThatThrownBy(() -> afterExpiry.consume(raw, TokenPurpose.PASSWORD_RESET))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    @DisplayName("an unknown token is rejected")
    void unknownTokenIsRejected() {
        assertThatThrownBy(() -> service.consume("never-issued", TokenPurpose.EMAIL_VERIFY))
                .isInstanceOf(AuthenticationRequiredException.class);
    }
}
