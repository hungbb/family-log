package app.giaky.common.web;

import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.ProblemDetailWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The per-address half of the rate limits in doc 05 §5.
 *
 * <p>A filter rather than an interceptor so a flood is turned away before it reaches the security
 * chain, request binding, or a BCrypt verification — the expensive parts. The per-<i>account</i>
 * login limit cannot live here, because the account is in the request body and reading a body in a
 * filter means buffering every request to serve the few that need it; {@code AuthService} applies
 * that one, where the email is already parsed.
 *
 * <p>Registered explicitly in {@code WebConfig} rather than by component scanning, so its order
 * relative to {@code CorrelationIdFilter} is stated rather than inherited: a rejected request must
 * still carry a correlation id.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** Paths whose limit is keyed by client address. Exact matches; these are all POST endpoints. */
    private static final Map<String, RateLimitPolicy> LIMITED_PATHS =
            Map.of(
                    "/api/v1/auth/login", RateLimitPolicy.LOGIN_PER_IP,
                    "/api/v1/auth/register", RateLimitPolicy.REGISTER_PER_IP,
                    "/api/v1/auth/forgot-password", RateLimitPolicy.FORGOT_PASSWORD_PER_IP);

    private final RateLimiter rateLimiter;
    private final ProblemDetailWriter problemWriter;

    public RateLimitFilter(RateLimiter rateLimiter, ProblemDetailWriter problemWriter) {
        this.rateLimiter = rateLimiter;
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        RateLimitPolicy policy = policyFor(request);
        if (policy == null) {
            chain.doFilter(request, response);
            return;
        }

        RateLimiter.Decision decision = rateLimiter.tryConsume(policy, clientAddress(request));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }

        log.info("Rate limit {} exceeded for {}", policy.name(), request.getRequestURI());
        problemWriter.write(
                response,
                ErrorCode.RATE_LIMITED,
                "Too many attempts. Wait a moment and try again.",
                decision.retryAfter());
    }

    private static RateLimitPolicy policyFor(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return null;
        }
        return LIMITED_PATHS.get(request.getRequestURI());
    }

    /**
     * The caller's address.
     *
     * <p>{@code getRemoteAddr()} is deliberate: behind a proxy, {@code
     * server.forward-headers-strategy=framework} makes Spring rewrite it from the forwarding
     * headers the proxy is trusted to have set. Reading {@code X-Forwarded-For} here instead would
     * let any client pick its own rate-limit bucket by sending the header itself.
     */
    private static String clientAddress(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? "unknown" : address;
    }

    /** The paths this filter needs to see. Used by the registration in {@code WebConfig}. */
    public static List<String> limitedUrlPatterns() {
        return List.copyOf(LIMITED_PATHS.keySet());
    }
}
