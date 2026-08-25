package app.giaky.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.method.HandlerTypePredicate;

/** Web-layer plumbing that is not tied to a single controller. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** The versioned API base path from {@code .docs/05-api-specification.md} §1. */
    public static final String API_BASE_PATH = "/api/v1";

    /**
     * Applies {@value #API_BASE_PATH} to every controller in the application, so no controller
     * repeats it and none can forget it. Actuator and the OpenAPI endpoints live outside our base
     * package and are unaffected.
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                API_BASE_PATH, HandlerTypePredicate.forBasePackage("app.giaky"));
    }

    /**
     * Blanket {@code ETag}s for {@code GET} responses, so an unchanged payload costs a 304 instead
     * of a body on the wire.
     *
     * <p>This hashes the rendered response and therefore still pays for producing it. Read paths
     * that can cheaply answer from a version counter — the clan tree, versioned aggregates — should
     * short-circuit earlier with {@code app.giaky.common.web.ETags} instead.
     */
    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> shallowEtagHeaderFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
                new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registration.addUrlPatterns("/api/*");
        registration.setName("shallowEtagHeaderFilter");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 100);
        return registration;
    }
}
