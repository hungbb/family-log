package app.giaky.linking.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code person_profile_propagation}. */
public interface ProfilePropagationJpaRepository
        extends JpaRepository<ProfilePropagationJpaEntity, PersonId> {

    Optional<ProfilePropagationJpaEntity> findByClanIdAndPersonId(ClanId clanId, PersonId personId);

    void deleteByClanIdAndPersonId(ClanId clanId, PersonId personId);
}
