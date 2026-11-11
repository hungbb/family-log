package app.giaky.clanorg.support;

import app.giaky.clanorg.application.MemberNodeLookup;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Who is recorded as which node, in a {@code Map} — for the ClanOrg tests, which have no tree.
 *
 * <p>The same arrangement as {@code InMemoryLineageAncestryPort}, for the same reason: {@code
 * MembershipService} takes its port at construction time, in a field initializer, before the
 * Linking fixtures that can answer it for real exist. Those fixtures call {@link #backedBy}; tests
 * that only care about the member list itself call {@link #link}.
 */
public final class InMemoryMemberNodeLookup implements MemberNodeLookup {

    private final Map<ClanId, Map<UserId, PersonId>> held = new HashMap<>();
    private MemberNodeLookup delegate;

    /** Hands every question to the real Linking graph instead of the map above. */
    public void backedBy(MemberNodeLookup target) {
        this.delegate = target;
    }

    /** Records that {@code member} holds {@code node} in {@code clanId}. */
    public InMemoryMemberNodeLookup link(ClanId clanId, UserId member, PersonId node) {
        held.computeIfAbsent(clanId, key -> new HashMap<>()).put(member, node);
        return this;
    }

    @Override
    public Map<UserId, PersonId> nodesOf(ClanId clanId, Collection<UserId> members) {
        if (delegate != null) {
            return delegate.nodesOf(clanId, members);
        }
        Map<UserId, PersonId> answer = new LinkedHashMap<>();
        Map<UserId, PersonId> inClan = held.getOrDefault(clanId, Map.of());
        for (UserId member : members) {
            PersonId node = inClan.get(member);
            if (node != null) {
                answer.put(member, node);
            }
        }
        return answer;
    }
}
