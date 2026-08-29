package app.giaky.notification.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One notification, as the bell and the panel render it (F6.10).
 *
 * <p>The payload crosses as a map rather than as a typed shape per {@link
 * app.giaky.notification.domain.NotificationType}. Four types today would be four records, four
 * mappers and four DTOs for something the client turns straight into an i18n interpolation — and a
 * fifth type is then a change in five files rather than one. The keys per type are documented on
 * {@code NotificationService}.
 *
 * @param clanId null for a notification that is not about one clan; everything Phase 6 raises has one
 * @param readAt null while unread — the field the badge counts
 */
public record NotificationView(
        UUID id,
        UUID clanId,
        String type,
        Map<String, Object> payload,
        Instant readAt,
        Instant createdAt) {}
