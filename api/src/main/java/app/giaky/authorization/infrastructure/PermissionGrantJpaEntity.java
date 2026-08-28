package app.giaky.authorization.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The {@code permission_grants} row.
 *
 * <p>No {@code @Version}. A grant is issued once and revoked once; there is no field two people edit
 * concurrently, and the one race that matters — two people granting the same thing at the same
 * moment — is held by {@code ux_grant_active} (I-G5), which is a stronger guarantee than a version
 * column.
 *
 * <p>{@code permission} and {@code scope_type} are stored as their constant names rather than as
 * ordinals, matching the {@code CHECK} constraints in V6: a row has to be readable in {@code psql}
 * during an incident, and an ordinal that shifts when somebody reorders an enum is a silent
 * privilege change.
 */
@Entity
@Table(name = "permission_grants")
public class PermissionGrantJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "clan_id", nullable = false, updatable = false)
    private ClanId clanId;

    @Column(name = "grantee_user_id", nullable = false, updatable = false)
    private UserId granteeUserId;

    @Column(name = "permission", nullable = false, length = 30, updatable = false)
    private String permission;

    @Column(name = "scope_type", nullable = false, length = 10, updatable = false)
    private String scopeType;

    @Column(name = "scope_ref_id", updatable = false)
    private UUID scopeRefId;

    @Column(name = "granted_by", nullable = false, updatable = false)
    private UserId grantedBy;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UserId revokedBy;

    protected PermissionGrantJpaEntity() {
        // for Hibernate
    }

    PermissionGrantJpaEntity(
            UUID id,
            ClanId clanId,
            UserId granteeUserId,
            String permission,
            String scopeType,
            UUID scopeRefId,
            UserId grantedBy,
            Instant grantedAt,
            Instant expiresAt,
            Instant revokedAt,
            UserId revokedBy) {

        this.id = id;
        this.clanId = clanId;
        this.granteeUserId = granteeUserId;
        this.permission = permission;
        this.scopeType = scopeType;
        this.scopeRefId = scopeRefId;
        this.grantedBy = grantedBy;
        this.grantedAt = grantedAt;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.revokedBy = revokedBy;
    }

    public UUID getId() {
        return id;
    }

    public ClanId getClanId() {
        return clanId;
    }

    public UserId getGranteeUserId() {
        return granteeUserId;
    }

    public String getPermission() {
        return permission;
    }

    public String getScopeType() {
        return scopeType;
    }

    public UUID getScopeRefId() {
        return scopeRefId;
    }

    public UserId getGrantedBy() {
        return grantedBy;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public UserId getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(UserId revokedBy) {
        this.revokedBy = revokedBy;
    }

    @Override
    public String toString() {
        return "PermissionGrantJpaEntity[id="
                + id
                + ", permission="
                + permission
                + ", scope="
                + scopeType
                + "]";
    }
}
