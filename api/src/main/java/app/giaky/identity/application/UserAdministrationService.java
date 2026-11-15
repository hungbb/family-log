package app.giaky.identity.application;

import app.giaky.common.audit.AuditAction;
import app.giaky.common.audit.AuditRecord;
import app.giaky.common.audit.AuditWriter;
import app.giaky.common.error.ConflictException;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.error.PermissionDeniedException;
import app.giaky.common.types.Paged;
import app.giaky.common.types.UserId;
import app.giaky.identity.domain.RefreshToken;
import app.giaky.identity.domain.TokenPurpose;
import app.giaky.identity.domain.User;
import app.giaky.identity.domain.UserRepository;
import app.giaky.identity.domain.UserStatus;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a super admin may do to other people's accounts.
 *
 * <p>Every method's first statement is {@link SuperAdminGuard#require}, and {@code
 * AuthorizationCoverageTest} holds the mutating ones to it even though the rest of {@code
 * identity.application} is exempt from that rule. The rest of Identity acts on the caller's own
 * account; this class is the one place it acts on somebody else's.
 *
 * <p>Every action is audited with the super admin as actor and the account as target. Nothing here
 * reveals or sets a password: a forgotten password is fixed by sending the holder a reset link,
 * so the new password is never known to anyone but them.
 */
@Service
public class UserAdministrationService {

    private static final Logger log = LoggerFactory.getLogger(UserAdministrationService.class);

    /** Upper bound on one page, so a hand-edited query cannot ask for the whole table. */
    public static final int MAX_PAGE_SIZE = 100;

    private final SuperAdminGuard guard;
    private final UserRepository users;
    private final RefreshTokenService refreshTokenService;
    private final VerificationTokenService verificationTokenService;
    private final AuditWriter auditWriter;
    private final Clock clock;

    public UserAdministrationService(
            SuperAdminGuard guard,
            UserRepository users,
            RefreshTokenService refreshTokenService,
            VerificationTokenService verificationTokenService,
            AuditWriter auditWriter,
            Clock clock) {
        this.guard = guard;
        this.users = users;
        this.refreshTokenService = refreshTokenService;
        this.verificationTokenService = verificationTokenService;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    /**
     * Every account on the platform, newest first.
     *
     * @param query matched against display name and address; null or blank for all
     * @param status only accounts in this state; null for all
     */
    @Transactional(readOnly = true)
    public Paged<AdminUserView> list(
            UserId caller, String query, UserStatus status, int page, int size) {
        guard.require(caller);
        int boundedSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Paged<User> found = users.searchAll(query, status, Math.max(page, 0), boundedSize);
        return new Paged<>(
                found.content().stream()
                        .map(user -> AdminUserView.of(user, guard.isSuperAdmin(user)))
                        .toList(),
                found.page(),
                found.size(),
                found.totalElements());
    }

    /**
     * Bars an account and ends every session it holds (I-U2).
     *
     * <p>Both halves of "ends": the refresh tokens are revoked and {@code tokenVersion} is bumped,
     * so the account's open browser tab stops working on its next request.
     *
     * @throws PermissionDeniedException {@code SUPER_ADMIN_PROTECTED} when the target is the caller
     *     or another configured super admin
     * @throws ConflictException {@code USER_STATUS_CONFLICT} when the account is already suspended
     *     or closed
     */
    @Transactional
    public AdminUserView suspend(UserId caller, UserId target) {
        guard.require(caller);
        User user = load(target);
        if (caller.equals(target) || guard.isConfiguredSuperAdmin(user)) {
            throw new PermissionDeniedException(
                    ErrorCode.SUPER_ADMIN_PROTECTED,
                    "A super admin cannot be suspended. Remove the address from SUPER_ADMIN_EMAILS"
                            + " first.");
        }
        if (!user.canAuthenticate()) {
            throw statusConflict("This account is already suspended or closed.");
        }

        User suspended = users.save(user.suspend(clock.instant()));
        int ended = refreshTokenService.revokeAll(target, RefreshToken.RevocationReason.SUSPENDED);
        auditWriter.write(AuditRecord.byOperator(AuditAction.USER_SUSPENDED, caller, target));
        log.info("User {} suspended by {}; {} session(s) ended", target, caller, ended);
        return AdminUserView.of(suspended, false);
    }

    /**
     * Lifts a suspension.
     *
     * @throws ConflictException {@code USER_STATUS_CONFLICT} when the account is not suspended
     */
    @Transactional
    public AdminUserView reactivate(UserId caller, UserId target) {
        guard.require(caller);
        User user = load(target);
        if (user.status() != UserStatus.SUSPENDED) {
            throw statusConflict("Only a suspended account can be reactivated.");
        }

        User reactivated = users.save(user.reactivate(clock.instant()));
        auditWriter.write(AuditRecord.byOperator(AuditAction.USER_REACTIVATED, caller, target));
        return AdminUserView.of(reactivated, guard.isSuperAdmin(reactivated));
    }

    /**
     * Confirms an address without a verification link: for the relative whose mail never arrives.
     *
     * <p>Idempotent, like the link: an address already verified is returned unchanged and nothing
     * is audited, because nothing happened.
     */
    @Transactional
    public AdminUserView markEmailVerified(UserId caller, UserId target) {
        guard.require(caller);
        User user = load(target);
        if (user.isEmailVerified()) {
            return AdminUserView.of(user, guard.isSuperAdmin(user));
        }

        User verified = users.save(user.verifyEmail(clock.instant()));
        auditWriter.write(
                AuditRecord.byOperator(AuditAction.EMAIL_VERIFIED_BY_ADMIN, caller, target));
        return AdminUserView.of(verified, guard.isSuperAdmin(verified));
    }

    /**
     * Sends a fresh verification link, invalidating any earlier one.
     *
     * @throws ConflictException {@code USER_STATUS_CONFLICT} when the address is already verified
     */
    @Transactional
    public void resendVerification(UserId caller, UserId target) {
        guard.require(caller);
        User user = load(target);
        if (user.isEmailVerified()) {
            throw statusConflict("This address is already verified.");
        }

        verificationTokenService.issue(user.id(), user.email(), TokenPurpose.EMAIL_VERIFY);
        auditWriter.write(
                AuditRecord.byOperator(AuditAction.VERIFICATION_RESENT_BY_ADMIN, caller, target));
    }

    /**
     * Sends the holder a password-reset link.
     *
     * <p>The super admin never learns the new password: the link goes to the account's own inbox
     * and the holder chooses it.
     *
     * @throws ConflictException {@code USER_STATUS_CONFLICT} when the account cannot sign in, since
     *     a new password would not let it
     */
    @Transactional
    public void sendPasswordReset(UserId caller, UserId target) {
        guard.require(caller);
        User user = load(target);
        if (!user.canAuthenticate()) {
            throw statusConflict(
                    "This account is suspended or closed; a new password would not let it sign in.");
        }

        verificationTokenService.issue(user.id(), user.email(), TokenPurpose.PASSWORD_RESET);
        auditWriter.write(
                AuditRecord.byOperator(AuditAction.PASSWORD_RESET_SENT_BY_ADMIN, caller, target));
    }

    private User load(UserId target) {
        return users.findById(target)
                .orElseThrow(
                        () -> new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "No such account."));
    }

    private static ConflictException statusConflict(String detail) {
        return new ConflictException(ErrorCode.USER_STATUS_CONFLICT, detail);
    }
}
