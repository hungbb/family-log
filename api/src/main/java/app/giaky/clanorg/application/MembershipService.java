package app.giaky.clanorg.application;

import app.giaky.authorization.application.PermissionQueryService;
import app.giaky.authorization.domain.Permission;
import app.giaky.authorization.domain.PermissionResolver;
import app.giaky.authorization.domain.Target;
import app.giaky.clanorg.application.event.MembershipRevoked;
import app.giaky.clanorg.domain.Branch;
import app.giaky.clanorg.domain.BranchRepository;
import app.giaky.clanorg.domain.ClanMembership;
import app.giaky.clanorg.domain.ClanMembershipRepository;
import app.giaky.common.audit.AuditAction;
import app.giaky.common.audit.AuditRecord;
import app.giaky.common.audit.AuditWriter;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.Paged;
import app.giaky.common.types.UserId;
import app.giaky.identity.application.UserDirectory;
import app.giaky.identity.application.UserProfile;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The member list, and the two ways out of a clan (doc 05 §4.5).
 *
 * <p>Names and addresses come from {@link UserDirectory}, Identity's published surface. ClanOrg
 * holds user ids and nothing else about an account (doc 02 §1.1), so the member list is assembled
 * from two sources rather than read from one join — which is the price of the boundary and the
 * reason the boundary still exists at the end of this phase.
 */
@Service
public class MembershipService {

    private static final Logger log = LoggerFactory.getLogger(MembershipService.class);

    private final ClanMembershipRepository memberships;
    private final BranchRepository branches;
    private final PermissionResolver resolver;
    private final PermissionQueryService permissions;
    private final LastAdminProtection lastAdmin;
    private final UserDirectory users;
    private final AuditWriter auditWriter;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public MembershipService(
            ClanMembershipRepository memberships,
            BranchRepository branches,
            PermissionResolver resolver,
            PermissionQueryService permissions,
            LastAdminProtection lastAdmin,
            UserDirectory users,
            AuditWriter auditWriter,
            ApplicationEventPublisher events,
            Clock clock) {
        this.memberships = memberships;
        this.branches = branches;
        this.resolver = resolver;
        this.permissions = permissions;
        this.lastAdmin = lastAdmin;
        this.users = users;
        this.auditWriter = auditWriter;
        this.events = events;
        this.clock = clock;
    }

    /**
     * The clan's members, filtered and paged (doc 05 §4.5).
     *
     * <p>The name search runs in Identity and comes back as a set of ids, which this intersects with
     * the clan's memberships. Pushing the filter into one SQL statement would mean joining {@code
     * users} from a ClanOrg query, and doc 02 §7 rule 1 says no. The cost is bounded by the number
     * of accounts in one clan, which does not grow with the tree.
     */
    @Transactional(readOnly = true)
    public Paged<MemberView> list(
            UserId caller,
            ClanId clanId,
            String search,
            BranchId branchFilter,
            int page,
            int size) {

        // Membership and nothing finer: reads are clan-wide in v1 (doc 04 §5.5).
        resolver.requireMembership(caller, clanId);

        List<ClanMembership> active = memberships.findAllActive(clanId);
        if (branchFilter != null) {
            // Resolved rather than just matched. Filtering by an id from another clan would
            // otherwise answer "no members", which is a truthful-sounding way of confirming that
            // the id exists somewhere — exactly what doc 04 §7 refuses to do.
            if (branches.findById(clanId, branchFilter).isEmpty()) {
                throw new NotFoundException(ErrorCode.BRANCH_NOT_FOUND, "No such branch.");
            }
            active = active.stream().filter(m -> branchFilter.equals(m.homeBranchId())).toList();
        }
        if (search != null && !search.isBlank()) {
            Set<UserId> matches = Set.copyOf(users.search(search));
            active = active.stream().filter(m -> matches.contains(m.userId())).toList();
        }

        Paged<ClanMembership> pageOfMemberships = Paged.of(active, page, size);
        Map<UserId, UserProfile> profiles =
                users.profiles(pageOfMemberships.content().stream().map(ClanMembership::userId).toList());
        Map<BranchId, String> branchNames = branchNames(clanId);
        // One query for the whole page, so the list can say who to ask for access without a
        // per-row permission lookup.
        Set<UserId> administrators = permissions.administrators(clanId);

        List<MemberView> content = new ArrayList<>(pageOfMemberships.content().size());
        for (ClanMembership membership : pageOfMemberships.content()) {
            UserProfile profile = profiles.get(membership.userId());
            content.add(
                    new MemberView(
                            membership.userId().value(),
                            profile == null ? null : profile.displayName(),
                            profile == null ? null : profile.email(),
                            profile == null ? null : profile.avatarMediaId(),
                            membership.homeBranchId() == null
                                    ? null
                                    : membership.homeBranchId().value(),
                            membership.homeBranchId() == null
                                    ? null
                                    : branchNames.get(membership.homeBranchId()),
                            membership.joinedAt(),
                            administrators.contains(membership.userId()),
                            // Phase 6 fills this in; the field is in the contract from doc 05 §4.5
                            // so the member list does not change shape when it does.
                            null));
        }
        return new Paged<>(content, page, size, pageOfMemberships.totalElements());
    }

    /**
     * Removes another member (doc 05 §4.5).
     *
     * <p>Refused for the last administrator (I-M3) — through {@link LastAdminProtection}, whose
     * implementation now counts live {@code CLAN_ADMIN} grants instead of asking who founded the
     * clan. The call site did not change, which is what the port was for.
     *
     * <p>{@code MEMBER_APPROVE} <b>over the branch the member sits in</b>, not over the clan. A
     * branch approver admits people to their chi and can therefore remove them from it; they cannot
     * reach into the chi next door. A member with no home branch is a clan-level concern and needs a
     * clan-scoped approver.
     */
    @Transactional
    public void remove(UserId caller, ClanId clanId, UserId target) {
        ClanMembership membership =
                memberships
                        .findActive(clanId, target)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "That person is not a member of this clan."));

        var authorizing =
                resolver.require(
                        caller,
                        Permission.MEMBER_APPROVE,
                        membership.homeBranchId() == null
                                ? Target.clan(clanId)
                                : Target.branch(clanId, membership.homeBranchId()));
        lastAdmin.requireNotLastAdmin(clanId, target);

        Instant now = clock.instant();
        memberships.save(membership.remove(now));

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.MEMBERSHIP_REVOKED,
                                clanId,
                                AuditRecord.TARGET_MEMBERSHIP,
                                membership.id(),
                                caller)
                        .authorizedBy(authorizing.authorizingGrantId()));
        events.publishEvent(new MembershipRevoked(clanId, target, caller, false, now));

        log.info("Membership revoked clanId={} userId={} by={}", clanId, target, caller);
    }

    /**
     * Leaves a clan.
     *
     * <p>Subject to the same last-admin rule: a founder who could leave would strand the clan, and
     * "delete the clan" is the action they actually want.
     */
    @Transactional
    public void leave(UserId caller, ClanId clanId) {
        // No permission needed beyond being here: leaving is a member's own business. On the
        // allow-list in architecture.AuthorizationCoverageTest for that reason.
        resolver.requireMembership(caller, clanId);
        lastAdmin.requireNotLastAdmin(clanId, caller);

        ClanMembership membership =
                memberships
                        .findActive(clanId, caller)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.CLAN_NOT_FOUND,
                                                "That clan does not exist, or you are not a member"
                                                        + " of it."));

        Instant now = clock.instant();
        memberships.save(membership.leave(now));

        auditWriter.write(
                AuditRecord.inClan(
                        AuditAction.MEMBERSHIP_LEFT,
                        clanId,
                        AuditRecord.TARGET_MEMBERSHIP,
                        membership.id(),
                        caller));
        events.publishEvent(new MembershipRevoked(clanId, caller, caller, true, now));
    }

    private Map<BranchId, String> branchNames(ClanId clanId) {
        Map<BranchId, String> names = new HashMap<>();
        for (Branch branch : branches.findAllByClan(clanId)) {
            names.put(branch.id(), branch.name());
        }
        return names;
    }
}
