package app.giaky.notification.api;

import app.giaky.common.security.AuthenticatedUser;
import app.giaky.common.security.CurrentUser;
import app.giaky.common.types.Paged;
import app.giaky.common.web.PageResponse;
import app.giaky.notification.application.NotificationService;
import app.giaky.notification.application.NotificationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own notifications — doc 05 §4.2.
 *
 * <p>Not nested under a clan, and it cannot be: a person's notifications span every family they
 * belong to, and the bell in the top bar is one list. Every endpoint here acts on the signed-in
 * account and takes no user id, which is what makes them safe without a permission check.
 */
@RestController
@RequestMapping("/me/notifications")
@Tag(name = "Notifications", description = "In-app notifications, polled by the client (F6.10)")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @Operation(
            summary = "Your notifications",
            description =
                    "Newest first. `unreadOnly=true` is what the badge polls; the panel asks for"
                        + " everything.")
    public PageResponse<NotificationResponse> list(
            @CurrentUser AuthenticatedUser caller,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Positive @Max(NotificationService.MAX_PAGE_SIZE)
                    int size) {

        Paged<NotificationView> result = notifications.list(caller.id(), unreadOnly, page, size);
        return new PageResponse<>(
                result.content().stream().map(NotificationResponse::of).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    @GetMapping("/unread-count")
    @Operation(
            summary = "How many are unread",
            description =
                    "One indexed count against the partial index the badge exists for — cheaper than"
                        + " asking for the list and counting it client-side every minute.")
    public UnreadCountResponse unreadCount(@CurrentUser AuthenticatedUser caller) {
        return new UnreadCountResponse(notifications.unreadCount(caller.id()));
    }

    @PostMapping("/{notificationId}/read")
    @Operation(summary = "Mark one of yours read")
    public ResponseEntity<Void> markRead(
            @CurrentUser AuthenticatedUser caller, @PathVariable UUID notificationId) {

        notifications.markRead(caller.id(), notificationId);
        return ResponseEntity.noContent().build();
    }

    /** The badge's whole answer. A record rather than a bare number, so the shape can grow. */
    public record UnreadCountResponse(long unread) {}
}
