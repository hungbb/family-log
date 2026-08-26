package app.giaky.common.security;

import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.ProblemDetailWriter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The HTTP security chain.
 *
 * <p>Stateless bearer authentication: {@link JwtAuthenticationFilter} establishes the principal,
 * {@code /auth/**} and the health and documentation endpoints are open, and everything else needs
 * one. There is no session, no form login and no {@code UserDetailsService} — the only credential
 * this application understands is a token it minted itself.
 *
 * <p>CSRF stays off, and that is safe precisely <i>because</i> the session lives in a header: a
 * cross-site form can make a browser attach cookies, but it cannot make it attach an
 * {@code Authorization} header. A CSRF token here would defend a cookie that does not exist.
 *
 * <p><b>The rules below are coarse and are not the security boundary.</b> They answer "is there a
 * caller?" and nothing else. Whether that caller may act on a particular clan, branch or person is
 * decided by {@code PermissionResolver} inside the application service (doc 04 §5.1).
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    /**
     * Endpoints reachable without a token.
     *
     * <p>{@code /auth/**} has to be open — it is where a token comes from. Health is open because a
     * load balancer probes it before anyone has signed in. The OpenAPI document is open because the
     * web client generates its types from it and it describes the shape of the API, never its data.
     * Everything else, the rest of Actuator included, is closed.
     */
    private static final String[] PUBLIC_PATHS = {
        "/api/v1/auth/**",
        "/actuator/health",
        "/actuator/health/**",
        "/actuator/info",
        "/v3/api-docs",
        "/v3/api-docs/**",
        "/swagger-ui.html",
        "/swagger-ui/**"
    };

    /** One year, the value browsers require before they will consider HSTS preloading. */
    private static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;

    private final List<String> allowedOrigins;

    public SecurityConfig(@Value("${app.cors.allowed-origins:}") List<String> allowedOrigins) {
        this.allowedOrigins = List.copyOf(allowedOrigins);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            AuthenticatedUserPort authenticatedUsers,
            ProblemDetailWriter problemWriter)
            throws Exception {

        JwtAuthenticationFilter jwtFilter =
                new JwtAuthenticationFilter(jwtService, authenticatedUsers, problemWriter);

        return http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .headers(SecurityConfig::responseHeaders)
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(PUBLIC_PATHS)
                                        .permitAll()
                                        // Preflight carries no credentials and must never be
                                        // answered with a 401: the browser would report it as a
                                        // CORS failure and hide the real error from the developer.
                                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(
                                                (request, response, failure) ->
                                                        problemWriter.write(
                                                                response,
                                                                ErrorCode.AUTH_REQUIRED,
                                                                "This endpoint requires a signed-in user."))
                                        .accessDeniedHandler(
                                                (request, response, failure) ->
                                                        problemWriter.write(
                                                                response,
                                                                ErrorCode.PERMISSION_DENIED,
                                                                "You may not perform this action.")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * The security headers of doc 05 §5.
     *
     * <p>No {@code Content-Security-Policy}: this service returns JSON and the one HTML page it
     * serves is Swagger UI, which a strict policy would break. CSP belongs to whatever serves the
     * web client's assets.
     */
    private static void responseHeaders(
            org.springframework.security.config.annotation.web.configurers.HeadersConfigurer<
                            HttpSecurity>
                    headers) {
        headers
                // Nothing here is meant to be framed, and a JSON response sniffed as HTML is how an
                // API endpoint becomes an XSS sink.
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(Customizer.withDefaults())
                .referrerPolicy(
                        referrer ->
                                referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                // Written only over HTTPS, so dev on http://localhost is unaffected while a
                // deployment behind TLS is pinned to it.
                .httpStrictTransportSecurity(
                        hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(HSTS_MAX_AGE_SECONDS));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(
                List.of(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.PUT.name(),
                        HttpMethod.PATCH.name(),
                        HttpMethod.DELETE.name(),
                        HttpMethod.OPTIONS.name()));
        config.setAllowedHeaders(List.of("*"));
        // The client reads the correlation id off responses to quote in bug reports, and ETag to
        // revalidate; neither is exposed to cross-origin JavaScript unless listed here. Retry-After
        // joins them in Phase 1 so a rate-limited client can say how long the wait is.
        config.setExposedHeaders(List.of("X-Correlation-Id", "ETag", "Location", "Retry-After"));
        // Credentials stay off: the session is a bearer token in a header, never a cookie, so
        // allowing them would widen the attack surface for nothing. See ADR 0003.
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
