package app.giaky.identity.application;

import app.giaky.common.audit.AuditAction;
import app.giaky.common.audit.AuditRecord;
import app.giaky.common.audit.AuditWriter;
import app.giaky.common.error.AuthenticationRequiredException;
import app.giaky.common.error.ConflictException;
import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.FieldViolation;
import app.giaky.common.error.TooManyRequestsException;
import app.giaky.common.security.AccessToken;
import app.giaky.common.security.JwtService;
import app.giaky.common.types.IdGenerator;
import app.giaky.common.types.UserId;
import app.giaky.common.web.RateLimitPolicy;
import app.giaky.common.web.RateLimiter;
import app.giaky.identity.domain.EmailAddress;
import app.giaky.identity.domain.LocaleTag;
import app.giaky.identity.domain.PasswordHash;
import app.giaky.identity.domain.PasswordHasher;
import app.giaky.identity.domain.PasswordPolicy;
import app.giaky.identity.domain.RefreshToken;
import app.giaky.identity.domain.TokenPurpose;
import app.giaky.identity.domain.User;
import app.giaky.identity.domain.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, sign-in, session lifecycle and password recovery (FR-A1, A3, A5, A6).
 *
 * <h2>What this class is careful about</h2>
 *
 * <ul>
 *   <li><b>Never confirming whether an address has an account.</b> Login answers the same way for a
 *       wrong password, an unknown address and a suspended account, and takes about the same time
 *       doing it. Forgot-password answers {@code 202} whatever it is given. The one place that does
 *       admit existence is registration, which returns {@code 409} on a duplicate — doc 05 §4.1
 *       specifies it, and the alternative is an account nobody can create and nobody can explain.
 *   <li><b>Invalidating sessions properly.</b> A password reset and "log out everywhere" both revoke
 *       the refresh tokens <i>and</i> bump {@code tokenVersion}, so outstanding access tokens die
 *       with them instead of working for another fifteen minutes.
 *   <li><b>Auditing the failures, not just the successes.</b> The audit writer commits
 *       independently, so a rejected login still leaves a record after this transaction rolls back.
 * </ul>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** One message for every way a sign-in can fail. Never say which one it was. */
    private static final String LOGIN_REJECTED = "Email or password is incorrect.";

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final RefreshTokenService refreshTokenService;
    private final VerificationTokenService verificationTokenService;
    private final JwtService jwtService;
    private final AuditWriter auditWriter;
    private final RateLimiter rateLimiter;
    private final IdGenerator idGenerator;
    private final Clock clock;

    /**
     * A hash to compare against when no account was found.
     *
     * <p>Skipping the comparison would make a miss measurably faster than a wrong password, turning
     * response time into an account-existence oracle and undoing the identical error messages.
     * Computed once, lazily, because at cost 12 it is not cheap.
     */
    private volatile PasswordHash timingDecoy;

    public AuthService(
            UserRepository users,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            RefreshTokenService refreshTokenService,
            VerificationTokenService verificationTokenService,
            JwtService jwtService,
            AuditWriter auditWriter,
            RateLimiter rateLimiter,
            IdGenerator idGenerator,
            Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.refreshTokenService = refreshTokenService;
        this.verificationTokenService = verificationTokenService;
        this.jwtService = jwtService;
        this.auditWriter = auditWriter;
        this.rateLimiter = rateLimiter;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    // --- registration ---------------------------------------------------------------------------

    /**
     * Creates an account and sends it a verification token (FR-A1).
     *
     * <p>The account can sign in immediately, in {@link app.giaky.identity.domain.UserStatus
     * #PENDING_VERIFICATION}. That is the Phase 1 decision (phase doc §8): blocking login until the
     * address is confirmed strands anyone whose message goes astray, so the restriction lands where
     * it protects something — creating a clan, from Phase 2.
     *
     * @throws DomainRuleViolation the password does not satisfy the policy
     * @throws ConflictException the address already has an account
     */
    @Transactional
    public Registration register(RegisterCommand command) {
        requireAcceptablePassword(command.password());

        EmailAddress email = EmailAddress.of(command.email());
        if (users.existsByEmail(email)) {
            // Doc 05 §4.1. This does confirm the address is taken; see the class comment.
            throw new ConflictException(
                    ErrorCode.EMAIL_ALREADY_REGISTERED,
                    "An account already exists for this email address.");
        }

        User user =
                users.save(
                        User.register(
                                UserId.of(idGenerator.newId()),
                                email,
                                passwordHasher.hash(command.password()),
                                command.displayName(),
                                command.locale() == null ? LocaleTag.DEFAULT : command.locale(),
                                clock.instant()));

        verificationTokenService.issue(user.id(), email, TokenPurpose.EMAIL_VERIFY);
        auditWriter.write(
                AuditRecord.ofUser(AuditAction.USER_REGISTERED, user.id())
                        .withIpAddress(command.client().ipAddress()));

        return new Registration(user.id(), user.status());
    }

    // --- sign in --------------------------------------------------------------------------------

    /**
     * Verifies credentials and opens a session (FR-A3).
     *
     * @throws TooManyRequestsException too many recent attempts against this address (doc 05 §5).
     *     Applied per account here rather than in the rate-limit filter, which cannot see the
     *     request body; the per-address limit is the filter's.
     * @throws AuthenticationRequiredException any failure at all, with one message
     */
    @Transactional
    public SessionTokens login(LoginCommand command) {
        String subject = normalizeForRateLimit(command.email());
        RateLimiter.Decision quota =
                rateLimiter.tryConsume(RateLimitPolicy.LOGIN_PER_ACCOUNT, subject);
        if (!quota.allowed()) {
            throw new TooManyRequestsException(
                    "Too many sign-in attempts for this account. Wait a moment and try again.",
                    quota.retryAfter());
        }

        Optional<User> found = findByRawEmail(command.email());
        if (found.isEmpty()) {
            // Spend roughly the time a real verification costs before answering (see timingDecoy).
            passwordHasher.matches(command.password(), timingDecoy());
            throw rejectedLogin(null, command);
        }

        User user = found.get();
        if (!passwordHasher.matches(command.password(), user.passwordHash())) {
            throw rejectedLogin(user.id(), command);
        }
        if (!user.canAuthenticate()) {
            // I-U2. Same message: "your account is suspended" is a useful hint to someone probing.
            throw rejectedLogin(user.id(), command);
        }

        User signedIn = users.save(user.recordLogin(clock.instant()));
        auditWriter.write(
                AuditRecord.ofUser(AuditAction.USER_LOGIN, signedIn.id())
                        .withIpAddress(command.client().ipAddress()));

        return issueSession(signedIn, command.client());
    }

    /**
     * Exchanges a refresh token for a new pair (FR-A3).
     *
     * <p>Re-reads the account rather than trusting the token: a session opened before a suspension
     * must not survive it.
     *
     * <p>A refusal commits what was written before it. {@code RefreshTokenService.rotate} revokes a
     * replayed token's whole chain and then throws; this is the outermost transaction that call
     * joins, so the rule has to be stated here as well or the revocation is rolled back with the
     * rejection. It also means a suspended account's presented token stays rotated — spent — which
     * is the outcome the sentence above asks for.
     */
    @Transactional(noRollbackFor = AuthenticationRequiredException.class)
    public SessionTokens refresh(String rawRefreshToken, ClientContext client) {
        RefreshTokenService.Issued rotated = refreshTokenService.rotate(rawRefreshToken, client);

        User user =
                users.findById(rotated.stored().userId())
                        .filter(User::canAuthenticate)
                        .orElseThrow(
                                () ->
                                        new AuthenticationRequiredException(
                                                ErrorCode.TOKEN_INVALID,
                                                "The refresh token is not valid. Sign in again."));

        AccessToken accessToken =
                jwtService.issue(user.id(), user.email().value(), user.tokenVersion());
        return new SessionTokens(
                accessToken.value(), rotated.rawToken(), accessToken.ttl(), UserProfile.of(user));
    }

    /** Ends one session (FR-A5). Succeeds whatever it is given; see {@code RefreshTokenService}. */
    @Transactional
    public void logout(String rawRefreshToken, UserId caller) {
        refreshTokenService.revoke(rawRefreshToken);
        if (caller != null) {
            auditWriter.write(AuditRecord.ofUser(AuditAction.USER_LOGGED_OUT, caller));
        }
    }

    /**
     * Ends every session for an account (FR-A5).
     *
     * <p>Both halves: the refresh tokens are revoked <i>and</i> {@code tokenVersion} is bumped, so
     * the access token in the other browser stops working on its next request rather than at the
     * end of its fifteen minutes. That is the whole reason the column exists (phase doc §9).
     */
    @Transactional
    public void logoutAll(UserId userId) {
        User user =
                users.findById(userId)
                        .orElseThrow(
                                () ->
                                        new AuthenticationRequiredException(
                                                ErrorCode.AUTH_REQUIRED, "Not signed in."));

        users.save(user.revokeAllSessions(clock.instant()));
        int ended = refreshTokenService.revokeAll(userId, RefreshToken.RevocationReason.LOGOUT_ALL);
        auditWriter.write(AuditRecord.ofUser(AuditAction.ALL_SESSIONS_REVOKED, userId));
        log.info("Revoked {} session(s) for user {}", ended, userId);
    }

    // --- verification and recovery ----------------------------------------------------------------

    /** Confirms an address (FR-A6). Idempotent, so a link opened twice is not an error. */
    @Transactional
    public void verifyEmail(String rawToken) {
        UserId userId = verificationTokenService.consume(rawToken, TokenPurpose.EMAIL_VERIFY);
        users.findById(userId)
                .ifPresent(
                        user -> {
                            users.save(user.verifyEmail(clock.instant()));
                            auditWriter.write(AuditRecord.ofUser(AuditAction.EMAIL_VERIFIED, userId));
                        });
    }

    /**
     * Starts a password reset (FR-A6).
     *
     * <p>Returns normally whatever it is given — an unknown address, an address that is not an
     * address, an account that is suspended. The endpoint answers {@code 202} in every case, because
     * anything else turns it into a free account-enumeration oracle for an unauthenticated caller.
     */
    @Transactional
    public void forgotPassword(String rawEmail, ClientContext client) {
        Optional<User> found = findByRawEmail(rawEmail);
        if (found.isEmpty()) {
            log.debug("Password reset requested for an address with no account");
            return;
        }
        User user = found.get();
        verificationTokenService.issue(user.id(), user.email(), TokenPurpose.PASSWORD_RESET);
        auditWriter.write(
                AuditRecord.ofUser(AuditAction.PASSWORD_RESET_REQUESTED, user.id())
                        .withIpAddress(client.ipAddress()));
    }

    /**
     * Sets a new password and ends every session (FR-A6).
     *
     * <p>Ending the sessions is the part that matters. Someone resetting because their account was
     * taken over needs the intruder signed out, and a reset that left the intruder's session alive
     * would achieve nothing at all.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        requireAcceptablePassword(newPassword);

        UserId userId = verificationTokenService.consume(rawToken, TokenPurpose.PASSWORD_RESET);
        User user =
                users.findById(userId)
                        .orElseThrow(
                                () ->
                                        new AuthenticationRequiredException(
                                                ErrorCode.TOKEN_INVALID,
                                                "This link is not valid. Request a new one."));

        users.save(user.changePassword(passwordHasher.hash(newPassword), clock.instant()));
        refreshTokenService.revokeAll(userId, RefreshToken.RevocationReason.PASSWORD_RESET);
        auditWriter.write(AuditRecord.ofUser(AuditAction.PASSWORD_RESET, userId));
    }

    // --- helpers -----------------------------------------------------------------------------------

    private SessionTokens issueSession(User user, ClientContext client) {
        AccessToken accessToken =
                jwtService.issue(user.id(), user.email().value(), user.tokenVersion());
        RefreshTokenService.Issued refresh = refreshTokenService.open(user.id(), client);
        return new SessionTokens(
                accessToken.value(), refresh.rawToken(), accessToken.ttl(), UserProfile.of(user));
    }

    /**
     * Records the failed attempt and builds the one rejection every failure gets.
     *
     * <p>Returns the exception for the caller to throw rather than throwing itself, so each call site
     * reads {@code throw rejectedLogin(...)} and the compiler can see the path ends there.
     *
     * @param subject the account the attempt was against, or null when the address matched none
     */
    private AuthenticationRequiredException rejectedLogin(UserId subject, LoginCommand command) {
        AuditRecord entry =
                subject == null
                        ? AuditRecord.anonymous(AuditAction.LOGIN_FAILED)
                        : AuditRecord.ofUser(AuditAction.LOGIN_FAILED, subject);
        auditWriter.write(entry.withIpAddress(command.client().ipAddress()));
        return new AuthenticationRequiredException(ErrorCode.AUTH_REQUIRED, LOGIN_REJECTED);
    }

    /** Looks up an account from unvalidated input, treating "not an address" as "no account". */
    private Optional<User> findByRawEmail(String rawEmail) {
        if (rawEmail == null || !EmailAddress.isValid(rawEmail)) {
            return Optional.empty();
        }
        return users.findByEmail(EmailAddress.of(rawEmail));
    }

    private void requireAcceptablePassword(String password) {
        List<String> violations = passwordPolicy.validate(password);
        if (!violations.isEmpty()) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED,
                    "The password does not meet the requirements.",
                    violations.stream().map(message -> new FieldViolation("password", message)).toList());
        }
    }

    /** Rate-limit key: the address as typed, folded, so case and padding cannot buy extra attempts. */
    private static String normalizeForRateLimit(String rawEmail) {
        return rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    private PasswordHash timingDecoy() {
        PasswordHash decoy = timingDecoy;
        if (decoy == null) {
            synchronized (this) {
                decoy = timingDecoy;
                if (decoy == null) {
                    decoy = passwordHasher.hash("timing-decoy-" + idGenerator.newId());
                    timingDecoy = decoy;
                }
            }
        }
        return decoy;
    }
}
