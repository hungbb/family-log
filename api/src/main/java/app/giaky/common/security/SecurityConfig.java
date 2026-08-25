package app.giaky.common.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The HTTP security chain.
 *
 * <p><b>TODO(Phase 1):</b> this chain currently permits <i>every</i> request. There is no
 * authentication yet — no users, no tokens, nothing to authenticate against — so locking endpoints
 * down here would only produce a wall of 401s in front of an empty application.
 *
 * <p>Phase 1 replaces the {@code permitAll()} below with the real rules: {@code /auth/**},
 * {@code /actuator/health} and the OpenAPI endpoints stay open, everything under {@code /api/v1}
 * requires an authenticated principal, and a {@code JwtAuthenticationFilter} runs ahead of the
 * chain. {@code SecurityConfigTest} pins the current shape and will fail the moment that lands —
 * that failure is the reminder to update it, not a regression.
 *
 * <p>What is already correct and should survive Phase 1: the session policy is stateless, CSRF is
 * off (there is no cookie to forge — the client sends a bearer token), and CORS is driven by
 * configuration so {@code prod} can be strict while {@code dev} allows the Vite dev server.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final List<String> allowedOrigins;

    public SecurityConfig(@Value("${app.cors.allowed-origins:}") List<String> allowedOrigins) {
        this.allowedOrigins = List.copyOf(allowedOrigins);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                // TODO(Phase 1): replace with the real authorization rules.
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
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
        // revalidate; neither is exposed to cross-origin JavaScript unless listed here.
        config.setExposedHeaders(List.of("X-Correlation-Id", "ETag", "Location"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
