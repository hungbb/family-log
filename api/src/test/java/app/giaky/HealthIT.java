package app.giaky;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.giaky.common.web.CorrelationId;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The Phase 0 wire test: the context starts, Flyway has migrated a database that began empty, and
 * {@code /actuator/health} reports {@code UP} — which, because the DB health indicator is enabled,
 * means the application really did reach PostgreSQL.
 */
class HealthIT extends app.giaky.support.AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("/actuator/health reports UP against a real database")
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    @DisplayName("every response carries a correlation id")
    void responsesCarryACorrelationId() throws Exception {
        String id =
                mockMvc.perform(get("/actuator/health"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getHeader(CorrelationId.HEADER);

        assertThat(id).isNotBlank();
    }

    @Test
    @DisplayName("an inbound correlation id is propagated rather than replaced")
    void inboundCorrelationIdIsPropagated() throws Exception {
        String id =
                mockMvc.perform(get("/actuator/health").header(CorrelationId.HEADER, "trace-123"))
                        .andReturn()
                        .getResponse()
                        .getHeader(CorrelationId.HEADER);

        assertThat(id).isEqualTo("trace-123");
    }

    @Test
    @DisplayName("Flyway migrated V1 from an empty database")
    void flywayAppliedTheFirstMigration() {
        Boolean applied =
                jdbcTemplate.queryForObject(
                        "SELECT success FROM flyway_schema_history WHERE version = '1'",
                        Boolean.class);

        assertThat(applied).isTrue();
    }

    @Test
    @DisplayName("V1 leaves f_unaccent() usable in an immutable context")
    void immutableUnaccentWrapperWorks() {
        // The point of the wrapper: Phase 4's generated tsvector column needs an IMMUTABLE function,
        // so assert both the folding and the volatility label.
        String folded =
                jdbcTemplate.queryForObject("SELECT f_unaccent('Nguyễn Phúc')", String.class);
        String volatility =
                jdbcTemplate.queryForObject(
                        "SELECT provolatile FROM pg_proc WHERE proname = 'f_unaccent'", String.class);

        assertThat(folded).isEqualTo("Nguyen Phuc");
        assertThat(volatility).isEqualTo("i");
    }

    @Test
    @DisplayName("the extensions V1 declares are installed")
    void extensionsAreInstalled() {
        var installed =
                jdbcTemplate.queryForList(
                        "SELECT extname FROM pg_extension WHERE extname IN ('pgcrypto', 'unaccent', 'pg_trgm')",
                        String.class);

        assertThat(installed).containsExactlyInAnyOrder("pgcrypto", "unaccent", "pg_trgm");
    }

    @Test
    @DisplayName("the datasource is the Testcontainers Postgres, not a developer's local database")
    void datasourceIsTheContainer() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(POSTGRES.getJdbcUrl());
        }
    }
}
