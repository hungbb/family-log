package app.giaky.notification.application;

import app.giaky.authorization.application.PermissionQueryService;
import app.giaky.authorization.domain.Permission;
import app.giaky.authorization.domain.Target;
import app.giaky.common.types.UserId;
import app.giaky.identity.application.UserDirectory;
import app.giaky.identity.application.UserProfile;
import app.giaky.linking.application.event.ClaimDecided;
import app.giaky.linking.application.event.ClaimSubmitted;
import app.giaky.linking.application.event.PersonLinked;
import app.giaky.linking.application.event.PersonUnlinked;
import app.giaky.notification.domain.NotificationType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns Phase 6's four events into somebody's notification list (B6.9).
 *
 * <p><b>Routing a claim is a scope question</b> (FR-N2). A claim on a Chi Hai node goes to the
 * people who could actually decide it and to nobody else — asked of {@link PermissionQueryService},
 * which asks the one resolver, so the inbox somebody is told about and the inbox they can act on
 * are the same list by construction.
 *
 * <p><b>Nobody is told about a thing they just did.</b> A requester who withdraws their own claim,
 * and a member who unlinks themselves, get no notification: the confirmation is the screen in front
 * of them.
 *
 * <p>Plain {@code @EventListener}, inside the publisher's transaction. A decision that commits and a
 * notification that does not would leave a relative waiting for an answer that has already been
 * given — and the write is one small insert on the path of an action a human just took.
 */
@Component
public class LinkingNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(LinkingNotificationListener.class);

    private final NotificationService notifications;
    private final PermissionQueryService permissions;
    private final UserDirectory users;

    public LinkingNotificationListener(
            NotificationService notifications,
            PermissionQueryService permissions,
            UserDirectory users) {

        this.notifications = notifications;
        this.permissions = permissions;
        this.users = users;
    }

    /** To everybody who may decide this claim, and to nobody else (FR-N2). */
    @EventListener
    public void onClaimSubmitted(ClaimSubmitted event) {
        Set<UserId> approvers =
                permissions.holdersCovering(
                        event.clanId(),
                        Permission.NODE_CLAIM_APPROVE,
                        Target.person(
                                event.clanId(), event.personId().value(), event.branchId()));

        // The requester may hold the permission themselves — a clan administrator claiming their own
        // node does — and telling them about their own claim is noise.
        List<UserId> recipients =
                approvers.stream().filter(user -> !user.equals(event.requesterUserId())).toList();
        if (recipients.isEmpty()) {
            // Worth a log line: a clan whose only approvers are branch-scoped has nobody who can
            // answer a claim on an unplaced node, and that is an administrator's problem to fix.
            log.warn(
                    "Claim has no approver in scope clanId={} claimId={} personId={}",
                    event.clanId(),
                    event.claimId(),
                    event.personId());
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("claimId", event.claimId().toString());
        payload.put("personId", event.personId().value().toString());
        payload.put("personName", event.personName());
        payload.put("requesterName", displayNameOf(event.requesterUserId()));

        notifications.raiseAll(
                recipients, event.clanId(), NotificationType.CLAIM_SUBMITTED, payload);
    }

    /** To the requester, unless they are the one who withdrew it. */
    @EventListener
    public void onClaimDecided(ClaimDecided event) {
        if (event.outcome() == ClaimDecided.Outcome.CANCELLED) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("claimId", event.claimId().toString());
        payload.put("personId", event.personId().value().toString());
        payload.put("personName", event.personName());
        payload.put("outcome", event.outcome().name());
        payload.put("reason", event.reason());

        notifications.raise(
                event.requesterUserId(),
                event.clanId(),
                NotificationType.CLAIM_DECIDED,
                payload);
    }

    /**
     * To the person who has just been placed in the tree by somebody else (FR-N4).
     *
     * <p>Skipped when the link came from their own claim: they are getting {@code CLAIM_DECIDED} for
     * that, and two notifications about one event read as a bug.
     */
    @EventListener
    public void onPersonLinked(PersonLinked event) {
        if (event.viaClaim()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("personId", event.personId().value().toString());
        payload.put("personName", event.personName());
        payload.put("actorName", displayNameOf(event.actorUserId()));

        notifications.raise(
                event.linkedUserId(), event.clanId(), NotificationType.NODE_LINKED, payload);
    }

    /** To the person whose link was removed, unless they removed it themselves (FR-N5). */
    @EventListener
    public void onPersonUnlinked(PersonUnlinked event) {
        if (event.selfService()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("personId", event.personId().value().toString());
        payload.put("personName", event.personName());
        payload.put("actorName", displayNameOf(event.actorUserId()));

        notifications.raise(
                event.previousUserId(), event.clanId(), NotificationType.NODE_UNLINKED, payload);
    }

    /**
     * A name for a notification line, or null.
     *
     * <p>Null rather than a fallback string, because the client renders the sentence and knows which
     * wording to use when it has no name. A server-side "Unknown" would be an English word in a
     * Vietnamese interface.
     */
    private String displayNameOf(UserId userId) {
        if (userId == null) {
            return null;
        }
        return users.profile(userId).map(UserProfile::displayName).orElse(null);
    }
}
