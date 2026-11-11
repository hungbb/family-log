package app.giaky;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The OpenAPI document describes what a client actually sends (doc 05 §1).
 *
 * <p>{@code /v3/api-docs} is what Swagger UI renders and what a client would be generated from. It
 * listed the resolved principal — the {@code @CurrentUser AuthenticatedUser caller} argument every
 * controller method takes — as a <b>required query parameter</b> on 66 of 73 operations, with the
 * internal {@code AuthenticatedUser} type published as a schema. "Try it out" in Swagger UI asked
 * for a {@code caller} object before it would send anything.
 */
class OpenApiDocumentIT extends app.giaky.support.AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private JsonNode document;

    @BeforeEach
    void fetchTheDocument() throws Exception {
        String body =
                mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        document = objectMapper.readTree(body);
    }

    @Test
    @DisplayName("no operation asks the client for the caller — the access token is the caller")
    void theCallerIsNotAParameter() {
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, JsonNode> path : document.get("paths").properties()) {
            for (Map.Entry<String, JsonNode> operation : path.getValue().properties()) {
                for (JsonNode parameter : operation.getValue().path("parameters")) {
                    if ("caller".equals(parameter.path("name").asText())) {
                        offenders.add(operation.getKey().toUpperCase() + " " + path.getKey());
                    }
                }
            }
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("the internal principal type is not published as a schema")
    void thePrincipalIsNotASchema() {
        assertThat(document.at("/components/schemas").has("AuthenticatedUser")).isFalse();
    }

    @Test
    @DisplayName("the parameters a client does send are still documented")
    void realParametersSurvive() {
        JsonNode search = document.at("/paths/~1api~1v1~1clans~1{clanId}~1persons/get/parameters");
        List<String> names = new ArrayList<>();
        search.forEach(parameter -> names.add(parameter.get("name").asText()));

        assertThat(names).contains("clanId", "q", "page", "size");
        // …and the document still says how the caller *is* identified.
        assertThat(document.at("/components/securitySchemes/bearerAuth/scheme").asText())
                .isEqualTo("bearer");
    }
}
