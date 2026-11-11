package app.giaky.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.SQLException;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests: the full application context on a real PostgreSQL 16, with
 * Flyway having migrated the schema exactly as it will in production.
 *
 * <p><b>Requires Docker.</b> Subclasses are named {@code *IT} and run under Failsafe in the
 * {@code verify} phase, so {@code ./mvnw test} stays runnable on a machine without a daemon.
 *
 * <p>One container is shared by the whole suite — starting Postgres per class would dominate the
 * run time — and each test method runs in a transaction that is rolled back afterwards, so tests
 * cannot see each other's writes. A test that genuinely needs committed data (anything asserting on
 * an {@code AFTER_COMMIT} listener) must opt out with {@code @Transactional(propagation = NEVER)}
 * and clean up after itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("integration")
public abstract class AbstractIntegrationTest {

    /**
     * Started once per JVM and never stopped: Testcontainers' Ryuk sidecar reaps it when the JVM
     * exits. {@code static} is what makes the reuse happen — JUnit would otherwise instantiate a
     * fresh container for every test class.
     */
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("giaky_test")
                    .withUsername("giaky")
                    .withPassword("giaky")
                    .withReuse(true);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @PersistenceContext protected EntityManager entityManager;

    @Autowired private JdbcTemplate savepoints;

    /**
     * Pushes pending JPA writes to the database and forgets what the session has cached.
     *
     * <p>Needed whenever a test mixes JPA with raw SQL, which several do — asserting on a column the
     * mapping does not expose, or forcing a state no endpoint can produce. Without the flush the raw
     * statement runs before the insert it is meant to act on; without the clear, a subsequent read
     * through JPA answers from the first-level cache and never sees the raw update.
     */
    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * Asserts that the database refuses a statement, and leaves the test's transaction usable.
     *
     * <p>PostgreSQL aborts the whole transaction at the first error: every statement after it fails
     * with {@code 25P02}, whatever it is. A schema test that provokes a violation and then carries
     * on is therefore either broken by its own first assertion or — worse — satisfied by it, because
     * "current transaction is aborted" is a {@code DataAccessException} too, and a second {@code
     * assertThatThrownBy} cannot tell a constraint firing from the wreckage of the previous one. The
     * savepoint confines the damage to the one statement under test.
     *
     * <p>A refusal that is really the test's own mistake — a misspelt column is SQLSTATE class 42 —
     * fails here rather than passing as "the constraint works".
     *
     * @return the assertion on what was thrown, for the caller to narrow further
     */
    protected AbstractThrowableAssert<?, ? extends Throwable> assertThatRefused(
            ThrowingCallable statement) {

        savepoints.execute("SAVEPOINT expected_refusal");
        Throwable thrown = catchThrowable(statement);
        savepoints.execute(
                thrown == null
                        ? "RELEASE SAVEPOINT expected_refusal"
                        : "ROLLBACK TO SAVEPOINT expected_refusal");

        assertThat(thrown)
                .as("the database should have refused this statement")
                .isInstanceOf(DataAccessException.class);
        if (NestedExceptionUtils.getRootCause(thrown) instanceof SQLException cause) {
            assertThat(cause.getSQLState())
                    .as("refused by a rule of the schema, not by a mistake in the statement: %s", cause.getMessage())
                    .doesNotStartWith("42")
                    .isNotEqualTo("25P02");
        }
        return assertThat(thrown);
    }
}
