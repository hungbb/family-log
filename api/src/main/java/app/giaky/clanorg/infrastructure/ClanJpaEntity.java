package app.giaky.clanorg.infrastructure;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * The {@code clans} row.
 *
 * <p>A separate class from {@code Clan}, with a MapStruct mapper between them (doc 06 §2.1).
 *
 * <p>{@code rootBranchId} and the two audit columns are typed rather than raw {@code UUID}s. The
 * converters in {@code common.persistence} do the translation, and what it buys is that a derived
 * finder can take a {@code ClanId} — which is what rule 6 of {@code architecture.LayeringTest}
 * defines a tenant-safe read as.
 */
@Entity
@Table(name = "clans")
public class ClanJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "clan_code", nullable = false, updatable = false, length = 32)
    private String clanCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "origin_place", length = 255)
    private String originPlace;

    @Column(name = "cover_media_id")
    private UUID coverMediaId;

    @Column(name = "visibility", nullable = false, length = 20)
    private String visibility;

    @Column(name = "root_branch_id")
    private BranchId rootBranchId;

    /**
     * Optimistic lock (doc 03 §1).
     *
     * <p>This is what makes {@code PATCH /clans/{id}} safe: the request carries the version it read,
     * a mismatch raises {@code OptimisticLockingFailureException}, and {@code GlobalExceptionHandler}
     * turns that into the {@code 409 VERSION_CONFLICT} doc 05 §1 promises.
     */
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

    public String getClanCode() {
        return clanCode;
    }

    public void setClanCode(String clanCode) {
        this.clanCode = clanCode;
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

    public String getOriginPlace() {
        return originPlace;
    }

    public void setOriginPlace(String originPlace) {
        this.originPlace = originPlace;
    }

    public UUID getCoverMediaId() {
        return coverMediaId;
    }

    public void setCoverMediaId(UUID coverMediaId) {
        this.coverMediaId = coverMediaId;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public BranchId getRootBranchId() {
        return rootBranchId;
    }

    public void setRootBranchId(BranchId rootBranchId) {
        this.rootBranchId = rootBranchId;
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
        return "ClanJpaEntity[id=" + id + ", clanCode=" + clanCode + "]";
    }
}
