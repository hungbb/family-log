package app.giaky.authorization.application;

import app.giaky.authorization.domain.Permission;
import app.giaky.authorization.domain.PermissionGrantRepository;
import app.giaky.authorization.domain.PermissionResolver;
import app.giaky.authorization.domain.Target;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read side of authorization: what does <i>this</i> person hold here?
 *
 * <p>Split from {@link GrantService} by audience rather than by resource. Everything here is
 * answerable to any member about themselves; everything there needs {@code ROLE_MANAGE}. Keeping
 * them apart means the endpoint every page calls on load cannot accidentally acquire a check meant
 * for the endpoint that hands out power.
 *
 * <p>This is also the seam ClanOrg uses. {@code ClanService} fills {@code myPermissions[]} from
 * {@link #permissionNames}, which is a call into a published application service rather than a
 * reach into {@code authorization.domain} — Phase 2's {@code InterimClanAccessCheck.permissionsOf}
 * had the same signature for exactly this reason, so replacing it changed one line.
 */
@Service
public class PermissionQueryService {

    private final PermissionResolver resolver;
    private final PermissionGrantRepository grants;
    private final GrantDecorator decorator;
    private final Clock clock;

    public PermissionQueryService(
            PermissionResolver resolver,
            PermissionGrantRepository grants,
            GrantDecorator decorator,
            Clock clock) {

        this.resolver = resolver;
        this.grants = grants;
        this.decorator = decorator;
        this.clock = clock;
    }

    /**
     * What the caller may do in the clan, for UI gating (doc 05 §4.3).
     *
     * <p>Names rather than the enum, because this crosses a context boundary and ends up in JSON.
     * Sorted, so a response body does not change because a {@code HashSet} rehashed.
     *
     * @return an empty list for somebody who is not a member — the caller decides whether that is a
     *     404, because only it knows whether the clan was supposed to be visible
     */
    @Transactional(readOnly = true)
    public List<String> permissionNames(UserId caller, ClanId clanId) {
        return resolver.permissionsOf(caller, clanId).stream()
                .map(Permission::name)
                .sorted()
                .toList();
    }

    /** Whether the caller holds a permission anywhere in the clan. UI gating and inbox filtering. */
    @Transactional(readOnly = true)
    public boolean holdsAnywhere(UserId caller, ClanId clanId, Permission permission) {
        return resolver.permissionsOf(caller, clanId).contains(permission);
    }

    /**
     * Who administers this clan.
     *
     * <p>Answerable to any member, and deliberately so: "who do I ask?" is the first question
     * somebody has when a refusal names a permission they do not hold, and a member list that
     * cannot answer it sends them to support instead of to their uncle.
     *
     * <p>Not filtered by membership here — the caller is ClanOrg's member list, which is iterating
     * active members already, so a grant belonging to somebody who has left simply matches nobody.
     */
    @Transactional(readOnly = true)
    public Set<UserId> administrators(ClanId clanId) {
        return grants.findEffectiveByPermission(clanId, Permission.CLAN_ADMIN, clock.instant())
                .stream()
                .map(grant -> grant.granteeUserId())
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Who may exercise {@code permission} over <i>this</i> target (FR-N2, B6.9).
     *
     * <p>Routing a claim to its approvers is a scope question, not a membership one: an approver for
     * Chi Hai should hear about a Chi Hai node and about nothing else. Answering it by loading every
     * grant of the permission and then asking {@link PermissionResolver#can} per holder keeps one
     * decision point — a second scope-containment query in SQL would be a second policy, and the
     * direction it would drift in is the one that tells the wrong people.
     *
     * <p>{@code CLAN_ADMIN} holders are candidates too, because {@code CLAN_ADMIN} confers
     * everything (FR-Z6) without a row naming the permission. Expanding it here rather than storing
     * the closure is the same choice {@code Permission.implies} makes.
     *
     * @return the accounts that would be allowed to act, in no particular order; empty when nobody
     *     covers the target, which is a real answer for an unplaced node in a clan whose only
     *     approvers are branch-scoped
     */
    @Transactional(readOnly = true)
    public Set<UserId> holdersCovering(ClanId clanId, Permission permission, Target target) {
        Instant now = clock.instant();
        Set<UserId> candidates = new LinkedHashSet<>();
        grants.findEffectiveByPermission(clanId, permission, now)
                .forEach(grant -> candidates.add(grant.granteeUserId()));
        grants.findEffectiveByPermission(clanId, Permission.CLAN_ADMIN, now)
                .forEach(grant -> candidates.add(grant.granteeUserId()));

        Set<UserId> covering = new LinkedHashSet<>();
        for (UserId candidate : candidates) {
            if (resolver.can(candidate, permission, target)) {
                covering.add(candidate);
            }
        }
        return covering;
    }

    /**
     * The permissions screen for oneself — {@code GET /clans/{clanId}/my-permissions}.
     *
     * <p>The flat list <i>and</i> the rows behind it. "Why can I edit this?" and "when does that
     * stop?" are questions with answers only the grant rows carry, and a person should not need
     * {@code ROLE_MANAGE} to ask them about their own access.
     */
    @Transactional(readOnly = true)
    public MyPermissionsView myPermissions(UserId caller, ClanId clanId) {
        // A non-member is told the clan does not exist, not handed an empty list (doc 04 §7). An
        // empty list would be a 200 that confirms the clan id is real.
        resolver.requireMembership(caller, clanId);

        Set<Permission> effective = resolver.permissionsOf(caller, clanId);
        List<String> names = effective.stream().map(Permission::name).sorted().toList();

        List<GrantView> mine =
                decorator
                        .decorate(
                                clanId,
                                grants.findEffectiveFor(clanId, caller, clock.instant()))
                        .stream()
                        .sorted(Comparator.comparing(GrantView::permission))
                        .toList();

        return new MyPermissionsView(names, mine);
    }
}
