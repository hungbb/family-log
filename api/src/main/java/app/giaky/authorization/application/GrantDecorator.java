package app.giaky.authorization.application;

import app.giaky.authorization.domain.LineageAncestryPort;
import app.giaky.authorization.domain.PermissionGrant;
import app.giaky.authorization.domain.ScopeType;
import app.giaky.clanorg.application.ClanDirectory;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import app.giaky.identity.application.UserDirectory;
import app.giaky.identity.application.UserProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Puts human names on grants, in two lookups per page rather than two per row.
 *
 * <p>Authorization stores ids and nothing else about an account or a branch (doc 02 §1.1), so every
 * screen that shows a grant has to assemble it from three contexts. That assembly is the same
 * wherever it happens — the permissions table, the member drawer, {@code my-permissions} — and
 * having it in one place is what stops one of those screens showing raw UUIDs after a refactor.
 */
@Component
public class GrantDecorator {

    private final ClanDirectory clans;
    private final UserDirectory users;
    private final LineageAncestryPort lineage;

    public GrantDecorator(ClanDirectory clans, UserDirectory users, LineageAncestryPort lineage) {
        this.clans = clans;
        this.users = users;
        this.lineage = lineage;
    }

    List<GrantView> decorate(ClanId clanId, List<PermissionGrant> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<UserId> userIds = new LinkedHashSet<>();
        for (PermissionGrant grant : rows) {
            userIds.add(grant.granteeUserId());
            if (grant.grantedBy() != null) {
                userIds.add(grant.grantedBy());
            }
        }
        Map<UserId, UserProfile> profiles = users.profiles(userIds);
        Map<BranchId, String> branchNames = new HashMap<>();
        clans.branches(clanId).forEach(branch -> branchNames.put(branch.id(), branch.name()));

        // Subtree scopes name a person, and a permissions screen showing a UUID is a screen nobody
        // can audit. Collected across the whole page and resolved in one call, like the profiles
        // above — the point of this class is that a page costs a fixed number of lookups.
        Set<UUID> subtreePersonIds = new LinkedHashSet<>();
        for (PermissionGrant grant : rows) {
            if (grant.scope().type() == ScopeType.SUBTREE) {
                subtreePersonIds.add(grant.scope().refId());
            }
        }
        Map<UUID, String> personNames = lineage.displayNames(clanId, subtreePersonIds);

        List<GrantView> views = new ArrayList<>(rows.size());
        for (PermissionGrant grant : rows) {
            UserProfile grantee = profiles.get(grant.granteeUserId());
            UserProfile granter = grant.grantedBy() == null ? null : profiles.get(grant.grantedBy());
            String scopeLabel =
                    switch (grant.scope().type()) {
                        case BRANCH -> branchNames.get(grant.scope().branchId());
                        case SUBTREE -> personNames.get(grant.scope().refId());
                        // Clan scope needs no label; "the whole clan" is what the scope type says.
                        case CLAN -> null;
                    };
            views.add(
                    GrantView.of(
                            grant,
                            grantee == null ? null : grantee.displayName(),
                            grantee == null ? null : grantee.email(),
                            granter == null ? null : granter.displayName(),
                            scopeLabel));
        }
        return views;
    }
}
