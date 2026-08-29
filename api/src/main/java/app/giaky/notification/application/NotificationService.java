package app.giaky.notification.application;

import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.IdGenerator;
import app.giaky.common.types.Paged;
import app.giaky.common.types.UserId;
import app.giaky.notification.domain.NotificationType;
import app.giaky.notification.infrastructure.NotificationJpaEntity;
import app.giaky.notification.infrastructure.NotificationJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app notifications: raised by listeners, read from {@code /me/notifications} (B6.9, doc 05 §4.2).
 *
 * <p><b>Polled, not pushed</b> (F6.10). A family application has a handful of notifications a week
 * and no requirement that they arrive in the same second; a sixty-second poll costs one indexed
 * query against a partial index and needs no websocket, no connection budget and no reconnection
 * logic. Phase 8 adds email over these same rows.
 *
 * <p><b>Delivery is never the reason a decision fails.</b> {@link #raise} runs inside the caller's
 * transaction and its writes are part of the same commit, but a notification nobody could be found
 * for is logged and dropped rather than thrown — an approval that succeeded must not be rolled back
 * because there was nobody to tell.
 *
 * <p><b>Payload keys, by type.</b> Documented here because the client interpolates them into i18n
 * strings and nothing else enforces the shape:
 *
 * <ul>
 *   <li>{@code CLAIM_SUBMITTED} — {@code claimId}, {@code personId}, {@code personName},
 *       {@code requesterName}
 *   <li>{@code CLAIM_DECIDED} — {@code claimId}, {@code personId}, {@code personName},
 *       {@code outcome} ({@code APPROVED} / {@code REJECTED}), {@code reason}
 *   <li>{@code NODE_LINKED} / {@code NODE_UNLINKED} — {@code personId}, {@code personName},
 *       {@code actorName}
 * </ul>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** The largest page {@code GET /me/notifications} will serve. */
    public static final int MAX_PAGE_SIZE = 50;

    private static final TypeReference<Map<String, Object>> PAYLOAD_SHAPE = new TypeReference<>() {};

    private final NotificationJpaRepository notifications;
    private final ObjectMapper json;
    private final IdGenerator ids;
    private final Clock clock;

    public NotificationService(
            NotificationJpaRepository notifications,
            ObjectMapper json,
            IdGenerator ids,
            Clock clock) {

        this.notifications = notifications;
        this.json = json;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * Delivers one notification.
     *
     * <p>No permission check, and none is possible: the caller is a listener acting on a decision
     * that was already authorized, and the recipient is whoever that decision concerns. What stops
     * this being a way to write into somebody's list is that it is not reachable from any endpoint.
     */
    @Transactional
    public void raise(
            UserId recipient, ClanId clanId, NotificationType type, Map<String, Object> payload) {

        NotificationJpaEntity row = new NotificationJpaEntity();
        row.setId(ids.newId());
        row.setRecipientUserId(recipient);
        row.setClanId(clanId);
        row.setType(type.name());
        row.setPayload(serialize(payload));
        row.setCreatedAt(clock.instant());
        notifications.save(row);
    }

    /** The same notification to several people — an approver group, usually. */
    @Transactional
    public void raiseAll(
            List<UserId> recipients,
            ClanId clanId,
            NotificationType type,
            Map<String, Object> payload) {

        for (UserId recipient : recipients) {
            raise(recipient, clanId, type, payload);
        }
    }

    /** The caller's own notifications, newest first (doc 05 §4.2). */
    @Transactional(readOnly = true)
    public Paged<NotificationView> list(UserId caller, boolean unreadOnly, int page, int size) {
        int bounded = Math.clamp(size, 1, MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(0, page), bounded);

        List<NotificationJpaEntity> rows =
                unreadOnly
                        ? notifications.findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(
                                caller, pageable)
                        : notifications.findByRecipientUserIdOrderByCreatedAtDesc(caller, pageable);
        long total =
                unreadOnly
                        ? notifications.countByRecipientUserIdAndReadAtIsNull(caller)
                        : notifications.countByRecipientUserId(caller);

        return new Paged<>(rows.stream().map(this::view).toList(), Math.max(0, page), bounded, total);
    }

    /** What the badge shows. Its own query, against the partial index the badge exists for. */
    @Transactional(readOnly = true)
    public long unreadCount(UserId caller) {
        return notifications.countByRecipientUserIdAndReadAtIsNull(caller);
    }

    /**
     * Marks one of the caller's own notifications read.
     *
     * <p>Looked up by id <i>and</i> recipient, so an id belonging to somebody else is a 404 rather
     * than an authorization question — the same shape as cancelling one's own join request.
     */
    @Transactional
    public void markRead(UserId caller, UUID notificationId) {
        NotificationJpaEntity row =
                notifications
                        .findByIdAndRecipientUserId(notificationId, caller)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "No such notification."));
        if (row.getReadAt() == null) {
            row.setReadAt(clock.instant());
            notifications.save(row);
        }
    }

    private NotificationView view(NotificationJpaEntity row) {
        return new NotificationView(
                row.getId(),
                row.getClanId() == null ? null : row.getClanId().value(),
                row.getType(),
                deserialize(row.getPayload()),
                row.getReadAt(),
                row.getCreatedAt());
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return json.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException unserializable) {
            // A notification with no payload still tells somebody something happened, which is
            // better than a decision failing because a name would not serialize.
            log.warn("Notification payload could not be serialized; delivering it empty", unserializable);
            return "{}";
        }
    }

    private Map<String, Object> deserialize(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(payload, PAYLOAD_SHAPE);
        } catch (JsonProcessingException unreadable) {
            log.warn("Notification payload could not be read back", unreadable);
            return Map.of();
        }
    }
}
