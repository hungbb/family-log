package app.giaky.authorization.infrastructure;

import app.giaky.authorization.domain.GrantPort;
import app.giaky.authorization.domain.PermissionGrant;
import app.giaky.authorization.domain.PermissionGrantRepository;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@link GrantPort} over the grant table, with the per-request cache of doc 06 §7 in front of it.
 *
 * <p>The clock lives here rather than in the resolver, which is what keeps the resolver free of
 * time: expired grants are filtered out of the answer, so nothing downstream can forget to. A grant
 * that lapses mid-request keeps working until the next request, which is the correct trade — a
 * consistent answer within one transaction beats a check that changes its mind halfway through a
 * multi-node mutation.
 */
@Component
public class CachingGrantAdapter implements GrantPort {

    private final PermissionGrantRepository grants;
    private final AuthorizationRequestCache cache;
    private final Clock clock;

    public CachingGrantAdapter(
            PermissionGrantRepository grants, AuthorizationRequestCache cache, Clock clock) {
        this.grants = grants;
        this.cache = cache;
        this.clock = clock;
    }

    @Override
    public List<PermissionGrant> effectiveGrants(ClanId clan, UserId user) {
        return cache.get(
                AuthorizationRequestCache.CacheKey.grantsOf(clan, user),
                () -> grants.findEffectiveFor(clan, user, clock.instant()));
    }

    @Override
    public void invalidate(ClanId clan, UserId user) {
        cache.evict(clan, user);
    }
}
