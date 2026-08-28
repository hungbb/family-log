package app.giaky.lineage.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The {@code unions} row — a marriage.
 *
 * <p>{@code partnerAId} and {@code partnerBId} are stored in canonical order (V8), which is what
 * makes I-N2 a plain unique index. Neither position means anything; {@code Union.create} is the only
 * thing that decides which is which.
 */
@Entity
@Table(name = "unions")
public class UnionJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "clan_id", nullable = false, updatable = false)
    private ClanId clanId;

    @Column(name = "partner_a_id", nullable = false, updatable = false)
    private PersonId partnerAId;

    @Column(name = "partner_b_id", nullable = false, updatable = false)
    private PersonId partnerBId;

    @Column(name = "union_type", nullable = false, length = 12)
    private String unionType;

    @Column(name = "status", nullable = false, length = 12)
    private String status;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "year", column = @Column(name = "start_year")),
        @AttributeOverride(name = "month", column = @Column(name = "start_month")),
        @AttributeOverride(name = "day", column = @Column(name = "start_day")),
        @AttributeOverride(
                name = "precision",
                column = @Column(name = "start_precision", nullable = false, length = 10)),
        @AttributeOverride(name = "circa", column = @Column(name = "start_circa", nullable = false)),
        @AttributeOverride(name = "rangeEndYear", column = @Column(name = "start_range_end_year")),
        @AttributeOverride(name = "lunarText", column = @Column(name = "start_lunar_text", length = 100))
    })
    private PartialDateEmbeddable startDate;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "year", column = @Column(name = "end_year")),
        @AttributeOverride(name = "month", column = @Column(name = "end_month")),
        @AttributeOverride(name = "day", column = @Column(name = "end_day")),
        @AttributeOverride(
                name = "precision",
                column = @Column(name = "end_precision", nullable = false, length = 10)),
        @AttributeOverride(name = "circa", column = @Column(name = "end_circa", nullable = false)),
        @AttributeOverride(name = "rangeEndYear", column = @Column(name = "end_range_end_year")),
        @AttributeOverride(name = "lunarText", column = @Column(name = "end_lunar_text", length = 100))
    })
    private PartialDateEmbeddable endDate;

    @Column(name = "notes")
    private String notes;

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

    public PersonId getPartnerAId() {
        return partnerAId;
    }

    public void setPartnerAId(PersonId partnerAId) {
        this.partnerAId = partnerAId;
    }

    public PersonId getPartnerBId() {
        return partnerBId;
    }

    public void setPartnerBId(PersonId partnerBId) {
        this.partnerBId = partnerBId;
    }

    public String getUnionType() {
        return unionType;
    }

    public void setUnionType(String unionType) {
        this.unionType = unionType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public PartialDateEmbeddable getStartDate() {
        return startDate;
    }

    public void setStartDate(PartialDateEmbeddable startDate) {
        this.startDate = startDate;
    }

    public PartialDateEmbeddable getEndDate() {
        return endDate;
    }

    public void setEndDate(PartialDateEmbeddable endDate) {
        this.endDate = endDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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
        return "UnionJpaEntity[id=" + id + ", a=" + partnerAId + ", b=" + partnerBId + "]";
    }
}
