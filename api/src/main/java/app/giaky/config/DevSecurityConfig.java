package app.giaky.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Leaves {@code /api/v1/dev/**} open, under the {@code dev} profile only.
 *
 * <p>The dev endpoints need to work while signed out — completing a password reset is exactly the
 * case — so they cannot sit behind the main chain's {@code authenticated()} rule. Rather than
 * adding a path to that chain's public list and relying on the controller's own
 * {@code @Profile("dev")} to keep it harmless in a deployment, the exemption itself is
 * profile-scoped: under any other profile this bean does not exist and {@code /api/v1/dev/**} is
 * simply another authenticated path that answers 404.
 *
 * <p>Ordered ahead of the main chain and matching only that prefix, so it changes nothing else.
 */
@Configuration
@Profile("dev")
public class DevSecurityConfig {

    @Bean
    @Order(0)
    public SecurityFilterChain devEndpointsFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/v1/dev/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
