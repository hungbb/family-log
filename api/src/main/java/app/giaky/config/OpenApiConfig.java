package app.giaky.config;

import app.giaky.common.security.CurrentUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI document behind {@code /swagger-ui.html}.
 *
 * <p>The bearer scheme is declared globally: from Phase 1 every endpoint outside {@code /auth/**}
 * needs a token, so declaring it once and letting the auth controller be the exception is both
 * shorter and harder to get wrong than annotating each controller.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    static {
        // @CurrentUser is filled in from the access token by CurrentUserArgumentResolver; it is not
        // something a client sends. springdoc cannot know that about a home-grown annotation, and
        // left alone it documents the argument as what an unannotated object parameter would be —
        // a required query parameter named "caller", typed as the internal AuthenticatedUser — on
        // every authenticated operation. The document is the contract clients are generated from,
        // so that is a wrong contract rather than a cosmetic slip. Same treatment springdoc gives
        // Spring Security's own @AuthenticationPrincipal.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
    }

    @Bean
    public OpenAPI giaKyOpenApi(@Value("${app.api.base-url:http://localhost:8080}") String baseUrl) {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Gia Ký API")
                                .version("v1")
                                .description(
                                        "REST API for Gia Ký, a gia phả (family lineage) platform. "
                                                + "Design source of truth: .docs/05-api-specification.md.")
                                .contact(new Contact().name("Gia Ký").url("https://giaky.app"))
                                .license(new License().name("Proprietary")))
                .servers(List.of(new Server().url(baseUrl).description("Base URL")))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        BEARER_SCHEME,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description(
                                                        "Issued by POST /api/v1/auth/login and"
                                                            + " refreshed by POST /api/v1/auth/refresh."
                                                            + " Valid for 15 minutes.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
