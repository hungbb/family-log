package app.giaky.linking.infrastructure;

import app.giaky.common.types.ClanId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The {@code person_profile_propagation} row (FR-N6).
 *
 * <p>One row per node that has opted in to anything. The table stays empty for the overwhelming
 * majority of nodes, which is the shape the default deserves: off costs nothing to store.
 */
@Entity
@Table(name = "person_profile_propagation")
public class ProfilePropagationJpaEntity {

    /**
     * The node, and the key — one row per node, so {@code person_id} <i>is</i> the primary key
     * rather than a surrogate sitting beside one.
     *
     * <p>A raw {@code UUID} rather than a {@code PersonId}, and the exception to what {@code
     * app.giaky.common.persistence} otherwise buys us: Hibernate never runs an {@code
     * AttributeConverter} over an identifier — not by {@code autoApply}, which JPA §3.7 excludes ids
     * from, and not by an explicit {@code @Convert} either. It reaches the attribute with no
     * converter, finds no {@code JdbcType} for the wrapper, and the {@code EntityManagerFactory}
     * fails to start. Every other entity here already keys on a plain {@code UUID} for the same
     * reason; only the reference columns get the typed wrapper.
     *
     * <p>The wrapper survives where it matters — {@link ProfilePropagationRepositoryAdapter} still
     * takes and returns a {@code PersonId}, and unwraps at this one boundary.
     */
    @Id
    @Column(name = "person_id", nullable = false, updatable = false)
    private UUID personId;

    @Column(name = "clan_id", nullable = false, updatable = false)
    private ClanId clanId;

    @Column(name = "propagate_avatar", nullable = false)
    private boolean propagateAvatar;

    @Column(name = "propagate_display_name", nullable = false)
    private boolean propagateDisplayName;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getPersonId() {
        return personId;
    }

    public void setPersonId(UUID personId) {
        this.personId = personId;
    }

    public ClanId getClanId() {
        return clanId;
    }

    public void setClanId(ClanId clanId) {
        this.clanId = clanId;
    }

    public boolean isPropagateAvatar() {
        return propagateAvatar;
    }

    public void setPropagateAvatar(boolean propagateAvatar) {
        this.propagateAvatar = propagateAvatar;
    }

    public boolean isPropagateDisplayName() {
        return propagateDisplayName;
    }

    public void setPropagateDisplayName(boolean propagateDisplayName) {
        this.propagateDisplayName = propagateDisplayName;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
