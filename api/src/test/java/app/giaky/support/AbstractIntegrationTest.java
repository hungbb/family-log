package app.giaky.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
}
