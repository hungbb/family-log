package app.giaky.notification.infrastructure;

import app.giaky.common.types.TenantExempt;
import app.giaky.common.types.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to {@code notifications}.
 *
 * <p>Exempt from the clan-scoping rule as a whole, and the annotation on the type says why once
 * rather than five times: a person's notifications span every clan they belong to, and the bell in
 * the top bar is one list, not one per family. The recipient id is the scope, and it comes from the
 * access token rather than from the request.
 */
@TenantExempt(
        justification =
                "A person's notification list spans every clan they are in, so no single ClanId"
                    + " exists to scope by. Every read here is scoped by the recipient id, which"
                    + " comes from the access token.")
public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, UUID> {

    List<NotificationJpaEntity> findByRecipientUserIdOrderByCreatedAtDesc(
            UserId recipientUserId, Pageable pageable);

    List<NotificationJpaEntity> findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(
            UserId recipientUserId, Pageable pageable);

    long countByRecipientUserId(UserId recipientUserId);

    long countByRecipientUserIdAndReadAtIsNull(UserId recipientUserId);

    /** Scoped by recipient as well as id, so one person cannot mark another's notification read. */
    Optional<NotificationJpaEntity> findByIdAndRecipientUserId(UUID id, UserId recipientUserId);
}
