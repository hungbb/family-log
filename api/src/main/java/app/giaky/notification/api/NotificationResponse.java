package app.giaky.notification.api;

import app.giaky.notification.application.NotificationView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One notification (doc 05 §4.2).
 *
 * <p>{@code payload} is deliberately open: the client builds an i18n key from {@code type} and
 * interpolates the payload into it, so the server never composes a sentence it would then have to
 * translate. The keys each type carries are documented on {@code NotificationService}.
 */
public record NotificationResponse(
        UUID id,
        UUID clanId,
        @Schema(description = "CLAIM_SUBMITTED | CLAIM_DECIDED | NODE_LINKED | NODE_UNLINKED")
                String type,
        Map<String, Object> payload,
        Instant readAt,
        Instant createdAt) {

    public static NotificationResponse of(NotificationView view) {
        return new NotificationResponse(
                view.id(),
                view.clanId(),
                view.type(),
                view.payload(),
                view.readAt(),
                view.createdAt());
    }
}
