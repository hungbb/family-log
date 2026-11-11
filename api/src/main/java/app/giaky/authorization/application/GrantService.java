package app.giaky.authorization.application;

import app.giaky.authorization.domain.AccessDecision;
import app.giaky.authorization.domain.GrantPort;
import app.giaky.authorization.domain.LineageAncestryPort;
import app.giaky.authorization.domain.Permission;
import app.giaky.authorization.domain.PermissionGrant;
import app.giaky.authorization.domain.PermissionGrantRepository;
import app.giaky.authorization.domain.PermissionResolver;
import app.giaky.authorization.domain.PermissionScope;
import app.giaky.authorization.domain.ScopeType;
import app.giaky.authorization.domain.Target;
import app.giaky.clanorg.application.ClanDirectory;
import app.giaky.common.audit.AuditAction;
import app.giaky.common.audit.AuditRecord;
import app.giaky.common.audit.AuditWriter;
import app.giaky.common.error.ConflictException;
import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.error.PermissionDeniedException;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.IdGenerator;
import app.giaky.common.types.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Granting and revoking permissions — the part of Phase 3 that people will actually use, and the
 * part with the sharpest edge (doc 04 §5.4, FR-Z5).
 *
 * <p><b>The four conditions.</b> To hand somebody {@code (P, S)} the actor must, all at once:
 *
 * <ol>
 *   <li>hold {@code ROLE_MANAGE} covering {@code S};
 *   <li>hold {@code P} themselves, at a scope that covers {@code S};
 *   <li>be granting it to somebody with an {@code ACTIVE} membership;
 *   <li>hold {@code CLAN_ADMIN} if {@code P} is {@code CLAN_ADMIN}.
 * </ol>
 *
 * <p>Condition 2 is the one that does the work. Without it, {@code ROLE_MANAGE} would be a
 * back door: a branch role manager could grant themselves {@code TREE_EDIT@CLAN} and be editing the
 * whole family a second later. With it, a grant can only ever move a capability sideways or
 * downwards — nobody hands out what they do not already hold, anywhere they do not already hold it.
 *
 * <p>All four are checked here, in the service, against the same {@code ScopeCoverage} the resolver
 * uses for every other decision. The dialog in the UI is built from {@link #grantable} so that an
 * escalation cannot be expressed at all (F3.4), but that is ergonomics; this is the rule.
 */
@Service
public class GrantService {

    private static final Logger log = LoggerFactory.getLogger(GrantService.class);

    /** How many lapsed grants one sweep marks revoked. Bounded so a neglected deployment recovers. */
    public static final int EXPIRY_BATCH_SIZE = 500;

    private final PermissionGrantRepository grants;
    private final PermissionResolver resolver;
    private final GrantPort grantCache;
    private final ClanDirectory clans;
    private final LineageAncestryPort ancestry;
    private final GrantDecorator decorator;
    private final AuditWriter auditWriter;
    private final IdGenerator ids;
    private final Clock clock;

    public GrantService(
            PermissionGrantRepository grants,
            PermissionResolver resolver,
            GrantPort grantCache,
            ClanDirectory clans,
            LineageAncestryPort ancestry,
            GrantDecorator decorator,
            AuditWriter auditWriter,
            IdGenerator ids,
            Clock clock) {

        this.grants = grants;
        this.resolver = resolver;
        this.grantCache = grantCache;
        this.clans = clans;
        this.ancestry = ancestry;
        this.decorator = decorator;
        this.auditWriter = auditWriter;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * Issues a grant (FR-Z5).
     *
     * @throws PermissionDeniedException {@code PERMISSION_ESCALATION_BLOCKED} when any of the four
     *     conditions fails — deliberately one code for all four, because distinguishing them tells a
     *     prober which of the four they are closest to satisfying
     * @throws DomainRuleViolation the permission cannot be held at that scope
     * @throws ConflictException an identical live grant already exists (I-G5)
     */
    @Transactional
    public GrantView grant(UserId actor, ClanId clanId, GrantCommand command) {
        Permission permission =
                Permission.parse(command.permission())
                        .orElseThrow(
                                () ->
                                        new DomainRuleViolation(
                                                ErrorCode.VALIDATION_FAILED,
                                                "No such permission: " + command.permission()));
        PermissionScope scope = parseScope(clanId, command);
        requireGrantableCombination(permission, scope);

        // Condition 1, and the membership gate that comes before every decision. A non-member is
        // told the clan does not exist (doc 04 §7); a member who manages roles somewhere else in
        // the tree is told they are escalating, because inside grant management that is what
        // reaching past your own scope is.
        AccessDecision authorizing;
        try {
            authorizing =
                    resolver.require(actor, Permission.ROLE_MANAGE, Target.forScope(clanId, scope));
        } catch (PermissionDeniedException denied) {
            throw escalationBlocked(
                    "You can only grant permissions within a scope you manage roles in.");
        }

        // Condition 2 — the one that closes the back door. Note it asks the same coverage question
        // the resolver asked above, over the actor's own hold on P.
        if (!holdsCovering(actor, clanId, permission, scope)) {
            throw escalationBlocked(
                    "You cannot grant " + permission + " where you do not hold it yourself.");
        }
        // Condition 4. Implied by condition 2 — CLAN_ADMIN exists only at clan scope — and stated
        // anyway, because I-G2 is the invariant a future change to the catalogue would break first.
        if (permission == Permission.CLAN_ADMIN
                && !resolver.permissionsOf(actor, clanId).contains(Permission.CLAN_ADMIN)) {
            throw escalationBlocked("Only an administrator can make somebody else one.");
        }
        // Condition 3. A grant to somebody outside the clan confers nothing anyway (doc 04 §1.6),
        // so the row would be a lie sitting in the permissions table.
        if (!clans.isActiveMember(clanId, command.granteeUserId())) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED,
                    "That person is not a member of this clan. Approve them first.");
        }

        Instant now = clock.instant();
        Optional<PermissionGrant> duplicate =
                grants.findEffectiveDuplicate(
                        clanId, command.granteeUserId(), permission, scope, now);
        if (duplicate.isPresent()) {
            throw new ConflictException(
                    ErrorCode.GRANT_ALREADY_EXISTS,
                    "They already hold that permission at that scope.");
        }
        closeLapsedPredecessor(clanId, command.granteeUserId(), permission, scope, now);

        PermissionGrant issued =
                grants.save(
                        PermissionGrant.issue(
                                ids.newId(),
                                clanId,
                                command.granteeUserId(),
                                permission,
                                scope,
                                actor,
                                command.expiresAt(),
                                now));
        grantCache.invalidate(clanId, command.granteeUserId());

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.PERMISSION_GRANTED,
                                clanId,
                                AuditRecord.TARGET_PERMISSION_GRANT,
                                issued.id(),
                                actor)
                        .authorizedBy(authorizing.authorizingGrantId()));

        log.info(
                "Permission granted clanId={} grantee={} permission={} scope={} by={}",
                clanId,
                command.granteeUserId(),
                permission,
                scope,
                actor);

        return decorator.decorate(clanId, List.of(issued)).get(0);
    }

    /**
     * Takes a grant back (doc 05 §4.6).
     *
     * <p>Soft: the row keeps its history and stops conferring anything. Revoking needs {@code
     * ROLE_MANAGE} covering the grant's own scope — not the revoker's, which would let a branch
     * manager delete the clan administrator's grant by pointing at it from inside their chi.
     *
     * @throws PermissionDeniedException {@code LAST_ADMIN_PROTECTED} when this is the clan's final
     *     administrator (I-M3)
     */
    @Transactional
    public void revoke(UserId actor, ClanId clanId, UUID grantId) {
        PermissionGrant grant =
                grants.findById(clanId, grantId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND, "No such grant."));

        AccessDecision authorizing =
                resolver.require(
                        actor, Permission.ROLE_MANAGE, Target.forScope(clanId, grant.scope()));

        if (grant.isRevoked()) {
            // Already gone. Idempotent rather than a 409: the caller wanted it revoked, and it is.
            return;
        }
        if (grant.permission() == Permission.CLAN_ADMIN) {
            requireNotFinalAdministrator(clanId, grant);
        }

        Instant now = clock.instant();
        grants.save(grant.revoke(actor, now));
        grantCache.invalidate(clanId, grant.granteeUserId());

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.PERMISSION_REVOKED,
                                clanId,
                                AuditRecord.TARGET_PERMISSION_GRANT,
                                grant.id(),
                                actor)
                        .authorizedBy(authorizing.authorizingGrantId()));

        log.info(
                "Permission revoked clanId={} grantee={} permission={} scope={} by={}",
                clanId,
                grant.granteeUserId(),
                grant.permission(),
                grant.scope(),
                actor);
    }

    /**
     * The clan's live grants (doc 05 §4.6).
     *
     * <p>Visible to anybody who manages roles anywhere in the clan, not only to administrators. A
     * branch role manager who cannot see what the branch beside theirs holds cannot tell whether the
     * person asking them for access already has it.
     */
    @Transactional(readOnly = true)
    public List<GrantView> list(UserId caller, ClanId clanId, UserId userFilter, String permissionFilter) {
        requireRoleManagerSomewhere(caller, clanId);

        Instant now = clock.instant();
        List<PermissionGrant> rows =
                grants.findAllEffective(clanId, now).stream()
                        .filter(grant -> userFilter == null || grant.granteeUserId().equals(userFilter))
                        .filter(
                                grant ->
                                        permissionFilter == null
                                                || permissionFilter.isBlank()
                                                || grant.permission().name().equals(permissionFilter))
                        .toList();
        return decorator.decorate(clanId, rows);
    }

    /**
     * The grants held by one member — the member drawer of F3.5.
     *
     * <p>Same audience as {@link #list}: whoever may manage roles may see who holds what.
     */
    @Transactional(readOnly = true)
    public List<GrantView> grantsOf(UserId caller, ClanId clanId, UserId member) {
        return list(caller, clanId, member, null);
    }

    /**
     * What the caller may grant, and where (F3.4).
     *
     * <p>Enumerated rather than derived: for every permission, every candidate scope in the clan is
     * put through the same conditions {@link #grant} enforces, and only the ones that pass are
     * offered. The candidate set is clan scope plus the clan's branches — tens of them — so this is
     * a nested loop over a small list and no queries beyond the branch tree the resolver has already
     * cached for this request.
     *
     * <p>Deriving the answer from the shape of the actor's own grants would be cleverer and would
     * be a second implementation of the covering relation, which is exactly what doc 04 §1.4 and the
     * phase notes warn against. Prefer the duplication that cannot disagree.
     */
    @Transactional(readOnly = true)
    public GrantableView grantable(UserId caller, ClanId clanId) {
        requireRoleManagerSomewhere(caller, clanId);

        List<GrantableView.GrantableScope> candidates = candidateScopes(clanId);
        List<GrantableView.GrantablePermission> offered = new ArrayList<>();

        for (Permission permission : Permission.values()) {
            List<GrantableView.GrantableScope> scopes = new ArrayList<>();
            for (GrantableView.GrantableScope candidate : candidates) {
                PermissionScope scope =
                        PermissionScope.of(
                                ScopeType.valueOf(candidate.scopeType()), candidate.scopeRefId());
                if (!permission.isValidAt(scope.type())) {
                    continue;
                }
                if (!resolver.can(caller, Permission.ROLE_MANAGE, Target.forScope(clanId, scope))) {
                    continue;
                }
                if (!holdsCovering(caller, clanId, permission, scope)) {
                    continue;
                }
                scopes.add(candidate);
            }
            if (!scopes.isEmpty()) {
                offered.add(new GrantableView.GrantablePermission(permission.name(), scopes));
            }
        }
        // Subtree scopes are deliberately not enumerated above. The candidate list is clan scope
        // plus the clan's branches — tens of entries — while a subtree scope names a person, and a
        // clan has thousands. The picker offers a person search instead and this flag is what tells
        // it the option is live (F3.6, F4.2); grant() resolves whichever person comes back.
        return new GrantableView(offered, ancestry.isAvailable());
    }

    /**
     * Renewing a grant that has run out: close the old row so the new one has somewhere to go.
     *
     * <p>A lapsed grant confers nothing from the moment it expires, but it stays in {@code
     * ux_grant_active} until the daily sweep marks it revoked — and for up to a day, granting the
     * same permission at the same scope again hit that index and answered {@code 500}. The
     * permissions screen does not list lapsed rows either, so there was no id to revoke by hand;
     * the only way out was to wait. This does for one row, now, what the sweep would have done:
     * the same revocation with no actor, and the same {@code PERMISSION_EXPIRED} entry.
     */
    private void closeLapsedPredecessor(
            ClanId clanId, UserId grantee, Permission permission, PermissionScope scope, Instant now) {

        grants.findLapsedDuplicate(clanId, grantee, permission, scope, now)
                .ifPresent(
                        lapsed -> {
                            grants.save(lapsed.revoke(null, now));
                            auditWriter.write(
                                    AuditRecord.inClan(
                                            AuditAction.PERMISSION_EXPIRED,
                                            clanId,
                                            AuditRecord.TARGET_PERMISSION_GRANT,
                                            lapsed.id(),
                                            null));
                        });
    }

    /**
     * Marks lapsed grants revoked (B3.14).
     *
     * <p>Hygiene only. Every read already filters on {@code expires_at}, so a deployment whose sweep
     * has not run for a week has not handed out a week of extra access — the table is simply untidy,
     * and an untidy permissions screen is one somebody eventually misreads.
     *
     * <p>A plain method rather than something only a scheduler can reach, so a test can age the
     * clock and call it. {@code ExpiredGrantSweepJob} is the thing with the timetable.
     *
     * @return how many grants were closed
     */
    @Transactional
    public int markExpiredRevoked() {
        Instant now = clock.instant();
        List<PermissionGrant> lapsed = grants.findLapsedNotYetRevoked(now, EXPIRY_BATCH_SIZE);
        for (PermissionGrant grant : lapsed) {
            // No actor: the calendar did this, and recording a person would be a small lie in the
            // one table that exists to be believed.
            grants.save(grant.revoke(null, now));
            auditWriter.write(
                    AuditRecord.inClan(
                            AuditAction.PERMISSION_EXPIRED,
                            grant.clanId(),
                            AuditRecord.TARGET_PERMISSION_GRANT,
                            grant.id(),
                            null));
        }
        if (!lapsed.isEmpty()) {
            log.info("Expiry sweep closed {} lapsed permission grants", lapsed.size());
        }
        return lapsed.size();
    }

    // --- collaborators inside the context ------------------------------------------------------

    /**
     * Issues a grant with no escalation check, for the system's own bootstraps.
     *
     * <p>Package-private, and the only callers are {@code GrantLifecycleListener}'s two: the
     * founder's {@code CLAN_ADMIN} at clan creation and the {@code CLAN_VIEW} a join approval
     * carries (doc 04 §6). Both are performed by the system in response to an action somebody was
     * already authorized to take, and neither has an actor whose own grants could cover them — the
     * founder's is precisely the first grant in the clan.
     *
     * <p>It is deliberately not public. An escalation check that can be skipped from outside the
     * context is not an escalation check.
     */
    PermissionGrant issueSystemGrant(
            ClanId clanId,
            UserId grantee,
            Permission permission,
            PermissionScope scope,
            UserId grantedBy,
            AuditAction action) {

        Instant now = clock.instant();
        Optional<PermissionGrant> existing =
                grants.findEffectiveDuplicate(clanId, grantee, permission, scope, now);
        if (existing.isPresent()) {
            return existing.get();
        }
        PermissionGrant issued =
                grants.save(
                        PermissionGrant.issue(
                                ids.newId(),
                                clanId,
                                grantee,
                                permission,
                                scope,
                                grantedBy,
                                null,
                                now));
        grantCache.invalidate(clanId, grantee);
        auditWriter.write(
                AuditRecord.inClan(
                        action,
                        clanId,
                        AuditRecord.TARGET_PERMISSION_GRANT,
                        issued.id(),
                        grantedBy));
        return issued;
    }

    /**
     * Revokes everything one person holds in one clan — what a membership ending means (B3.12).
     *
     * <p>Grants are already ineffective without a membership (doc 04 §1.6), so this changes no
     * decision. It changes what the permissions screen says, and it means somebody re-admitted to a
     * clan comes back with nothing rather than with whatever they had when they were removed.
     */
    int revokeAllFor(ClanId clanId, UserId member, UserId actor) {
        Instant now = clock.instant();
        List<PermissionGrant> held = grants.findEffectiveFor(clanId, member, now);
        for (PermissionGrant grant : held) {
            grants.save(grant.revoke(actor, now));
            auditWriter.write(
                    AuditRecord.inClan(
                            AuditAction.PERMISSION_REVOKED,
                            clanId,
                            AuditRecord.TARGET_PERMISSION_GRANT,
                            grant.id(),
                            actor));
        }
        grantCache.invalidate(clanId, member);
        return held.size();
    }

    /** Whether {@code user} is the clan's only remaining administrator (I-M3). */
    boolean isFinalAdministrator(ClanId clanId, UserId user) {
        List<PermissionGrant> admins =
                grants.findEffectiveByPermission(clanId, Permission.CLAN_ADMIN, clock.instant());
        Set<UserId> holders = new HashSet<>();
        for (PermissionGrant grant : admins) {
            // Only holders who are still members count: a grant belonging to somebody who has left
            // confers nothing, and treating it as protection would strand the clan quietly.
            if (clans.isActiveMember(clanId, grant.granteeUserId())) {
                holders.add(grant.granteeUserId());
            }
        }
        return holders.size() == 1 && holders.contains(user);
    }

    // --- the four conditions, and the plumbing around them --------------------------------------

    /**
     * Whether the actor holds {@code permission} at some scope covering {@code scope} — condition 2.
     *
     * <p>Asks {@link PermissionResolver#scopesHolding}, which has already expanded implications, and
     * then the same covering relation everything else uses.
     */
    private boolean holdsCovering(
            UserId actor, ClanId clanId, Permission permission, PermissionScope scope) {

        return resolver.scopesHolding(actor, clanId, permission).stream()
                .anyMatch(held -> resolver.scopeCovers(clanId, held, scope));
    }

    /**
     * The gate on reading the permissions screen: {@code ROLE_MANAGE} anywhere in the clan.
     *
     * <p>Scope-blind on purpose. Read filtering is clan-wide in v1 (doc 04 §5.5) — the scopes
     * constrain writes — and a role manager who could not see the clan's grant table could not tell
     * whether the permission they are about to hand out is already held.
     */
    private void requireRoleManagerSomewhere(UserId caller, ClanId clanId) {
        if (!resolver.permissionsOf(caller, clanId).contains(Permission.ROLE_MANAGE)) {
            // Membership first: a non-member must not learn the clan exists (doc 04 §7).
            if (!clans.isActiveMember(clanId, caller)) {
                throw new NotFoundException(
                        ErrorCode.CLAN_NOT_FOUND,
                        "That clan does not exist, or you are not a member of it.");
            }
            throw new PermissionDeniedException(
                    "Only somebody who manages roles in this clan can see its permissions.");
        }
    }

    private void requireNotFinalAdministrator(ClanId clanId, PermissionGrant grant) {
        if (isFinalAdministrator(clanId, grant.granteeUserId())) {
            throw new PermissionDeniedException(
                    ErrorCode.LAST_ADMIN_PROTECTED,
                    "This clan would be left with nobody able to administer it. Make somebody else"
                            + " an administrator first.");
        }
    }

    private PermissionScope parseScope(ClanId clanId, GrantCommand command) {
        ScopeType type;
        try {
            type = ScopeType.valueOf(command.scopeType() == null ? "" : command.scopeType().trim());
        } catch (IllegalArgumentException unknown) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED, "No such scope: " + command.scopeType());
        }
        if (type.requiresReference() == (command.scopeRefId() == null)) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED,
                    type == ScopeType.CLAN
                            ? "A clan-wide permission names no branch."
                            : "Choose a branch for this permission.");
        }
        if (type == ScopeType.SUBTREE) {
            // Live from Phase 4 (B4.8). The guard that used to answer 422 SUBTREE_SCOPE_UNAVAILABLE
            // is gone; what replaces it is the same rule branch scope has always had — resolve the
            // reference before storing it, because a grant pointing at nobody covers nobody and is a
            // row an administrator cannot explain.
            if (!ancestry.isAvailable()) {
                throw new DomainRuleViolation(
                        ErrorCode.SUBTREE_SCOPE_UNAVAILABLE,
                        "Scoping a permission to one person's descendants needs the family tree,"
                                + " which this deployment cannot reach.");
            }
            if (!ancestry.personExists(clanId, command.scopeRefId())) {
                throw new NotFoundException(ErrorCode.PERSON_NOT_FOUND, "No such person.");
            }
            return PermissionScope.subtree(command.scopeRefId());
        }
        if (type == ScopeType.BRANCH) {
            BranchId branchId = BranchId.of(command.scopeRefId());
            // Resolved rather than trusted. A grant scoped to another clan's branch would cover
            // nothing, and a row nobody can explain is worse than a refusal.
            if (clans.branch(clanId, branchId).isEmpty()) {
                throw new NotFoundException(ErrorCode.BRANCH_NOT_FOUND, "No such branch.");
            }
            return PermissionScope.branch(branchId);
        }
        return PermissionScope.clan();
    }

    /** I-G2 and the catalogue's scope table, before any permission question is asked. */
    private static void requireGrantableCombination(Permission permission, PermissionScope scope) {
        if (!permission.isValidAt(scope.type())) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED,
                    permission + " cannot be scoped to a " + scope.type().name().toLowerCase() + ".");
        }
    }

    private static PermissionDeniedException escalationBlocked(String detail) {
        return new PermissionDeniedException(ErrorCode.PERMISSION_ESCALATION_BLOCKED, detail);
    }

    /** Clan scope, then every branch in path order — the picker's list, top down. */
    private List<GrantableView.GrantableScope> candidateScopes(ClanId clanId) {
        List<GrantableView.GrantableScope> candidates = new ArrayList<>();
        candidates.add(
                new GrantableView.GrantableScope(ScopeType.CLAN.name(), null, null, 0));
        clans.branches(clanId).stream()
                .filter(branch -> !branch.archived())
                .forEach(
                        branch ->
                                candidates.add(
                                        new GrantableView.GrantableScope(
                                                ScopeType.BRANCH.name(),
                                                branch.id().value(),
                                                branch.name(),
                                                branch.depth())));
        return candidates;
    }

}
