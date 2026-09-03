package app.giaky.linking.infrastructure;

import app.giaky.common.types.ClanId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to {@code person_profile_propagation}.
 *
 * <p>The person id is a raw {@code UUID} here rather than a {@code PersonId} because it is this
 * table's primary key, and Hibernate does not run attribute converters over identifiers — see
 * {@link ProfilePropagationJpaEntity#getPersonId()}. {@code ClanId} still types the tenancy
 * parameter, which is the one rule 6 of {@code architecture.LayeringTest} cares about.
 */
public interface ProfilePropagationJpaRepository
        extends JpaRepository<ProfilePropagationJpaEntity, UUID> {

    Optional<ProfilePropagationJpaEntity> findByClanIdAndPersonId(ClanId clanId, UUID personId);

    void deleteByClanIdAndPersonId(ClanId clanId, UUID personId);
}
