package app.giaky.identity.application;

import app.giaky.common.audit.AuditAction;
import app.giaky.common.audit.AuditRecord;
import app.giaky.common.audit.AuditWriter;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.types.UserId;
import app.giaky.identity.domain.User;
import app.giaky.identity.domain.UserRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The signed-in user's own account: read it, change it, see its sessions (FR-A4).
 *
 * <p>Every method takes the caller's own {@link UserId} and operates on that account only. There is
 * no "get user by id" here and there should not be: nothing in this product needs to read another
 * person's account, and an endpoint that could would need an authorization story that Phase 3 has
 * not written yet.
 */
@Service
public class AccountService {

    private final UserRepository users;
    private final RefreshTokenService refreshTokenService;
    private final AuditWriter auditWriter;
    private final Clock clock;

    public AccountService(
            UserRepository users,
            RefreshTokenService refreshTokenService,
            AuditWriter auditWriter,
            Clock clock) {
        this.users = users;
        this.refreshTokenService = refreshTokenService;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserProfile profile(UserId userId) {
        return UserProfile.of(require(userId));
    }

    /**
     * Updates display name and locale (FR-A4).
     *
     * <p>Returns the stored profile rather than nothing, so the client renders what the server
     * actually holds instead of what it hoped it sent.
     */
    @Transactional
    public UserProfile updateProfile(UserId userId, UpdateProfileCommand command) {
        User existing = require(userId);
        User updated =
                users.save(
                        existing.updateProfile(
                                command.displayName(), command.locale(), clock.instant()));

        if (updated != existing) {
            auditWriter.write(
                    AuditRecord.ofUser(AuditAction.PROFILE_UPDATED, userId)
                            .withChange(describe(existing), describe(updated)));
        }
        return UserProfile.of(updated);
    }

    /** The account's live sessions (task F1.6). */
    @Transactional(readOnly = true)
    public List<SessionInfo> sessions(UserId userId) {
        return refreshTokenService.activeSessions(userId);
    }

    private User require(UserId userId) {
        return users.findById(userId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        ErrorCode.RESOURCE_NOT_FOUND, "No such account."));
    }

    /**
     * The audited fields, as JSON.
     *
     * <p>Hand-built rather than serialized: an object mapper here would happily follow whatever
     * fields {@code User} grows later, and the first one that is sensitive would end up in an
     * append-only table nobody can edit. Naming the two fields keeps the trail deliberate.
     */
    private static String describe(User user) {
        return "{\"displayName\":\""
                + user.displayName().replace("\\", "\\\\").replace("\"", "\\\"")
                + "\",\"locale\":\""
                + user.locale().tag()
                + "\"}";
    }
}
