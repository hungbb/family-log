package app.giaky.clanorg.infrastructure;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * The {@code branches} row.
 *
 * <p>{@code parentBranchId} is a plain id rather than a {@code @ManyToOne}. Mapping it as an
 * association would let a caller walk the tree object by object, which for a ten-deep structure is
 * ten queries where the materialized path answers in none — and it would put a lazy proxy inside an
 * aggregate that is supposed to be a value.
 */
@Entity
@Table(name = "branches")
public class BranchJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "clan_id", nullable = false, updatable = false)
    private ClanId clanId;

    @Column(name = "parent_branch_id")
    private BranchId parentBranchId;

    @Column(name = "branch_code", nullable = false, updatable = false, length = 32)
    private String branchCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "branch_root_person_id")
    private UUID branchRootPersonId;

    @Column(name = "path", nullable = false, length = 512)
    private String path;

    /** {@code SMALLINT} in the schema; {@code int} here, which PostgreSQL narrows on assignment. */
    @Column(name = "depth", nullable = false)
    private int depth;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

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

    public BranchId getParentBranchId() {
        return parentBranchId;
    }

    public void setParentBranchId(BranchId parentBranchId) {
        this.parentBranchId = parentBranchId;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public void setBranchCode(String branchCode) {
        this.branchCode = branchCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getBranchRootPersonId() {
        return branchRootPersonId;
    }

    public void setBranchRootPersonId(UUID branchRootPersonId) {
        this.branchRootPersonId = branchRootPersonId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
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
        return "BranchJpaEntity[id=" + id + ", clanId=" + clanId + ", path=" + path + "]";
    }
}
