package app.giaky.clanorg.infrastructure;

import app.giaky.clanorg.domain.BranchPathService;
import app.giaky.clanorg.domain.BranchRepository;
import app.giaky.clanorg.domain.CodeGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the ClanOrg domain services.
 *
 * <p>Both are plain objects with no Spring annotation on them, which is the point: {@code
 * CodeGenerator} and {@code BranchPathService} hold rules that must be testable with a fake
 * repository and a fixed random source, and an {@code @Service} on either would make Spring a
 * prerequisite for reading a unit test. Knowing how to build them is this layer's job.
 */
@Configuration
public class ClanOrgConfig {

    /** Uses a {@link java.security.SecureRandom}; see the class note on why that matters. */
    @Bean
    public CodeGenerator codeGenerator() {
        return new CodeGenerator();
    }

    @Bean
    public BranchPathService branchPathService(BranchRepository branches) {
        return new BranchPathService(branches);
    }
}
