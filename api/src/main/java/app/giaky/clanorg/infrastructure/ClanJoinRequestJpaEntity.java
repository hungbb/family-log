package app.giaky.clanorg.infrastructure;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** The {@code clan_join_requests} row. */
@Entity
@Table(name = "clan_join_requests")
public class ClanJoinRequestJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "clan_id", nullable = false, updatable = false)
    private ClanId clanId;

    @Column(name = "target_branch_id", updatable = false)
    private BranchId targetBranchId;

    @Column(name = "requester_user_id", nullable = false, updatable = false)
    private UserId requesterUserId;

    @Column(name = "message", updatable = false)
    private String message;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "decided_by")
    private UserId decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ClanId getClanId() {
        return clanId;
    }

    public void setClanId(ClanId clanId) {
        this.clanId = clanId;
    }

    public BranchId getTargetBranchId() {
        return targetBranchId;
    }

    public void setTargetBranchId(BranchId targetBranchId) {
        this.targetBranchId = targetBranchId;
    }

    public UserId getRequesterUserId() {
        return requesterUserId;
    }

    public void setRequesterUserId(UserId requesterUserId) {
        this.requesterUserId = requesterUserId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UserId getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(UserId decidedBy) {
        this.decidedBy = decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Override
    public String toString() {
        return "ClanJoinRequestJpaEntity[id=" + id + ", clanId=" + clanId + ", status=" + status + "]";
    }
}
