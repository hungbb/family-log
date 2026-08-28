package app.giaky.common.audit.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The {@code audit_entries} row.
 *
 * <p>One of the tables doc 06 §2.1 exempts from the domain/entity split: it carries no invariants
 * and nothing ever loads it to make a decision, so a second class to map onto would be ceremony.
 *
 * <p>Mutable fields with a no-arg constructor because Hibernate requires them. Nothing outside this
 * package touches the type, and the table itself is append-only, so the mutability is contained.
 */
@Entity
@Table(name = "audit_entries")
public class AuditEntryJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "clan_id")
    private UUID clanId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "target_type", nullable = false, length = 40)
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json")
    private String beforeJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json")
    private String afterJson;

    @Column(name = "authorizing_grant_id")
    private UUID authorizingGrantId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEntryJpaEntity() {
        // for Hibernate
    }

    AuditEntryJpaEntity(
            UUID id,
            UUID clanId,
            UUID actorUserId,
            String action,
            String targetType,
            UUID targetId,
            String beforeJson,
            String afterJson,
            UUID authorizingGrantId,
            String ipAddress,
            String correlationId,
            Instant occurredAt) {
        this.id = id;
        this.clanId = clanId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.authorizingGrantId = authorizingGrantId;
        this.ipAddress = ipAddress;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    /** Which grant authorised the action (FR-Z8); null when nothing had to be authorised. */
    public UUID getAuthorizingGrantId() {
        return authorizingGrantId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
