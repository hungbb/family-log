package app.giaky.linking.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.linking.domain.ProfilePropagationRepository;
import app.giaky.linking.domain.PropagationFlags;
import java.time.Clock;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link ProfilePropagationRepository}. */
@Repository
public class ProfilePropagationRepositoryAdapter implements ProfilePropagationRepository {

    private final ProfilePropagationJpaRepository repository;
    private final Clock clock;

    public ProfilePropagationRepositoryAdapter(
            ProfilePropagationJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public PropagationFlags find(ClanId clanId, PersonId personId) {
        return repository
                .findByClanIdAndPersonId(clanId, personId)
                .map(row -> new PropagationFlags(row.isPropagateAvatar(), row.isPropagateDisplayName()))
                // An absent row and a row with both flags off are the same answer, and the absent
                // one is what almost every node has.
                .orElseGet(PropagationFlags::none);
    }

    @Override
    public void save(ClanId clanId, PersonId personId, PropagationFlags flags) {
        ProfilePropagationJpaEntity row =
                repository
                        .findByClanIdAndPersonId(clanId, personId)
                        .orElseGet(ProfilePropagationJpaEntity::new);
        row.setPersonId(personId);
        row.setClanId(clanId);
        row.setPropagateAvatar(flags.avatar());
        row.setPropagateDisplayName(flags.displayName());
        row.setUpdatedAt(clock.instant());
        repository.saveAndFlush(row);
    }

    @Override
    public void clear(ClanId clanId, PersonId personId) {
        repository.deleteByClanIdAndPersonId(clanId, personId);
    }
}
