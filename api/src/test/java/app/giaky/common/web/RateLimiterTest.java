package app.giaky.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The bucket behaviour behind doc 05 §5. */
class RateLimiterTest {

    private static final RateLimitPolicy THREE_PER_MINUTE =
            new RateLimitPolicy("test", 3, Duration.ofMinutes(1));

    private final RateLimiter limiter = new RateLimiter();

    @Test
    @DisplayName("permits up to the quota, then refuses")
    void permitsUpToTheQuota() {
        for (int attempt = 0; attempt < THREE_PER_MINUTE.permits(); attempt++) {
            assertThat(limiter.tryConsume(THREE_PER_MINUTE, "subject").allowed()).isTrue();
        }

        assertThat(limiter.tryConsume(THREE_PER_MINUTE, "subject").allowed()).isFalse();
    }

    @Test
    @DisplayName("a refusal says how long to wait, so Retry-After can be a real number")
    void refusalCarriesAWait() {
        for (int attempt = 0; attempt < THREE_PER_MINUTE.permits(); attempt++) {
            limiter.tryConsume(THREE_PER_MINUTE, "subject");
        }

        RateLimiter.Decision refused = limiter.tryConsume(THREE_PER_MINUTE, "subject");

        assertThat(refused.retryAfter()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("subjects have their own buckets")
    void subjectsAreIndependent() {
        for (int attempt = 0; attempt < THREE_PER_MINUTE.permits(); attempt++) {
            limiter.tryConsume(THREE_PER_MINUTE, "first");
        }

        assertThat(limiter.tryConsume(THREE_PER_MINUTE, "second").allowed()).isTrue();
    }

    @Test
    @DisplayName("policies have their own buckets, so a login limit is not spent by a registration")
    void policiesAreIndependent() {
        RateLimitPolicy other = new RateLimitPolicy("other", 3, Duration.ofMinutes(1));
        for (int attempt = 0; attempt < THREE_PER_MINUTE.permits(); attempt++) {
            limiter.tryConsume(THREE_PER_MINUTE, "subject");
        }

        assertThat(limiter.tryConsume(other, "subject").allowed()).isTrue();
    }

    @Test
    @DisplayName("resetting forgets every bucket")
    void resetClearsBuckets() {
        for (int attempt = 0; attempt < THREE_PER_MINUTE.permits(); attempt++) {
            limiter.tryConsume(THREE_PER_MINUTE, "subject");
        }

        limiter.reset();

        assertThat(limiter.tryConsume(THREE_PER_MINUTE, "subject").allowed()).isTrue();
    }

    @Test
    @DisplayName("the documented limits are the ones doc 05 §5 states")
    void documentedLimitsMatchTheSpecification() {
        assertThat(RateLimitPolicy.LOGIN_PER_IP.permits()).isEqualTo(10);
        assertThat(RateLimitPolicy.LOGIN_PER_ACCOUNT.permits()).isEqualTo(5);
        assertThat(RateLimitPolicy.REGISTER_PER_IP.permits()).isEqualTo(5);
        assertThat(RateLimitPolicy.FORGOT_PASSWORD_PER_IP.permits()).isEqualTo(5);
        assertThat(RateLimitPolicy.LOGIN_PER_IP.window()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("a nonsensical policy cannot be constructed")
    void policyIsValidated() {
        assertThatThrownBy(() -> new RateLimitPolicy("bad", 0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitPolicy("bad", 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
