package app.giaky.linking.infrastructure;

import app.giaky.linking.domain.ClanMemberPort;
import app.giaky.linking.domain.PersonLinkPort;
import app.giaky.linking.domain.PersonUserLinker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the Linking domain services.
 *
 * <p>{@link PersonUserLinker} carries no Spring annotation, and that is deliberate for the same
 * reason {@code PermissionResolver} carries none: it holds three invariants that must be testable
 * against stub ports with no Spring and no database (doc 09 §4.2). Knowing how to build it is this
 * layer's job.
 */
@Configuration
public class LinkingConfig {

    @Bean
    public PersonUserLinker personUserLinker(PersonLinkPort nodes, ClanMemberPort members) {
        return new PersonUserLinker(nodes, members);
    }
}
