package app.giaky.notification.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The {@code notifications} row.
 *
 * <p>One of the three tables doc 06 §2.1 exempts from the domain/entity split. There is no invariant
 * here worth a domain object — a notification is written once, read once and marked read — and a
 * {@code Notification} record with a mapper either side of it would be ceremony rather than a
 * boundary.
 */
@Entity
@Table(name = "notifications")
public class NotificationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private UserId recipientUserId;

    @Column(name = "clan_id", updatable = false)
    private ClanId clanId;

    @Column(name = "type", nullable = false, length = 40, updatable = false)
    private String type;

    /** Everything the client needs to render the line and link to what it is about. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false)
    private String payload;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserId getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(UserId recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public ClanId getClanId() {
        return clanId;
    }

    public void setClanId(ClanId clanId) {
        this.clanId = clanId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
