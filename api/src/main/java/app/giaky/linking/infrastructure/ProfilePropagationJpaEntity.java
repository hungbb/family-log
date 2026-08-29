package app.giaky.linking.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The {@code person_profile_propagation} row (FR-N6).
 *
 * <p>One row per node that has opted in to anything. The table stays empty for the overwhelming
 * majority of nodes, which is the shape the default deserves: off costs nothing to store.
 */
@Entity
@Table(name = "person_profile_propagation")
public class ProfilePropagationJpaEntity {

    @Id
    @Column(name = "person_id", nullable = false, updatable = false)
    private PersonId personId;

    @Column(name = "clan_id", nullable = false, updatable = false)
    private ClanId clanId;

    @Column(name = "propagate_avatar", nullable = false)
    private boolean propagateAvatar;

    @Column(name = "propagate_display_name", nullable = false)
    private boolean propagateDisplayName;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PersonId getPersonId() {
        return personId;
    }

    public void setPersonId(PersonId personId) {
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
