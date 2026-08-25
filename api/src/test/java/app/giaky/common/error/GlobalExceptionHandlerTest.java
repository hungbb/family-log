package app.giaky.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.giaky.common.security.SecurityConfig;
import app.giaky.common.web.CorrelationId;
import app.giaky.common.web.CorrelationIdFilter;
import app.giaky.config.WebConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The error contract, asserted end to end through a real MVC dispatch.
 *
 * <p>This is the shape {@code web/src/api/errors.ts} parses. It is worth testing early and in
 * detail: the frontend's error handling is written against these exact field names, and a silent
 * change here surfaces as an unhelpful "something went wrong" in the UI rather than a build failure.
 */
@WebMvcTest
@Import({
    SecurityConfig.class,
    WebConfig.class,
    CorrelationIdFilter.class,
    GlobalExceptionHandler.class,
    GlobalExceptionHandlerTest.BoomController.class
})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("a domain exception becomes an RFC 7807 body with code and correlationId")
    void apiExceptionShape() throws Exception {
        mockMvc.perform(get("/api/v1/boom/denied").header(CorrelationId.HEADER, "trace-1"))
                .andExpect(status().isForbidden())
                .andExpect(header().string(CorrelationId.HEADER, "trace-1"))
                .andExpect(jsonPath("$.type").value("https://giaky.app/errors/permission-denied"))
                .andExpect(jsonPath("$.title").value("Permission denied"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("You do not hold TREE_EDIT over this node."))
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"))
                .andExpect(jsonPath("$.correlationId").value("trace-1"));
    }

    @Test
    @DisplayName("a not-found carries the specific code, not a generic 404")
    void notFoundCarriesItsCode() throws Exception {
        mockMvc.perform(get("/api/v1/boom/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLAN_NOT_FOUND"));
    }

    @Test
    @DisplayName("field-level violations travel in the errors extension")
    void domainRuleViolationCarriesFieldErrors() throws Exception {
        mockMvc.perform(get("/api/v1/boom/invalid-dates"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DATE_RANGE_INVALID"))
                .andExpect(jsonPath("$.errors[0].field").value("deathYear"))
                .andExpect(jsonPath("$.errors[0].message").value("must not precede birthYear"));
    }

    @Test
    @DisplayName("bean validation failures are 422 VALIDATION_FAILED with one entry per field")
    void beanValidationBecomes422() throws Exception {
        mockMvc.perform(
                        post("/api/v1/boom/validate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"\",\"generation\":-1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", Matchers.hasSize(2)))
                .andExpect(
                        jsonPath("$.errors[*].field")
                                .value(Matchers.containsInAnyOrder("name", "generation")));
    }

    @Test
    @DisplayName("unparseable JSON is 400 MALFORMED_REQUEST")
    void unreadableBodyBecomes400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/boom/validate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("an optimistic lock failure is translated to 409 VERSION_CONFLICT")
    void optimisticLockBecomes409() throws Exception {
        mockMvc.perform(get("/api/v1/boom/stale"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    @DisplayName("an unexpected exception leaks nothing but the correlation id")
    void unexpectedExceptionLeaksNothing() throws Exception {
        mockMvc.perform(get("/api/v1/boom/unexpected").header(CorrelationId.HEADER, "trace-9"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.correlationId").value("trace-9"))
                .andExpect(jsonPath("$.detail").value(Matchers.not(Matchers.containsString("JDBC"))))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("a junk inbound correlation id is replaced rather than echoed")
    void junkCorrelationIdIsReplaced() throws Exception {
        mockMvc.perform(
                        get("/api/v1/boom/denied")
                                .header(CorrelationId.HEADER, "<script>alert(1)</script>"))
                .andExpect(header().string(CorrelationId.HEADER, Matchers.not("<script>alert(1)</script>")))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    /** Throws one of each kind, so the handler is exercised through the real dispatcher. */
    @RestController
    static class BoomController {

        @GetMapping("/boom/denied")
        void denied() {
            throw new PermissionDeniedException("You do not hold TREE_EDIT over this node.");
        }

        @GetMapping("/boom/missing")
        void missing() {
            throw new NotFoundException(ErrorCode.CLAN_NOT_FOUND, "No such clan.");
        }

        @GetMapping("/boom/invalid-dates")
        void invalidDates() {
            throw new DomainRuleViolation(
                    ErrorCode.DATE_RANGE_INVALID,
                    "A person cannot die before being born.",
                    List.of(new FieldViolation("deathYear", "must not precede birthYear")));
        }

        @GetMapping("/boom/stale")
        void stale() {
            throw new OptimisticLockingFailureException("row version mismatch");
        }

        @GetMapping("/boom/unexpected")
        void unexpected() {
            throw new IllegalStateException("JDBC connection pool exhausted at 10.0.0.4:5432");
        }

        @PostMapping("/boom/validate")
        void validate(@jakarta.validation.Valid @RequestBody Payload payload) {
            // Never reached: validation fails first.
        }

        record Payload(@NotBlank String name, @Positive int generation) {}
    }
}
