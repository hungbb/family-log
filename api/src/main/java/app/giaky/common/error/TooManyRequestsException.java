package app.giaky.common.error;

import java.time.Duration;

/**
 * 429 — a rate limit was exceeded (doc 05 §5).
 *
 * <p>The sixth branch of {@link ApiException}. The five Phase 0 branches all describe something
 * about the <i>request</i> — who is asking, whether the thing exists, whether the state allows it;
 * this one describes something about the caller's recent history, which none of them can express.
 *
 * <p>Carries the wait before a retry can succeed, so the handler can set {@code Retry-After} and the
 * client can show a real number instead of "try again later".
 */
public non-sealed class TooManyRequestsException extends ApiException {

    private final transient Duration retryAfter;

    public TooManyRequestsException(String detail, Duration retryAfter) {
        super(ErrorCode.RATE_LIMITED, detail);
        this.retryAfter = retryAfter;
    }

    /** How long the caller should wait. Never negative; may be {@link Duration#ZERO}. */
    public Duration retryAfter() {
        return retryAfter.isNegative() ? Duration.ZERO : retryAfter;
    }
}
