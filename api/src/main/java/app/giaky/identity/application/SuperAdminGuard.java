package app.giaky.identity.application;

import app.giaky.common.error.PermissionDeniedException;
import app.giaky.common.types.UserId;
import app.giaky.identity.domain.User;
import app.giaky.identity.domain.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one decision point for platform administration: is this caller a super admin?
 *
 * <p>Super admins sit outside the clan permission model of doc 04 on purpose. That model answers
 * "who may act inside this clan", and every grant in it is clan-scoped; managing the accounts and
 * the infrastructure of the whole platform is a different question with no clan to scope it to
 * (persona P5, "Platform Operator").
 *
 * <p><b>Who they are.</b> The addresses in {@code SUPER_ADMIN_EMAILS}, recognised on every call
 * rather than copied into a column. Revoking one is therefore a configuration change, with no row
 * left behind that nobody remembers to delete, and no endpoint that can mint another.
 *
 * <p><b>Three conditions, all required:</b>
 *
 * <ol>
 *   <li>the account's address is in the configured list;
 *   <li>the address is <i>verified</i>. Without this, whoever registered the address first would be
 *       the super admin. That is an account takeover of the whole platform, available to anyone
 *       who guesses the operator's address before the operator signs up;
 *   <li>the account can authenticate (I-U2). A suspended super admin is not one.
 * </ol>
 *
 * <p>Like {@code PermissionResolver}, this is meant to be the only place the question is asked.
 * {@code AuthorizationCoverageTest} accepts a call to {@link #require} as the guard on a mutating
 * service method, the same way it accepts {@code PermissionResolver.require}.
 */
@Service
public class SuperAdminGuard {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminGuard.class);

    private final UserRepository users;
    private final Set<String> superAdminEmails;

    public SuperAdminGuard(
            UserRepository users,
            @Value("${app.platform.super-admin-emails:}") List<String> configured) {
        this.users = users;
        this.superAdminEmails =
                configured.stream()
                        .map(email -> email.trim().toLowerCase(Locale.ROOT))
                        .filter(email -> !email.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
        if (superAdminEmails.isEmpty()) {
            log.info("No super admin configured (SUPER_ADMIN_EMAILS is empty)");
        } else {
            log.info("{} super admin address(es) configured", superAdminEmails.size());
        }
    }

    /** Whether the account is a super admin right now. Never throws. */
    @Transactional(readOnly = true)
    public boolean isSuperAdmin(UserId userId) {
        return users.findById(userId).map(this::isSuperAdmin).orElse(false);
    }

    /**
     * Refuses unless the caller is a super admin.
     *
     * @throws PermissionDeniedException {@code PERMISSION_DENIED}, with the same message whatever
     *     condition failed; saying which would tell a prober the operator's address.
     */
    @Transactional(readOnly = true)
    public void require(UserId caller) {
        if (!isSuperAdmin(caller)) {
            throw new PermissionDeniedException("This action requires a platform administrator.");
        }
    }

    /** The decision for an account already loaded, so a list can mark its rows without N reads. */
    boolean isSuperAdmin(User user) {
        return user.isEmailVerified()
                && user.canAuthenticate()
                && superAdminEmails.contains(user.email().value());
    }

    /**
     * Whether the address is a configured super admin, whatever the state of its account.
     *
     * <p>Used to protect such accounts from suspension. A super admin who could suspend another
     * would turn a disagreement into a lockout that only a redeploy can undo.
     */
    boolean isConfiguredSuperAdmin(User user) {
        return superAdminEmails.contains(user.email().value());
    }
}
