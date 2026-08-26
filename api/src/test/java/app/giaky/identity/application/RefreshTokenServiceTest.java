package app.giaky.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.common.audit.AuditAction;
import app.giaky.common.error.AuthenticationRequiredException;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.types.UserId;
import app.giaky.identity.domain.RefreshToken;
import app.giaky.identity.domain.RefreshToken.RevocationReason;
import app.giaky.identity.support.IdentityTestFixtures;
import app.giaky.identity.support.InMemoryRefreshTokenRepository;
import app.giaky.identity.support.RecordingAuditWriter;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Rotation and reuse detection (task B1.4) — the exit criterion that says this must be proven by
 * test.
 */
class RefreshTokenServiceTest {

    private static final ClientContext CLIENT = new ClientContext("203.0.113.7", "Firefox");
    private static final UserId USER = UserId.of(UUID.randomUUID());

    private InMemoryRefreshTokenRepository repository;
    private RecordingAuditWriter audit;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRefreshTokenRepository();
        audit = new RecordingAuditWriter();
        service = serviceAt(IdentityTestFixtures.fixedClock());
    }

    private RefreshTokenService serviceAt(Clock clock) {
        return new RefreshTokenService(
                repository,
                audit,
                IdentityTestFixtures.idGenerator(),
                clock,
                IdentityTestFixtures.securityProperties());
    }

    @Test
    @DisplayName("opening a session stores only the hash, never the token")
    void opensASessionWithoutStoringTheToken() {
        RefreshTokenService.Issued issued = service.open(USER, CLIENT);

        assertThat(issued.rawToken()).isNotBlank();
        assertThat(repository.all()).singleElement();
        assertThat(repository.all().getFirst().tokenHash())
                .isEqualTo(OpaqueTokens.hash(issued.rawToken()))
                .isNotEqualTo(issued.rawToken());
        assertThat(repository.all().getFirst().userAgent()).isEqualTo("Firefox");
        assertThat(repository.all().getFirst().ipAddress()).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("the session expires after the configured lifetime")
    void sessionCarriesTheConfiguredLifetime() {
        RefreshTokenService.Issued issued = service.open(USER, CLIENT);

        assertThat(issued.stored().expiresAt())
                .isEqualTo(IdentityTestFixtures.NOW.plus(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("rotating spends the presented token and returns a successor in the same chain")
    void rotationSpendsAndSucceeds() {
        RefreshTokenService.Issued first = service.open(USER, CLIENT);

        RefreshTokenService.Issued second = service.rotate(first.rawToken(), CLIENT);

        assertThat(second.rawToken()).isNotEqualTo(first.rawToken());
        assertThat(second.stored().chainId()).isEqualTo(first.stored().chainId());
        assertThat(revoked(first)).isTrue();
        assertThat(reasonFor(first)).isEqualTo(RevocationReason.ROTATED);
        assertThat(second.stored().isRevoked()).isFalse();
    }

    @Test
    @DisplayName("a token can be rotated exactly once")
    void aTokenIsSingleUse() {
        RefreshTokenService.Issued first = service.open(USER, CLIENT);
        service.rotate(first.rawToken(), CLIENT);

        assertThatThrownBy(() -> service.rotate(first.rawToken(), CLIENT))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    @DisplayName("reusing a rotated token revokes the entire chain")
    void reuseRevokesTheWholeChain() {
        RefreshTokenService.Issued first = service.open(USER, CLIENT);
        RefreshTokenService.Issued second = service.rotate(first.rawToken(), CLIENT);
        RefreshTokenService.Issued third = service.rotate(second.rawToken(), CLIENT);

        // The thief presents the token they captured two rotations ago.
        assertThatThrownBy(() -> service.rotate(first.rawToken(), CLIENT))
                .isInstanceOf(AuthenticationRequiredException.class);

        // The real client's live token dies with it: which of the two is the thief is unknowable,
        // so the session ends for both and everyone signs in again.
        assertThat(revoked(third)).isTrue();
        assertThat(reasonFor(third)).isEqualTo(RevocationReason.REUSE_DETECTED);
        assertThat(repository.findActiveByUser(USER, IdentityTestFixtures.NOW)).isEmpty();
        assertThat(audit.recorded(AuditAction.REFRESH_TOKEN_REUSE_DETECTED)).isTrue();
    }

    @Test
    @DisplayName("reuse detection does not touch a second, unrelated session")
    void reuseOnlyRevokesItsOwnChain() {
        RefreshTokenService.Issued phone = service.open(USER, CLIENT);
        RefreshTokenService.Issued laptop = service.open(USER, CLIENT);
        service.rotate(phone.rawToken(), CLIENT);

        assertThatThrownBy(() -> service.rotate(phone.rawToken(), CLIENT))
                .isInstanceOf(AuthenticationRequiredException.class);

        assertThat(revoked(laptop)).isFalse();
        assertThat(repository.findActiveByUser(USER, IdentityTestFixtures.NOW))
                .extracting(RefreshToken::chainId)
                .containsExactly(laptop.stored().chainId());
    }

    @Test
    @DisplayName("an unknown token is rejected as TOKEN_INVALID, saying nothing about why")
    void unknownTokenIsRejected() {
        assertThatThrownBy(() -> service.rotate("never-issued", CLIENT))
                .isInstanceOf(AuthenticationRequiredException.class)
                .satisfies(
                        thrown ->
                                assertThat(((AuthenticationRequiredException) thrown).code())
                                        .isEqualTo(ErrorCode.TOKEN_INVALID));
    }

    @Test
    @DisplayName("an expired token is rejected without killing the chain")
    void expiredTokenIsRejectedButNotTreatedAsReuse() {
        RefreshTokenService.Issued issued = service.open(USER, CLIENT);
        RefreshTokenService afterExpiry =
                serviceAt(IdentityTestFixtures.clockAt(Duration.ofDays(31)));

        assertThatThrownBy(() -> afterExpiry.rotate(issued.rawToken(), CLIENT))
                .isInstanceOf(AuthenticationRequiredException.class);

        assertThat(revoked(issued)).isFalse();
        assertThat(audit.recorded(AuditAction.REFRESH_TOKEN_REUSE_DETECTED)).isFalse();
    }

    @Test
    @DisplayName("signing out revokes just that session")
    void logoutRevokesOneSession() {
        RefreshTokenService.Issued phone = service.open(USER, CLIENT);
        RefreshTokenService.Issued laptop = service.open(USER, CLIENT);

        service.revoke(phone.rawToken());

        assertThat(reasonFor(phone)).isEqualTo(RevocationReason.LOGOUT);
        assertThat(revoked(laptop)).isFalse();
    }

    @Test
    @DisplayName("signing out with an unrecognised token still succeeds")
    void logoutIsSilentAboutUnknownTokens() {
        service.revoke("never-issued");

        assertThat(repository.all()).isEmpty();
    }

    @Test
    @DisplayName("logging out everywhere revokes every live session and reports the count")
    void revokeAllEndsEverySession() {
        service.open(USER, CLIENT);
        service.open(USER, CLIENT);
        service.open(UserId.of(UUID.randomUUID()), CLIENT);

        int ended = service.revokeAll(USER, RevocationReason.LOGOUT_ALL);

        assertThat(ended).isEqualTo(2);
        assertThat(repository.findActiveByUser(USER, IdentityTestFixtures.NOW)).isEmpty();
    }

    @Test
    @DisplayName("the session list shows live sessions only, newest first")
    void activeSessionsListsLiveSessionsNewestFirst() {
        RefreshTokenService.Issued first = service.open(USER, CLIENT);
        RefreshTokenService later = serviceAt(IdentityTestFixtures.clockAt(Duration.ofMinutes(5)));
        RefreshTokenService.Issued second = later.open(USER, new ClientContext("198.51.100.4", "Safari"));
        service.revoke(first.rawToken());

        List<SessionInfo> sessions = later.activeSessions(USER);

        assertThat(sessions).extracting(SessionInfo::id).containsExactly(second.stored().id());
        assertThat(sessions.getFirst().userAgent()).isEqualTo("Safari");
    }

    private boolean revoked(RefreshTokenService.Issued issued) {
        return repository.all().stream()
                .filter(token -> token.id().equals(issued.stored().id()))
                .findFirst()
                .orElseThrow()
                .isRevoked();
    }

    private RevocationReason reasonFor(RefreshTokenService.Issued issued) {
        return repository.all().stream()
                .filter(token -> token.id().equals(issued.stored().id()))
                .findFirst()
                .orElseThrow()
                .revokedReason();
    }
}
