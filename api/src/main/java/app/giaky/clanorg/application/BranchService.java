package app.giaky.clanorg.application;

import app.giaky.clanorg.application.event.BranchArchived;
import app.giaky.clanorg.application.event.BranchCreated;
import app.giaky.clanorg.application.event.BranchMoved;
import app.giaky.clanorg.domain.Branch;
import app.giaky.clanorg.domain.BranchCode;
import app.giaky.clanorg.domain.BranchPathService;
import app.giaky.clanorg.domain.BranchRepository;
import app.giaky.clanorg.domain.Clan;
import app.giaky.clanorg.domain.ClanMembershipRepository;
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
    private final BranchPathService paths;
    private final CodeGenerator codeGenerator;
    private final InterimClanAccessCheck access;
    private final AuditWriter auditWriter;
    private final ApplicationEventPublisher events;
    private final IdGenerator ids;
    private final Clock clock;

    public BranchService(
            BranchRepository branches,
            ClanMembershipRepository memberships,
            BranchPathService paths,
            CodeGenerator codeGenerator,
            InterimClanAccessCheck access,
            AuditWriter auditWriter,
            ApplicationEventPublisher events,
            IdGenerator ids,
            Clock clock) {
        this.branches = branches;
        this.memberships = memberships;
        this.paths = paths;
        this.codeGenerator = codeGenerator;
        this.access = access;
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
        access.requireVisible(caller, clanId);
        return assembleTree(clanId);
    }

    @Transactional(readOnly = true)
    public BranchView detail(UserId caller, ClanId clanId, BranchId branchId) {
        access.requireVisible(caller, clanId);
        Branch branch = require(clanId, branchId);
        return BranchView.of(branch, memberCounts(clanId).getOrDefault(branchId, 0L), List.of());
    }

    /**
     * Adds a branch (FR-C4).
     *
     * <p>A null parent means the clan's root branch. That is not a convenience: a branch with no
     * parent would be a second root, which I-C2 forbids and {@code ux_branches_one_root} refuses.
     */
    @Transactional
    public BranchView create(UserId caller, ClanId clanId, CreateBranchCommand command) {
        Clan clan = access.requireAdministrator(caller, clanId);
        Instant now = clock.instant();

        BranchId parentId =
                command.parentBranchId() == null ? clan.rootBranchId() : command.parentBranchId();
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
                        caller));
        events.publishEvent(
                new BranchCreated(clanId, created.id(), parent.id(), caller, now));

        return BranchView.of(created, 0L, List.of());
    }

    /** Rename or re-describe (FR-C5). */
    @Transactional
    public BranchView update(
            UserId caller, ClanId clanId, BranchId branchId, UpdateBranchCommand command) {

        access.requireAdministrator(caller, clanId);
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
                        caller));

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
        access.requireAdministrator(caller, clanId);
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
                        caller));
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
        access.requireAdministrator(caller, clanId);
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
                        caller));
        events.publishEvent(new BranchArchived(clanId, branchId, caller, now));

        return BranchView.of(
                archived, memberCounts(clanId).getOrDefault(branchId, 0L), List.of());
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
