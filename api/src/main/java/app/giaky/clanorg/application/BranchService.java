package app.giaky.clanorg.application;

import app.giaky.authorization.domain.Permission;
import app.giaky.authorization.domain.PermissionResolver;
import app.giaky.authorization.domain.Target;
import app.giaky.clanorg.application.event.BranchArchived;
import app.giaky.clanorg.application.event.BranchCreated;
import app.giaky.clanorg.application.event.BranchMoved;
import app.giaky.clanorg.domain.Branch;
import app.giaky.clanorg.domain.BranchCode;
import app.giaky.clanorg.domain.BranchPathService;
import app.giaky.clanorg.domain.BranchRepository;
import app.giaky.clanorg.domain.Clan;
import app.giaky.clanorg.domain.ClanMembershipRepository;
import app.giaky.clanorg.domain.ClanRepository;
import app.giaky.clanorg.domain.CodeGenerator;
import app.giaky.clanorg.domain.MaterializedPath;
import app.giaky.common.audit.AuditAction;
import app.giaky.common.audit.AuditRecord;
import app.giaky.common.audit.AuditWriter;
import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.IdGenerator;
import app.giaky.common.types.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The branch tree: create, rename, move, archive, and read as a tree (FR-C4…FR-C7).
 *
 * <p>Every illegal structure this phase can be asked for is refused here or in {@link Branch}, and
 * the two of them together are what I-C3, I-C6 and I-C7 mean in practice: no cycles, no archiving
 * over live children, no branch deeper than ten.
 */
@Service
public class BranchService {

    private static final Logger log = LoggerFactory.getLogger(BranchService.class);

    private final BranchRepository branches;
    private final ClanMembershipRepository memberships;
    private final ClanRepository clans;
    private final BranchPathService paths;
    private final CodeGenerator codeGenerator;
    private final PermissionResolver resolver;
    private final AuditWriter auditWriter;
    private final ApplicationEventPublisher events;
    private final IdGenerator ids;
    private final Clock clock;

    public BranchService(
            BranchRepository branches,
            ClanMembershipRepository memberships,
            ClanRepository clans,
            BranchPathService paths,
            CodeGenerator codeGenerator,
            PermissionResolver resolver,
            AuditWriter auditWriter,
            ApplicationEventPublisher events,
            IdGenerator ids,
            Clock clock) {
        this.branches = branches;
        this.memberships = memberships;
        this.clans = clans;
        this.paths = paths;
        this.codeGenerator = codeGenerator;
        this.resolver = resolver;
        this.auditWriter = auditWriter;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * The clan's whole branch structure, nested, with member counts (doc 05 §4.4).
     *
     * <p>Two queries for any size of tree: the branches, and the per-branch member counts. Assembly
     * is in memory because the depth cap bounds the result to something a page can render anyway.
     */
    @Transactional(readOnly = true)
    public List<BranchView> tree(UserId caller, ClanId clanId) {
        resolver.requireMembership(caller, clanId);
        return assembleTree(clanId);
    }

    @Transactional(readOnly = true)
    public BranchView detail(UserId caller, ClanId clanId, BranchId branchId) {
        resolver.requireMembership(caller, clanId);
        Branch branch = require(clanId, branchId);
        return BranchView.of(branch, memberCounts(clanId).getOrDefault(branchId, 0L), List.of());
    }

    /**
     * Adds a branch (FR-C4).
     *
     * <p>A null parent means the clan's root branch. That is not a convenience: a branch with no
     * parent would be a second root, which I-C2 forbids and {@code ux_branches_one_root} refuses.
     *
     * <p>{@code ROLE_MANAGE} on the parent, not {@code CLAN_ADMIN} (doc 05 §4.4). Somebody trusted
     * to hand out permissions inside a chi is trusted to divide that chi further — and because the
     * check names the parent, dividing a chi they do not manage is refused with the branch they do
     * hold named in the message. An administrator passes by implication, at clan scope.
     */
    @Transactional
    public BranchView create(UserId caller, ClanId clanId, CreateBranchCommand command) {
        Clan clan = requireClan(clanId);
        BranchId parentId =
                command.parentBranchId() == null ? clan.rootBranchId() : command.parentBranchId();
        var authorizing =
                resolver.require(
                        caller, Permission.ROLE_MANAGE, Target.branch(clanId, parentId));

        Instant now = clock.instant();
        Branch parent = require(clanId, parentId);
        if (parent.isArchived()) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED,
                    "That branch is archived, so nothing new can be added under it.");
        }
        if (parent.childDepth() > Branch.MAX_DEPTH) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED,
                    "Branches can be nested "
                            + Branch.MAX_DEPTH
                            + " levels deep at most, and this one is already at the limit.");
        }

        BranchCode code = codeGenerator.newBranchCode(command.name(), branches::isBranchCodeTaken);
        Branch created =
                branches.save(
                        Branch.createUnder(
                                freeBranchId(clanId, parent),
                                parent,
                                code,
                                command.name(),
                                command.description(),
                                caller,
                                now));

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.BRANCH_CREATED,
                                clanId,
                                AuditRecord.TARGET_BRANCH,
                                created.id().value(),
                                caller)
                        .authorizedBy(authorizing.authorizingGrantId()));
        events.publishEvent(
                new BranchCreated(clanId, created.id(), parent.id(), caller, now));

        return BranchView.of(created, 0L, List.of());
    }

    /** Rename or re-describe (FR-C5). */
    @Transactional
    public BranchView update(
            UserId caller, ClanId clanId, BranchId branchId, UpdateBranchCommand command) {

        var authorizing = resolver.require(caller, Permission.CLAN_ADMIN, Target.clan(clanId));
        Branch branch = require(clanId, branchId);
        ClanService.requireVersion(branch.version(), command.expectedVersion());

        Branch updated =
                branches.save(
                        branch.update(
                                command.name(), command.description(), caller, clock.instant()));

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.BRANCH_UPDATED,
                                clanId,
                                AuditRecord.TARGET_BRANCH,
                                branchId.value(),
                                caller)
                        .authorizedBy(authorizing.authorizingGrantId()));

        return BranchView.of(updated, memberCounts(clanId).getOrDefault(branchId, 0L), List.of());
    }

    /**
     * Names the person a branch descends from (I-C5).
     *
     * <p>Called by Lineage when a node is created with {@code placement.type = BRANCH_ROOT}, which
     * is the only way a branch acquires one in Phase 4. A published application service rather than
     * a shared repository, because Lineage may not touch {@code clanorg.domain} (doc 02 §7 rule 1).
     *
     * <p><b>{@code TREE_EDIT} on the branch, not {@code CLAN_ADMIN}.</b> Naming the ancestor a chi
     * descends from is a statement about the family tree, and the person making it is the person who
     * just created that ancestor — an editor of that branch, by construction. Requiring clan
     * administration would make the placement unusable by the people it exists for.
     *
     * <p>Refused when the branch already has a root person: replacing one silently re-scopes every
     * {@code SUBTREE} grant that reaches this branch through it (doc 04 §5.4), and that is not a
     * side effect of adding a person.
     *
     * @throws DomainRuleViolation the branch already names a different root person
     */
    @Transactional
    public BranchView assignRootPerson(
            UserId caller, ClanId clanId, BranchId branchId, java.util.UUID personId) {

        var authorizing =
                resolver.require(caller, Permission.TREE_EDIT, Target.branch(clanId, branchId));
        Branch branch = require(clanId, branchId);

        if (branch.branchRootPersonId() != null
                && !branch.branchRootPersonId().equals(personId)) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED,
                    "This branch already descends from somebody. Clear that first if the book says"
                            + " otherwise.");
        }

        Branch updated =
                branches.save(branch.withRootPerson(personId, caller, clock.instant()));

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.BRANCH_UPDATED,
                                clanId,
                                AuditRecord.TARGET_BRANCH,
                                branchId.value(),
                                caller)
                        .authorizedBy(authorizing.authorizingGrantId()));

        return BranchView.of(updated, memberCounts(clanId).getOrDefault(branchId, 0L), List.of());
    }

    /**
     * Re-parents a branch and everything under it (FR-C6).
     *
     * <p>The risky one (phase 2 doc §8). {@link BranchPathService#move} does both halves inside this
     * transaction, so there is no moment at which a reader could see a subtree whose paths no longer
     * describe the tree.
     *
     * @throws DomainRuleViolation the move would create a cycle, pass the depth cap, or land under
     *     an archived branch
     */
    @Transactional
    public BranchView move(UserId caller, ClanId clanId, BranchId branchId, BranchId newParentId) {
        var authorizing = resolver.require(caller, Permission.CLAN_ADMIN, Target.clan(clanId));
        Branch branch = require(clanId, branchId);
        Branch newParent = require(clanId, newParentId);
        Instant now = clock.instant();

        BranchPathService.MoveResult result;
        try {
            result = paths.move(branch, newParent, caller, now);
        } catch (IllegalArgumentException illegal) {
            // The domain refuses in the language of invariants; the API answers in the language of
            // doc 05 §2. CYCLE_DETECTED is the honest code for the case that actually matters —
            // a branch asked to become its own descendant.
            throw new DomainRuleViolation(
                    newParent.path().isWithin(branch.path())
                            ? ErrorCode.CYCLE_DETECTED
                            : ErrorCode.VALIDATION_FAILED,
                    illegal.getMessage());
        }

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.BRANCH_MOVED,
                                clanId,
                                AuditRecord.TARGET_BRANCH,
                                branchId.value(),
                                caller)
                        .authorizedBy(authorizing.authorizingGrantId()));
        events.publishEvent(
                new BranchMoved(
                        clanId,
                        branchId,
                        branch.parentBranchId(),
                        newParentId,
                        result.descendantsRewritten(),
                        caller,
                        now));

        log.info(
                "Branch moved clanId={} branchId={} newParentId={} descendantsRewritten={}",
                clanId,
                branchId,
                newParentId,
                result.descendantsRewritten());

        return BranchView.of(
                result.branch(), memberCounts(clanId).getOrDefault(branchId, 0L), List.of());
    }

    /**
     * Retires a branch (FR-C7).
     *
     * <p>I-C6: refused while it still has live children. Archiving a branch whose children are still
     * in use would hide them from the tree without archiving them, which is worse than either
     * outcome the caller was choosing between.
     */
    @Transactional
    public BranchView archive(UserId caller, ClanId clanId, BranchId branchId) {
        var authorizing = resolver.require(caller, Permission.CLAN_ADMIN, Target.clan(clanId));
        Branch branch = require(clanId, branchId);

        if (branch.isRoot()) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED,
                    "The root branch is the clan itself and cannot be archived.");
        }
        long liveChildren = branches.countLiveChildren(clanId, branchId);
        if (liveChildren > 0) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED,
                    "Archive or move its "
                            + liveChildren
                            + " sub-branches first.");
        }

        Instant now = clock.instant();
        Branch archived = branches.save(branch.archive(caller, now));

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.BRANCH_ARCHIVED,
                                clanId,
                                AuditRecord.TARGET_BRANCH,
                                branchId.value(),
                                caller)
                        .authorizedBy(authorizing.authorizingGrantId()));
        events.publishEvent(new BranchArchived(clanId, branchId, caller, now));

        return BranchView.of(
                archived, memberCounts(clanId).getOrDefault(branchId, 0L), List.of());
    }

    /**
     * The clan, or a 404 that discloses nothing.
     *
     * <p>Loaded before the permission check here, unlike everywhere else, because the check needs
     * the clan's root branch id to know what "under no parent" means. A caller who is not a member
     * still gets the same 404 either way (doc 04 §7).
     */
    private Clan requireClan(ClanId clanId) {
        return clans.findById(clanId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        ErrorCode.CLAN_NOT_FOUND,
                                        "That clan does not exist, or you are not a member of it."));
    }

    /** The branch, or a 404 that says nothing about whether it exists in some other clan. */
    private Branch require(ClanId clanId, BranchId branchId) {
        if (branchId == null) {
            throw new NotFoundException(ErrorCode.BRANCH_NOT_FOUND, "No such branch.");
        }
        return branches
                .findById(clanId, branchId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        ErrorCode.BRANCH_NOT_FOUND, "No such branch."));
    }

    /**
     * An id whose eight-character path segment is not already used in this clan.
     *
     * <p>{@code ux_branches_path} makes a collision an insert failure rather than a mis-scoped read
     * (see V4). The odds of needing a second draw are on the order of one in a hundred million for a
     * clan with a thousand branches; drawing again costs one indexed lookup and removes the case
     * entirely.
     */
    private BranchId freeBranchId(ClanId clanId, Branch parent) {
        for (int attempt = 0; attempt < 5; attempt++) {
            BranchId candidate = BranchId.of(ids.newId());
            MaterializedPath path = BranchPathService.childPathOf(parent, candidate);
            if (!branches.isPathTaken(clanId, path)) {
                return candidate;
            }
            log.warn("Branch path segment collision in clanId={}, drawing another id", clanId);
        }
        throw new IllegalStateException(
                "could not find a free branch path segment in clan " + clanId);
    }

    private Map<BranchId, Long> memberCounts(ClanId clanId) {
        Map<BranchId, Long> counts = new HashMap<>();
        for (ClanMembershipRepository.BranchMemberCount row :
                memberships.countActiveMembersPerBranch(clanId)) {
            counts.put(row.branchId(), row.memberCount());
        }
        return counts;
    }

    /**
     * Turns the clan's flat branch list into the nested shape the client renders.
     *
     * <p>The repository returns them ordered by path, which is depth-first order — a branch always
     * precedes its own descendants — so one pass over the list is enough to attach every child to a
     * parent that has already been seen.
     */
    private List<BranchView> assembleTree(ClanId clanId) {
        List<Branch> all = branches.findAllByClan(clanId);
        Map<BranchId, Long> counts = memberCounts(clanId);

        Map<BranchId, List<Branch>> childrenOf = new HashMap<>();
        List<Branch> roots = new ArrayList<>();
        for (Branch branch : all) {
            if (branch.isRoot()) {
                roots.add(branch);
            } else {
                childrenOf.computeIfAbsent(branch.parentBranchId(), key -> new ArrayList<>())
                        .add(branch);
            }
        }
        return roots.stream().map(root -> toView(root, childrenOf, counts)).toList();
    }

    private BranchView toView(
            Branch branch, Map<BranchId, List<Branch>> childrenOf, Map<BranchId, Long> counts) {

        List<BranchView> children =
                childrenOf.getOrDefault(branch.id(), List.of()).stream()
                        .map(child -> toView(child, childrenOf, counts))
                        .toList();
        return BranchView.of(branch, counts.getOrDefault(branch.id(), 0L), children);
    }
}
