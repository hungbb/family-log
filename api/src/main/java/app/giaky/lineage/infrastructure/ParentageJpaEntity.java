package app.giaky.lineage.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The {@code parentages} row — one parent → child edge.
 *
 * <p>No optimistic-locking version. An edge has no editable attributes: it is created, or it is
 * removed, and both are single statements against a row nobody else is holding a draft of. The
 * conflict a {@code @Version} would catch is the one {@code ux_parentage_child_role} already
 * refuses (I-R3).
 */
@Entity
@Table(name = "parentages")
public class ParentageJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "clan_id", nullable = false, updatable = false)
    private ClanId clanId;

    @Column(name = "parent_person_id", nullable = false, updatable = false)
    private PersonId parentPersonId;

    @Column(name = "child_person_id", nullable = false, updatable = false)
    private PersonId childPersonId;

    @Column(name = "parent_role", nullable = false, length = 10)
    private String parentRole;

    @Column(name = "nature", nullable = false, length = 12)
    private String nature;

    @Column(name = "union_id")
    private UUID unionId;

    @Column(name = "created_by", updatable = false)
    private UserId createdBy;

    @Column(name = "updated_by")
    private UserId updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

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

    public PersonId getParentPersonId() {
        return parentPersonId;
    }

    public void setParentPersonId(PersonId parentPersonId) {
        this.parentPersonId = parentPersonId;
    }

    public PersonId getChildPersonId() {
        return childPersonId;
    }

    public void setChildPersonId(PersonId childPersonId) {
        this.childPersonId = childPersonId;
    }

    public String getParentRole() {
        return parentRole;
    }

    public void setParentRole(String parentRole) {
        this.parentRole = parentRole;
    }

    public String getNature() {
        return nature;
    }

    public void setNature(String nature) {
        this.nature = nature;
    }

    public UUID getUnionId() {
        return unionId;
    }

    public void setUnionId(UUID unionId) {
        this.unionId = unionId;
    }

    public UserId getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UserId createdBy) {
        this.createdBy = createdBy;
    }

    public UserId getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UserId updatedBy) {
        this.updatedBy = updatedBy;
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

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    @Override
    public String toString() {
        return "ParentageJpaEntity[id="
                + id
                + ", parent="
                + parentPersonId
                + ", child="
                + childPersonId
                + "]";
    }
}
