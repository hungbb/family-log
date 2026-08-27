package app.giaky.clanorg.application;

import app.giaky.clanorg.application.event.ClanCreated;
import app.giaky.clanorg.domain.Branch;
import app.giaky.clanorg.domain.BranchCode;
import app.giaky.clanorg.domain.BranchRepository;
import app.giaky.clanorg.domain.Clan;
import app.giaky.clanorg.domain.ClanCode;
import app.giaky.clanorg.domain.ClanJoinRequestRepository;
import app.giaky.clanorg.domain.ClanMembership;
import app.giaky.clanorg.domain.ClanMembershipRepository;
import app.giaky.clanorg.domain.ClanRepository;
import app.giaky.clanorg.domain.CodeGenerator;
import app.giaky.common.audit.AuditAction;
import app.giaky.common.audit.AuditRecord;
import app.giaky.common.audit.AuditWriter;
import app.giaky.common.error.ConflictException;
import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.IdGenerator;
import app.giaky.common.types.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clans: found one, list the ones you are in, read, edit, delete, and look one up by code
 * (FR-C1…FR-C3, FR-C8).
 *
 * <p>Access is decided by {@link InterimClanAccessCheck} and nowhere else — no method here tests a
 * membership or a creator id of its own. That is what makes Phase 3's replacement a one-file change.
 */
@Service
public class ClanService {

    private static final Logger log = LoggerFactory.getLogger(ClanService.class);

    private final ClanRepository clans;
    private final BranchRepository branches;
    private final ClanMembershipRepository memberships;
    private final ClanJoinRequestRepository joinRequests;
    private final CodeGenerator codeGenerator;
    private final InterimClanAccessCheck access;
    private final AuditWriter auditWriter;
    private final ApplicationEventPublisher events;
    private final IdGenerator ids;
    private final Clock clock;

    public ClanService(
            ClanRepository clans,
            BranchRepository branches,
            ClanMembershipRepository memberships,
            ClanJoinRequestRepository joinRequests,
            CodeGenerator codeGenerator,
            InterimClanAccessCheck access,
            AuditWriter auditWriter,
            ApplicationEventPublisher events,
            IdGenerator ids,
            Clock clock) {
        this.clans = clans;
        this.branches = branches;
        this.memberships = memberships;
        this.joinRequests = joinRequests;
        this.codeGenerator = codeGenerator;
        this.access = access;
        this.auditWriter = auditWriter;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * Founds a clan (FR-C1): the clan, its root branch, and the founder's membership, in one
     * transaction.
     *
     * <p>All three or none. A clan without a root branch violates I-C2 and has no place to put
     * anybody; a clan whose founder is not a member is a clan its own creator gets a 404 from. There
     * is no partial state here worth being able to reach.
     *
     * <p>The {@code ClanCreated} event is what Phase 3 will consume to issue the founder's
     * {@code CLAN_ADMIN} grant (doc 04 §6). Publishing it now, with nothing subscribed, is what makes
     * that a listener rather than an edit to this method.
     */
    @Transactional
    public ClanView create(UserId caller, CreateClanCommand command) {
        Instant now = clock.instant();

        ClanCode clanCode = codeGenerator.newClanCode(command.codeSource(), clans::isClanCodeTaken);
        ClanId clanId = ClanId.of(ids.newId());
        Clan clan =
                clans.save(
                        Clan.create(
                                clanId,
                                clanCode,
                                command.name(),
                                command.description(),
                                command.originPlace(),
                                caller,
                                now));

        Branch root = createRootBranch(clan, caller, now);
        clan = clans.save(clan.withRootBranch(root.id()));

        // The founder is a member from the first moment, and their home branch is the root: the
        // clan switcher, the member list and every read path treat them like anybody else, which
        // means none of those paths need a special case for the person who made the clan.
        memberships.save(
                ClanMembership.grant(
                        ids.newId(), clanId, caller, root.id(), caller, now));

        auditWriter.write(
                AuditRecord.inClan(
                        AuditAction.CLAN_CREATED,
                        clanId,
                        AuditRecord.TARGET_CLAN,
                        clanId.value(),
                        caller));
        events.publishEvent(new ClanCreated(clanId, root.id(), caller, now));

        log.info("Clan created clanId={} clanCode={} createdBy={}", clanId, clanCode, caller);
        return ClanView.of(clan, 1L, 1L, access.permissionsOf(caller, clan));
    }

    /** The clans the caller is an active member of, newest membership first (FR-C8). */
    @Transactional(readOnly = true)
    public List<ClanSummaryView> listForUser(UserId caller) {
        List<ClanMembership> mine = memberships.findActiveByUser(caller);
        if (mine.isEmpty()) {
            return List.of();
        }
        Map<ClanId, Clan> byId =
                clans.findAllById(mine.stream().map(ClanMembership::clanId).toList()).stream()
                        .collect(java.util.stream.Collectors.toMap(Clan::id, Function.identity()));

        return mine.stream()
                // A membership whose clan was soft-deleted simply drops out; the switcher should
                // show what a person can open, not what they used to be able to.
                .filter(membership -> byId.containsKey(membership.clanId()))
                .map(
                        membership -> {
                            Clan clan = byId.get(membership.clanId());
                            return ClanSummaryView.of(
                                    clan, membership, access.permissionsOf(caller, clan));
                        })
                .sorted(Comparator.comparing(ClanSummaryView::name))
                .toList();
    }

    @Transactional(readOnly = true)
    public ClanView detail(UserId caller, ClanId clanId) {
        Clan clan = access.requireVisible(caller, clanId);
        return ClanView.of(
                clan,
                memberships.findAllActive(clanId).size(),
                branches.findAllByClan(clanId).size(),
                access.permissionsOf(caller, clan));
    }

    /**
     * Edits the descriptive fields (FR-C3).
     *
     * @throws app.giaky.common.error.ConflictException the clan changed since the caller read it
     */
    @Transactional
    public ClanView update(UserId caller, ClanId clanId, UpdateClanCommand command) {
        Clan clan = access.requireAdministrator(caller, clanId);
        requireVersion(clan.version(), command.expectedVersion());

        Clan updated =
                clans.save(
                        clan.update(
                                command.name(),
                                command.description(),
                                command.originPlace(),
                                command.coverMediaId(),
                                caller,
                                clock.instant()));

        auditWriter.write(
                AuditRecord.inClan(
                        AuditAction.CLAN_UPDATED,
                        clanId,
                        AuditRecord.TARGET_CLAN,
                        clanId.value(),
                        caller));

        return ClanView.of(
                updated,
                memberships.findAllActive(clanId).size(),
                branches.findAllByClan(clanId).size(),
                access.permissionsOf(caller, updated));
    }

    /**
     * Soft-deletes a clan (doc 05 §4.3): thirty days to change your mind.
     *
     * <p>Nothing cascades. Branches, memberships and requests keep their rows and simply become
     * unreachable, because every read starts from a clan that is now invisible — and because a
     * restore that had to rebuild them would not be a restore.
     */
    @Transactional
    public void softDelete(UserId caller, ClanId clanId) {
        Clan clan = access.requireAdministrator(caller, clanId);
        clans.save(clan.softDelete(caller, clock.instant()));

        auditWriter.write(
                AuditRecord.inClan(
                        AuditAction.CLAN_DELETED,
                        clanId,
                        AuditRecord.TARGET_CLAN,
                        clanId.value(),
                        caller));
        log.info("Clan soft-deleted clanId={} by={}", clanId, caller);
    }

    /**
     * The card shown to somebody holding a code, before they ask to join (FR-J1).
     *
     * <p>Accepts a clan code or a branch code — the two are shaped identically and a person pasting
     * one from a message has no reason to know which they were given.
     *
     * <p>Answers {@code 400 INVALID_CLAN_CODE} for a malformed code and {@code 404} for a
     * well-formed one that matches nothing. The distinction is safe: the first is about the string
     * the caller typed, the second discloses only that <i>some</i> code in the space is unused.
     */
    @Transactional(readOnly = true)
    public ClanCardView lookupByCode(UserId caller, String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (!ClanCode.isValid(code)) {
            throw new DomainRuleViolation(
                    ErrorCode.INVALID_CLAN_CODE, "That does not look like a join code.");
        }

        Optional<Branch> branch = branches.findByBranchCode(BranchCode.of(code));
        Clan clan =
                branch.map(Branch::clanId)
                        .flatMap(clans::findById)
                        .or(() -> clans.findByClanCode(ClanCode.of(code)))
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.CLAN_NOT_FOUND,
                                                "No clan uses that code."));

        BranchId targetBranchId = branch.map(Branch::id).orElse(null);
        boolean member = access.isMember(caller, clan.id());
        boolean pending =
                joinRequests.findPending(clan.id(), targetBranchId, caller).isPresent();

        return new ClanCardView(
                clan.clanCode().value(),
                clan.name(),
                clan.originPlace(),
                memberships.findAllActive(clan.id()).size(),
                targetBranchId == null ? null : targetBranchId.value(),
                branch.map(Branch::name).orElse(null),
                member,
                pending);
    }

    /**
     * The root branch a new clan starts with.
     *
     * <p>Named after the clan, because "the whole family" is what it means and asking for a second
     * name during creation would be a question with one sensible answer.
     */
    private Branch createRootBranch(Clan clan, UserId caller, Instant now) {
        BranchCode branchCode =
                codeGenerator.newBranchCode(clan.name(), branches::isBranchCodeTaken);
        BranchId branchId = BranchId.of(ids.newId());
        return branches.save(
                Branch.createRoot(
                        branchId, clan.id(), branchCode, clan.name(), null, caller, now));
    }

    /**
     * The optimistic-lock check, made explicit rather than left to JPA.
     *
     * <p>JPA would catch a stale write at flush time and the handler would still answer 409. Doing
     * it here means the caller is refused before anything else in the transaction has happened, and
     * the message can say what actually went wrong.
     */
    static void requireVersion(long actual, long expected) {
        if (actual != expected) {
            throw new ConflictException(
                    ErrorCode.VERSION_CONFLICT,
                    "This changed since you loaded it. Reload and try again.");
        }
    }
}
