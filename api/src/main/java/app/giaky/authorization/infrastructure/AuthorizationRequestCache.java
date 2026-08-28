package app.giaky.authorization.infrastructure;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * One grant query per request, however many checks run (B3.6, doc 06 §7).
 *
 * <p>A single mutation asks the resolver several times over — once for the coarse pre-check, once in
 * the service, once per endpoint of a relationship — and every one of those calls loads the same
 * handful of rows. Without this the permission model would cost a round trip per question, and the
 * per-node {@code canEdit} flags of Phase 4 would be unaffordable.
 *
 * <p><b>Why not {@code @RequestScope}.</b> A request-scoped bean needs a proxy at every injection
 * point and, more to the point, throws when there is no request — and the two places that most need
 * a correct answer are a scheduled sweep and an event listener, neither of which has one. Reading
 * the request attributes directly means the cache simply is not there outside a request, and the
 * adapter goes to the database each time. Correct either way; fast where it matters.
 *
 * <p>Cached values are the {@code List<PermissionGrant>} for a (user, clan) and the branch list for
 * a clan. Both are invalidated by {@link #evict} the moment something writes a grant, so a service
 * that grants and then re-checks inside one request sees its own write.
 */
@Component
public class AuthorizationRequestCache {

    /** The request attribute holding this request's map. Namespaced so nothing else collides. */
    private static final String ATTRIBUTE = AuthorizationRequestCache.class.getName();

    /**
     * Returns the cached value for {@code key}, computing it once per request.
     *
     * <p>Outside a request — a job, a listener, a test calling a service directly — {@code supplier}
     * runs every time and nothing is stored.
     */
    public <T> T get(CacheKey key, Supplier<T> supplier) {
        Map<CacheKey, Object> cache = currentCache();
        if (cache == null) {
            return supplier.get();
        }
        // computeIfAbsent would be neater, and would also throw on the re-entrant lookup that a
        // supplier reaching back into the resolver produces. Two steps cannot.
        @SuppressWarnings("unchecked")
        T cached = (T) cache.get(key);
        if (cached != null) {
            return cached;
        }
        T computed = supplier.get();
        cache.put(key, computed);
        return computed;
    }

    /** Forgets everything cached for one user in one clan. Called whenever a grant changes. */
    public void evict(ClanId clanId, UserId userId) {
        Map<CacheKey, Object> cache = currentCache();
        if (cache != null) {
            cache.remove(CacheKey.grantsOf(clanId, userId));
        }
    }

    /** Forgets every cached grant list for a clan — after a change that touches several people. */
    public void evictClan(ClanId clanId) {
        Map<CacheKey, Object> cache = currentCache();
        if (cache != null) {
            cache.keySet().removeIf(key -> clanId.equals(key.clanId()));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<CacheKey, Object> currentCache() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        Object existing = attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (existing instanceof Map<?, ?> map) {
            return (Map<CacheKey, Object>) map;
        }
        Map<CacheKey, Object> fresh = new HashMap<>();
        attributes.setAttribute(ATTRIBUTE, fresh, RequestAttributes.SCOPE_REQUEST);
        return fresh;
    }

    /**
     * What is being cached.
     *
     * @param userId null for entries that belong to the clan rather than to one person
     */
    public record CacheKey(String kind, ClanId clanId, UserId userId) {

        public CacheKey {
            Objects.requireNonNull(kind, "kind must not be null");
            Objects.requireNonNull(clanId, "clanId must not be null");
        }

        public static CacheKey grantsOf(ClanId clanId, UserId userId) {
            return new CacheKey("grants", clanId, Objects.requireNonNull(userId));
        }

        public static CacheKey branchesOf(ClanId clanId) {
            return new CacheKey("branches", clanId, null);
        }
    }
}
