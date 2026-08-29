package app.giaky.linking.domain;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;

/**
 * Storage for {@link PropagationFlags} (FR-N6).
 *
 * <p>A missing row and a row with both flags off are the same answer, and readers here get {@link
 * PropagationFlags#none()} for either. That is what lets the table stay empty for the overwhelming
 * majority of nodes, which never opt in to anything.
 */
public interface ProfilePropagationRepository {

    /** The flags for one node. Never empty — an absent row is {@link PropagationFlags#none()}. */
    PropagationFlags find(ClanId clanId, PersonId personId);

    void save(ClanId clanId, PersonId personId, PropagationFlags flags);

    /** Forgets the flags for a node — what unlinking does, so the next occupant starts from off. */
    void clear(ClanId clanId, PersonId personId);
}
