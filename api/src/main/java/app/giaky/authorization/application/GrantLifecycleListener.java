package app.giaky.authorization.application;

import app.giaky.authorization.domain.Permission;
import app.giaky.authorization.domain.PermissionScope;
import app.giaky.clanorg.application.event.ClanCreated;
import app.giaky.clanorg.application.event.MembershipGranted;
import app.giaky.clanorg.application.event.MembershipRevoked;
import app.giaky.common.audit.AuditAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The three moments when membership and grants have to move together (doc 04 §6, B3.10–B3.12).
 *
 * <p>Listeners rather than three edits to {@code ClanService}, {@code JoinRequestService} and {@code
 * MembershipService}, because the events already existed: Phase 2 published all three with nothing
 * subscribed, precisely so that this phase could be an addition rather than a change to code that
 * was already tested (see the note on {@code ClanService.create}).
 *
 * <p><b>Plain {@code @EventListener}, not {@code @TransactionalEventListener}.</b> These run inside
 * the publisher's transaction, and that is the whole requirement. A clan whose founder's {@code
 * CLAN_ADMIN} was written after the commit is a clan that spends a moment with nobody able to
 * administer it, and if the second write fails it stays that way — with the clan's own creator
 * getting a 403 from it forever. All-or-nothing is the only correct answer here.
 */
@Component
public class GrantLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(GrantLifecycleListener.class);

    private final GrantService grants;

    public GrantLifecycleListener(GrantService grants) {
        this.grants = grants;
    }

    /**
     * The founder becomes the clan's first administrator (doc 04 §6, B3.10).
     *
     * <p>Flagged in the audit trail as a bootstrap rather than as an ordinary grant: nobody had the
     * power to issue this one, which is a different fact from somebody having exercised it.
     */
    @EventListener
    public void onClanCreated(ClanCreated event) {
        grants.issueSystemGrant(
                event.clanId(),
                event.createdBy(),
                Permission.CLAN_ADMIN,
                PermissionScope.clan(),
                event.createdBy(),
                AuditAction.PERMISSION_BOOTSTRAPPED);

        log.info(
                "Bootstrapped CLAN_ADMIN for founder clanId={} userId={}",
                event.clanId(),
                event.createdBy());
    }

    /**
     * An approved member can see the clan (doc 04 §6, B3.11).
     *
     * <p>{@code CLAN_VIEW} at the scope they were admitted into: their home branch when they joined
     * through a branch code, clan-wide otherwise. In v1 reads are clan-wide either way (doc 04
     * §5.5), so the scope is not yet what limits what they see — it is what the permissions screen
     * shows, and what a later read-partitioning change would already be built on.
     */
    @EventListener
    public void onMembershipGranted(MembershipGranted event) {
        PermissionScope scope =
                event.homeBranchId() == null
                        ? PermissionScope.clan()
                        : PermissionScope.branch(event.homeBranchId());

        grants.issueSystemGrant(
                event.clanId(),
                event.userId(),
                Permission.CLAN_VIEW,
                scope,
                event.approvedBy(),
                AuditAction.PERMISSION_GRANTED);
    }

    /**
     * A membership ending deactivates that person's grants in the clan (B3.12).
     *
     * <p>Belt and braces. Grants already confer nothing without an active membership (doc 04 §1.6),
     * so this changes no decision — it changes what the permissions screen says, and it means
     * somebody re-admitted later comes back with nothing rather than with whatever they held on the
     * day they were removed. That second property is the one that would otherwise surprise people.
     */
    @EventListener
    public void onMembershipRevoked(MembershipRevoked event) {
        int revoked = grants.revokeAllFor(event.clanId(), event.userId(), event.actorUserId());
        if (revoked > 0) {
            log.info(
                    "Deactivated {} grants after membership ended clanId={} userId={}",
                    revoked,
                    event.clanId(),
                    event.userId());
        }
    }
}
