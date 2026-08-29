package app.giaky.linking.support;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.linking.domain.ProfilePropagationRepository;
import app.giaky.linking.domain.PropagationFlags;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link ProfilePropagationRepository} in a map.
 *
 * <p>An absent key answers {@link PropagationFlags#none()}, exactly as an absent row does — the
 * default being off is the behaviour under test as much as it is the storage.
 */
public final class InMemoryProfilePropagationRepository implements ProfilePropagationRepository {

    private final Map<String, PropagationFlags> rows = new LinkedHashMap<>();

    @Override
    public PropagationFlags find(ClanId clanId, PersonId personId) {
        return rows.getOrDefault(key(clanId, personId), PropagationFlags.none());
    }

    @Override
    public void save(ClanId clanId, PersonId personId, PropagationFlags flags) {
        rows.put(key(clanId, personId), flags);
    }

    @Override
    public void clear(ClanId clanId, PersonId personId) {
        rows.remove(key(clanId, personId));
    }

    private static String key(ClanId clanId, PersonId personId) {
        return clanId.value() + ":" + personId.value();
    }
}
