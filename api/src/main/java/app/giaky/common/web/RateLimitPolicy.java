package app.giaky.common.web;

import java.time.Duration;

/**
 * One rate limit: how many requests, over what window, under what name.
 *
 * <p>The limits are those of doc 05 §5. They are constants rather than configuration on purpose —
 * a login limit that an operator can raise is a login limit an operator will raise the first time
 * someone complains, and the number exists to make credential stuffing expensive.
 *
 * @param name identifies the bucket family; part of the key, so two policies never share a bucket
 * @param permits requests allowed per window
 * @param window the refill period
 */
public record RateLimitPolicy(String name, int permits, Duration window) {

    /** Doc 05 §5: 10 login attempts per minute from one address. */
    public static final RateLimitPolicy LOGIN_PER_IP =
            new RateLimitPolicy("login-ip", 10, Duration.ofMinutes(1));

    /**
     * Doc 05 §5: 5 login attempts per minute against one account, whatever the source address.
     *
     * <p>The per-IP limit alone does not protect an account from a distributed guessing attack; this
     * one does, at the cost of letting an attacker lock a known address out of its own login for up
     * to a minute. That trade is the standard one and is why the window is short.
     */
    public static final RateLimitPolicy LOGIN_PER_ACCOUNT =
            new RateLimitPolicy("login-account", 5, Duration.ofMinutes(1));

    /** Doc 05 §5: 5 registrations per minute from one address. */
    public static final RateLimitPolicy REGISTER_PER_IP =
            new RateLimitPolicy("register-ip", 5, Duration.ofMinutes(1));

    /** Doc 05 §5: 5 password-reset requests per minute from one address. */
    public static final RateLimitPolicy FORGOT_PASSWORD_PER_IP =
            new RateLimitPolicy("forgot-password-ip", 5, Duration.ofMinutes(1));

    public RateLimitPolicy {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive");
        }
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
    }
}
