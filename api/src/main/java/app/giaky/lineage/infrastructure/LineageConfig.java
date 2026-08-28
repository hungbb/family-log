package app.giaky.lineage.infrastructure;

import app.giaky.lineage.domain.AncestryProjection;
import app.giaky.lineage.domain.AncestryRepository;
import app.giaky.lineage.domain.LineageGraphService;
import app.giaky.lineage.domain.ParentageRepository;
import app.giaky.lineage.domain.PersonRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the Lineage domain services.
 *
 * <p>Neither class carries a Spring annotation, and that is deliberate for the same reason
 * {@code ClanOrgConfig} gives: {@link AncestryProjection} is held to ≥ 95 % coverage plus a property
 * test (doc 09 §4.4), and {@link LineageGraphService} holds I-R4 and I-R5. Both have to be testable
 * against in-memory repositories with no container anywhere in sight. Knowing how to build them is
 * this layer's job.
 */
@Configuration
public class LineageConfig {

    @Bean
    public AncestryProjection ancestryProjection(AncestryRepository ancestry) {
        return new AncestryProjection(ancestry);
    }

    @Bean
    public LineageGraphService lineageGraphService(
            PersonRepository persons,
            ParentageRepository parentages,
            AncestryProjection ancestry) {

        return new LineageGraphService(persons, parentages, ancestry);
    }
}
