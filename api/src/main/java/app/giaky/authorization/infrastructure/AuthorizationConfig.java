package app.giaky.authorization.infrastructure;

import app.giaky.authorization.domain.BranchHierarchyPort;
import app.giaky.authorization.domain.GrantPort;
import app.giaky.authorization.domain.LineageAncestryPort;
import app.giaky.authorization.domain.MembershipPort;
import app.giaky.authorization.domain.PermissionResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the resolver.
 *
 * <p>{@link PermissionResolver} carries no Spring annotation, and that is the point rather than an
 * oversight: it holds the rules the whole product depends on, and those rules must be readable in a
 * unit test with four hand-written stubs and no container (doc 04 §8). Knowing how to build it is
 * this layer's job — the same arrangement {@code ClanOrgConfig} makes for {@code BranchPathService}.
 */
@Configuration
public class AuthorizationConfig {

    @Bean
    public PermissionResolver permissionResolver(
            MembershipPort memberships,
            GrantPort grants,
            BranchHierarchyPort branches,
            LineageAncestryPort ancestry,
            Clock clock) {

        return new PermissionResolver(memberships, grants, branches, ancestry, clock);
    }
}
