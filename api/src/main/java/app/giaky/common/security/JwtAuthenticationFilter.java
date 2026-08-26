package app.giaky.common.security;

import app.giaky.common.error.AuthenticationRequiredException;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.ProblemDetailWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns a {@code Authorization: Bearer …} header into an {@link AuthenticatedUser} principal.
 *
 * <p>Three outcomes, and the difference between them is deliberate:
 *
 * <ul>
 *   <li><b>No header</b> — the request continues anonymously. The chain decides whether that is
 *       allowed; a filter that rejected here would break every public endpoint.
 *   <li><b>A header that does not verify</b> — answered immediately with {@code 401} and the
 *       specific code ({@code TOKEN_EXPIRED} or {@code TOKEN_INVALID}). Falling through to the
 *       entry point would flatten both into {@code AUTH_REQUIRED}, and the client's refresh logic
 *       (doc 07 §4.2) is driven by exactly that distinction.
 *   <li><b>A header that verifies</b> — the account is re-read and its {@code tokenVersion} and
 *       status checked, so "log out everywhere", a password reset and a suspension all take effect
 *       on the next request rather than when the token happens to expire.
 * </ul>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // Not a @Component: a Filter bean is auto-registered by Spring Boot for every request in
    // addition to being placed in the security chain, which would run it twice. SecurityConfig
    // constructs it and inserts it once.

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AuthenticatedUserPort authenticatedUsers;
    private final ProblemDetailWriter problemWriter;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            AuthenticatedUserPort authenticatedUsers,
            ProblemDetailWriter problemWriter) {
        this.jwtService = jwtService;
        this.authenticatedUsers = authenticatedUsers;
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Optional<String> bearer = bearerToken(request);
        if (bearer.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        AccessTokenClaims claims;
        try {
            claims = jwtService.verify(bearer.get());
        } catch (AuthenticationRequiredException rejected) {
            reject(response, rejected.code(), rejected.getMessage());
            return;
        }

        Optional<AuthenticatedUser> account = authenticatedUsers.findAuthenticatable(claims.userId());
        if (account.isEmpty() || account.get().tokenVersion() != claims.tokenVersion()) {
            // Deleted, suspended, deactivated, or holding a token minted before the last global
            // revocation. One answer for all four: the token is no longer valid, and saying which
            // would confirm an account exists.
            reject(response, ErrorCode.TOKEN_INVALID, "The access token is no longer valid.");
            return;
        }

        var authentication =
                new UsernamePasswordAuthenticationToken(account.get(), null, List.of());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            chain.doFilter(request, response);
        } finally {
            // The context is thread-bound and the thread is pooled (or virtual and recycled by the
            // container); leaving a principal behind would authenticate the next request as this user.
            SecurityContextHolder.clearContext();
        }
    }

    private void reject(HttpServletResponse response, ErrorCode code, String detail)
            throws IOException {
        SecurityContextHolder.clearContext();
        problemWriter.write(response, code, detail);
    }

    private static Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
