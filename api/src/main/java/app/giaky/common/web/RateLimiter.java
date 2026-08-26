package app.giaky.common.web;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory token buckets, keyed by policy and subject.
 *
 * <p>The subject is whatever the limit is <i>about</i>: a client address for the per-IP limits, a
 * normalized email for the per-account one. Buckets are created on first use and refill their whole
 * capacity at the end of each window, which is what "5 per minute" means to the person reading doc
 * 05 §5 — a greedy trickle refill would let a caller sustain the rate indefinitely by spacing
 * requests, and that is not the intent for a login endpoint.
 *
 * <p><b>One process only.</b> Two instances of this application would each grant the full quota.
 * That is correct for v1, which runs single-instance; a distributed deployment replaces the map
 * with a Bucket4j proxy over Redis and changes nothing at the call sites.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    /**
     * Above this, the map is cleared rather than grown.
     *
     * <p>Keys are attacker-controlled (any address, any email), so the map is an unbounded
     * allocation waiting to happen. Clearing forgives everyone currently limited, which is the
     * lesser evil: the alternative is an out-of-memory error that forgives everyone permanently.
     * A real eviction policy arrives with the distributed backend.
     */
    static final int MAX_TRACKED_SUBJECTS = 100_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Takes one permit for {@code subject} under {@code policy}.
     *
     * @return the outcome, carrying how long to wait when the permit was refused
     */
    public Decision tryConsume(RateLimitPolicy policy, String subject) {
        if (buckets.size() >= MAX_TRACKED_SUBJECTS) {
            log.warn(
                    "Rate-limit bucket map hit {} entries; clearing. Sustained growth here means the"
                            + " limiter is being used as a cache key by something unbounded.",
                    MAX_TRACKED_SUBJECTS);
            buckets.clear();
        }
        Bucket bucket =
                buckets.computeIfAbsent(policy.name() + '|' + subject, key -> newBucket(policy));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        return new Decision(
                probe.isConsumed(), Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    /** Forgets every bucket. Test seam; also the overflow valve above. */
    public void reset() {
        buckets.clear();
    }

    private static Bucket newBucket(RateLimitPolicy policy) {
        Bandwidth bandwidth =
                Bandwidth.builder()
                        .capacity(policy.permits())
                        .refillIntervally(policy.permits(), policy.window())
                        .build();
        return Bucket.builder().addLimit(bandwidth).build();
    }

    /**
     * @param allowed whether the permit was granted
     * @param retryAfter how long until one is available; {@link Duration#ZERO} when allowed
     */
    public record Decision(boolean allowed, Duration retryAfter) {}
}
