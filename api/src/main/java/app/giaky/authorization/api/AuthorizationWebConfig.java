package app.giaky.authorization.api;

import app.giaky.authorization.domain.PermissionResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Puts {@link RequiresPermissionInterceptor} into the MVC chain.
 *
 * <p>Registered here rather than in {@code config.WebConfig} so that the wiring lives with the
 * context that owns it — and, just as deliberately, so that the MVC slice tests keep working.
 * {@code @WebMvcTest} scans for {@code WebMvcConfigurer} and {@code HandlerInterceptor} beans by
 * type, which would pull the interceptor into every slice and, with it, the whole resolver and its
 * four adapters. A plain {@code @Configuration} is excluded from those slices, so the interceptor is
 * built here as a {@code @Bean} and reaches the chain through an inner configurer: present in the
 * running application, absent from a test about error bodies.
 *
 * <p>{@code addPathPatterns} is not used. The interceptor does nothing at all on a handler without
 * the annotation, so a path list would only be a second place to forget to add a route.
 */
@Configuration
public class AuthorizationWebConfig {

    @Bean
    public RequiresPermissionInterceptor requiresPermissionInterceptor(
            PermissionResolver resolver) {
        return new RequiresPermissionInterceptor(resolver);
    }

    @Bean
    public WebMvcConfigurer requiresPermissionInterceptorRegistration(
            RequiresPermissionInterceptor interceptor) {

        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor);
            }
        };
    }
}
