package app.giaky.lineage.infrastructure;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * The {@code persons} row.
 *
 * <p>No associations. Parents, children and spouses are edges in their own tables, and mapping them
 * as {@code @OneToMany} would mean every read of a person dragging in a lazy collection that the
 * tree read has already fetched in bulk — and would make "load a person" a different number of
 * queries depending on how large their family is.
 *
 * <p>{@code name_search} is absent on purpose: it is a stored generated column (V7), so the database
 * writes it and the mapping must not try to. Search reads it through a native query.
 */
@Entity
@Table(name = "persons")
public class PersonJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "clan_id", nullable = false, updatable = false)
    private ClanId clanId;

    @Column(name = "branch_id")
    private BranchId branchId;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "family_name", length = 100)
    private String familyName;

    @Column(name = "given_name", length = 100)
    private String givenName;

    @Column(name = "alias", length = 100)
    private String alias;

    @Column(name = "posthumous_name", length = 100)
    private String posthumousName;

    @Column(name = "gender", nullable = false, length = 10)
    private String gender;

    @Column(name = "life_status", nullable = false, length = 10)
    private String lifeStatus;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "year", column = @Column(name = "birth_year")),
        @AttributeOverride(name = "month", column = @Column(name = "birth_month")),
        @AttributeOverride(name = "day", column = @Column(name = "birth_day")),
        @AttributeOverride(
                name = "precision",
                column = @Column(name = "birth_precision", nullable = false, length = 10)),
        @AttributeOverride(name = "circa", column = @Column(name = "birth_circa", nullable = false)),
        @AttributeOverride(name = "rangeEndYear", column = @Column(name = "birth_range_end_year")),
        @AttributeOverride(name = "lunarText", column = @Column(name = "birth_lunar_text", length = 100))
    })
    private PartialDateEmbeddable birth;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "year", column = @Column(name = "death_year")),
        @AttributeOverride(name = "month", column = @Column(name = "death_month")),
        @AttributeOverride(name = "day", column = @Column(name = "death_day")),
        @AttributeOverride(
                name = "precision",
                column = @Column(name = "death_precision", nullable = false, length = 10)),
        @AttributeOverride(name = "circa", column = @Column(name = "death_circa", nullable = false)),
        @AttributeOverride(name = "rangeEndYear", column = @Column(name = "death_range_end_year")),
        @AttributeOverride(name = "lunarText", column = @Column(name = "death_lunar_text", length = 100))
    })
    private PartialDateEmbeddable death;

    @Column(name = "burial_place", length = 255)
    private String burialPlace;

    /** {@code SMALLINT} in the schema; nullable, because <i>đời</i> is often simply not known. */
    @Column(name = "generation")
    private Short generation;

    @Column(name = "birth_order")
    private Short birthOrder;

    @Column(name = "avatar_media_id")
    private UUID avatarMediaId;

    @Column(name = "notes_html")
    private String notesHtml;

    @Column(name = "notes_text")
    private String notesText;

    @Column(name = "linked_user_id")
    private UserId linkedUserId;

    @Column(name = "is_founding_ancestor", nullable = false)
    private boolean foundingAncestor;

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

    public BranchId getBranchId() {
        return branchId;
    }

    public void setBranchId(BranchId branchId) {
        this.branchId = branchId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getPosthumousName() {
        return posthumousName;
    }

    public void setPosthumousName(String posthumousName) {
        this.posthumousName = posthumousName;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getLifeStatus() {
        return lifeStatus;
    }

    public void setLifeStatus(String lifeStatus) {
        this.lifeStatus = lifeStatus;
    }

    public PartialDateEmbeddable getBirth() {
        return birth;
    }

    public void setBirth(PartialDateEmbeddable birth) {
        this.birth = birth;
    }

    public PartialDateEmbeddable getDeath() {
        return death;
    }

    public void setDeath(PartialDateEmbeddable death) {
        this.death = death;
    }

    public String getBurialPlace() {
        return burialPlace;
    }

    public void setBurialPlace(String burialPlace) {
        this.burialPlace = burialPlace;
    }

    public Short getGeneration() {
        return generation;
    }

    public void setGeneration(Short generation) {
        this.generation = generation;
    }

    public Short getBirthOrder() {
        return birthOrder;
    }

    public void setBirthOrder(Short birthOrder) {
        this.birthOrder = birthOrder;
    }

    public UUID getAvatarMediaId() {
        return avatarMediaId;
    }

    public void setAvatarMediaId(UUID avatarMediaId) {
        this.avatarMediaId = avatarMediaId;
    }

    public String getNotesHtml() {
        return notesHtml;
    }

    public void setNotesHtml(String notesHtml) {
        this.notesHtml = notesHtml;
    }

    public String getNotesText() {
        return notesText;
    }

    public void setNotesText(String notesText) {
        this.notesText = notesText;
    }

    public UserId getLinkedUserId() {
        return linkedUserId;
    }

    public void setLinkedUserId(UserId linkedUserId) {
        this.linkedUserId = linkedUserId;
    }

    public boolean isFoundingAncestor() {
        return foundingAncestor;
    }

    public void setFoundingAncestor(boolean foundingAncestor) {
        this.foundingAncestor = foundingAncestor;
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
        return "PersonJpaEntity[id=" + id + ", clanId=" + clanId + "]";
    }
}
