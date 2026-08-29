package app.giaky.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.giaky.common.audit.AuditAction;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.types.UserId;
import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.LocaleTag;
import app.giaky.identity.domain.PasswordHash;
import app.giaky.identity.domain.User;
import app.giaky.identity.support.IdentityTestFixtures;
import app.giaky.identity.support.InMemoryRefreshTokenRepository;
import app.giaky.identity.support.InMemoryUserRepository;
import app.giaky.identity.support.RecordingAuditWriter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Profile read and update (FR-A4), and the session list behind the profile page. */
class AccountServiceTest {

    private static final ClientContext CLIENT = new ClientContext("203.0.113.7", "Firefox");

    private InMemoryUserRepository users;
    private InMemoryRefreshTokenRepository refreshTokens;
    private RecordingAuditWriter audit;
    private AccountService accountService;
    private RefreshTokenService refreshTokenService;
    private User an;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        refreshTokens = new InMemoryRefreshTokenRepository();
        audit = new RecordingAuditWriter();
        var clock = IdentityTestFixtures.fixedClock();
        refreshTokenService =
                new RefreshTokenService(
                        refreshTokens,
                        audit,
                        IdentityTestFixtures.idGenerator(),
                        clock,
                        IdentityTestFixtures.securityProperties());
        accountService = new AccountService(users, refreshTokenService, audit, event -> {}, clock);

        an =
                users.save(
                        User.register(
                                UserId.of(UUID.randomUUID()),
                                EmailAddress.of("an@example.com"),
                                PasswordHash.of("$2a$12$hash"),
                                "An Nguyen",
                                LocaleTag.VI_VN,
                                IdentityTestFixtures.NOW));
    }

    @Test
    @DisplayName("reads the profile without exposing anything credential-shaped")
    void readsProfile() {
        UserProfile profile = accountService.profile(an.id());

        assertThat(profile.email()).isEqualTo("an@example.com");
        assertThat(profile.displayName()).isEqualTo("An Nguyen");
        assertThat(profile.locale()).isEqualTo(LocaleTag.VI_VN);
        assertThat(profile.emailVerified()).isFalse();
    }

    @Test
    @DisplayName("an account that does not exist is a 404, not a null profile")
    void missingAccountIsNotFound() {
        assertThatThrownBy(() -> accountService.profile(UserId.of(UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("updates the given fields and returns what was stored")
    void updatesProfile() {
        UserProfile updated =
                accountService.updateProfile(
                        an.id(), new UpdateProfileCommand("An Nguyen Van", LocaleTag.EN_US));

        assertThat(updated.displayName()).isEqualTo("An Nguyen Van");
        assertThat(updated.locale()).isEqualTo(LocaleTag.EN_US);
        assertThat(users.findById(an.id()).orElseThrow().displayName()).isEqualTo("An Nguyen Van");
    }

    @Test
    @DisplayName("an absent field is left alone")
    void partialUpdateLeavesOtherFields() {
        accountService.updateProfile(an.id(), new UpdateProfileCommand(null, LocaleTag.EN_US));

        User stored = users.findById(an.id()).orElseThrow();
        assertThat(stored.displayName()).isEqualTo("An Nguyen");
        assertThat(stored.locale()).isEqualTo(LocaleTag.EN_US);
    }

    @Test
    @DisplayName("a real change is audited with before and after")
    void auditsRealChanges() {
        accountService.updateProfile(an.id(), new UpdateProfileCommand("An Nguyen Van", null));

        assertThat(audit.recorded(AuditAction.PROFILE_UPDATED)).isTrue();
        assertThat(audit.written().getFirst().beforeJson()).contains("An Nguyen");
        assertThat(audit.written().getFirst().afterJson()).contains("An Nguyen Van");
    }

    @Test
    @DisplayName("an update that changes nothing writes no audit entry")
    void noOpUpdateIsNotAudited() {
        accountService.updateProfile(an.id(), new UpdateProfileCommand("An Nguyen", LocaleTag.VI_VN));

        assertThat(audit.written()).isEmpty();
    }

    @Test
    @DisplayName("a display name with a quote does not break the audit JSON")
    void audJsonEscapesQuotes() {
        accountService.updateProfile(an.id(), new UpdateProfileCommand("An \"Bo\" Nguyen", null));

        assertThat(audit.written().getFirst().afterJson()).contains("An \\\"Bo\\\" Nguyen");
    }

    @Test
    @DisplayName("lists the account's live sessions")
    void listsSessions() {
        refreshTokenService.open(an.id(), CLIENT);
        refreshTokenService.open(UserId.of(UUID.randomUUID()), CLIENT);

        assertThat(accountService.sessions(an.id())).hasSize(1);
        assertThat(accountService.sessions(an.id()).getFirst().userAgent()).isEqualTo("Firefox");
    }
}
