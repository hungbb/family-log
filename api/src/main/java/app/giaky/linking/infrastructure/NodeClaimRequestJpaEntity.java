package app.giaky.linking.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** The {@code node_claim_requests} row. */
@Entity
@Table(name = "node_claim_requests")
public class NodeClaimRequestJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "clan_id", nullable = false, updatable = false)
    private ClanId clanId;

    @Column(name = "person_id", nullable = false, updatable = false)
    private PersonId personId;

    @Column(name = "requester_user_id", nullable = false, updatable = false)
    private UserId requesterUserId;

    @Column(name = "evidence", updatable = false)
    private String evidence;

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

    public PersonId getPersonId() {
        return personId;
    }

    public void setPersonId(PersonId personId) {
        this.personId = personId;
    }

    public UserId getRequesterUserId() {
        return requesterUserId;
    }

    public void setRequesterUserId(UserId requesterUserId) {
        this.requesterUserId = requesterUserId;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
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

    @Override
    public String toString() {
        return "NodeClaimRequestJpaEntity[id="
                + id
                + ", clanId="
                + clanId
                + ", status="
                + status
                + "]";
    }
}
