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

/**
 * The {@code clan_memberships} row.
 *
 * <p>No {@code @Version}: doc 03 §1 puts optimistic locking on {@code clans}, {@code branches} and
 * {@code persons}, and a membership has no field two people edit at once. The one race that matters
 * — two approvals of the same request — is held by {@code ux_membership_active} (I-M1), which is a
 * stronger guarantee than a version column would be.
 */
@Entity
@Table(name = "clan_memberships")
public class ClanMembershipJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "clan_id", nullable = false, updatable = false)
    private ClanId clanId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UserId userId;

    @Column(name = "home_branch_id")
    private BranchId homeBranchId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "invited_by")
    private UserId invitedBy;

    @Column(name = "approved_by")
    private UserId approvedBy;

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

    public UserId getUserId() {
        return userId;
    }

    public void setUserId(UserId userId) {
        this.userId = userId;
    }

    public BranchId getHomeBranchId() {
        return homeBranchId;
    }

    public void setHomeBranchId(BranchId homeBranchId) {
        this.homeBranchId = homeBranchId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    public Instant getLeftAt() {
        return leftAt;
    }

    public void setLeftAt(Instant leftAt) {
        this.leftAt = leftAt;
    }

    public UserId getInvitedBy() {
        return invitedBy;
    }

    public void setInvitedBy(UserId invitedBy) {
        this.invitedBy = invitedBy;
    }

    public UserId getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(UserId approvedBy) {
        this.approvedBy = approvedBy;
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
        return "ClanMembershipJpaEntity[id=" + id + ", clanId=" + clanId + ", status=" + status + "]";
    }
}
