package app.giaky.authorization.application;

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

    public GrantDecorator(ClanDirectory clans, UserDirectory users) {
        this.clans = clans;
        this.users = users;
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

        List<GrantView> views = new ArrayList<>(rows.size());
        for (PermissionGrant grant : rows) {
            UserProfile grantee = profiles.get(grant.granteeUserId());
            UserProfile granter = grant.grantedBy() == null ? null : profiles.get(grant.grantedBy());
            String scopeLabel =
                    grant.scope().type() == ScopeType.BRANCH
                            ? branchNames.get(grant.scope().branchId())
                            : null;
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
