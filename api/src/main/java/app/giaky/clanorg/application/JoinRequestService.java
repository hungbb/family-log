package app.giaky.clanorg.application;

import app.giaky.authorization.domain.AccessDecision;
import app.giaky.authorization.domain.Permission;
import app.giaky.authorization.domain.PermissionResolver;
import app.giaky.authorization.domain.Target;
import app.giaky.clanorg.application.event.JoinRequestDecided;
import app.giaky.clanorg.application.event.JoinRequestSubmitted;
import app.giaky.clanorg.application.event.MembershipGranted;
import app.giaky.clanorg.domain.Branch;
import app.giaky.clanorg.domain.BranchCode;
import app.giaky.clanorg.domain.BranchRepository;
import app.giaky.clanorg.domain.Clan;
import app.giaky.clanorg.domain.ClanCode;
import app.giaky.clanorg.domain.ClanJoinRequest;
import app.giaky.clanorg.domain.ClanJoinRequestRepository;
import app.giaky.clanorg.domain.ClanMembership;
import app.giaky.clanorg.domain.ClanMembershipRepository;
import app.giaky.clanorg.domain.ClanRepository;
import app.giaky.clanorg.domain.JoinRequestStatus;
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
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Joining a clan, from both sides: submit, cancel, and the approver's inbox (FR-J1…FR-J5).
 *
 * <p>The one method here that has to be exactly right is {@link #approve}: it creates a membership,
 * and doing that twice would give one person two memberships in a clan, which every count and every
 * permission check downstream would then be wrong about. Three things stop it — the state machine in
 * {@link ClanJoinRequest} (I-J4), the read-before-write below, and {@code ux_membership_active}
 * (I-M1) underneath both, which is the one that survives two requests arriving at once.
 */
@Service
public class JoinRequestService {

    private static final Logger log = LoggerFactory.getLogger(JoinRequestService.class);

    /** How many of a caller's own requests {@code GET /me/join-requests} returns. */
    public static final int MY_REQUESTS_LIMIT = 50;

    /** How many requests one sweep closes. Bounded so a long-neglected deployment cannot stall. */
    public static final int EXPIRY_BATCH_SIZE = 500;

    private final ClanJoinRequestRepository requests;
    private final ClanRepository clans;
    private final BranchRepository branches;
    private final ClanMembershipRepository memberships;
    private final PermissionResolver resolver;
    private final UserDirectory users;
    private final AuditWriter auditWriter;
    private final ApplicationEventPublisher events;
    private final IdGenerator ids;
    private final Clock clock;

    public JoinRequestService(
            ClanJoinRequestRepository requests,
            ClanRepository clans,
            BranchRepository branches,
            ClanMembershipRepository memberships,
            PermissionResolver resolver,
            UserDirectory users,
            AuditWriter auditWriter,
            ApplicationEventPublisher events,
            IdGenerator ids,
            Clock clock) {
        this.requests = requests;
        this.clans = clans;
        this.branches = branches;
        this.memberships = memberships;
        this.resolver = resolver;
        this.users = users;
        this.auditWriter = auditWriter;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * Asks to join, by clan code or by branch code (FR-J1).
     *
     * @throws ConflictException the caller is already a member (I-J2) or already has a pending
     *     request for the same target (I-J1)
     */
    @Transactional
    public JoinRequestView submit(UserId caller, SubmitJoinRequestCommand command) {
        if (!command.hasExactlyOneCode()) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED, "Give exactly one code: a clan code or a branch code.");
        }

        boolean byBranch = command.branchCode() != null && !command.branchCode().isBlank();
        String rawCode = byBranch ? command.branchCode() : command.clanCode();
        if (!ClanCode.isValid(rawCode)) {
            throw new DomainRuleViolation(
                    ErrorCode.INVALID_CLAN_CODE, "That does not look like a join code.");
        }

        Branch targetBranch =
                byBranch
                        ? branches
                                .findByBranchCode(BranchCode.of(rawCode))
                                .orElseThrow(JoinRequestService::noSuchCode)
                        : null;
        Clan clan =
                targetBranch == null
                        ? clans.findByClanCode(ClanCode.of(rawCode))
                                .orElseThrow(JoinRequestService::noSuchCode)
                        : clans.findById(targetBranch.clanId()).orElseThrow(JoinRequestService::noSuchCode);

        if (targetBranch != null && targetBranch.isArchived()) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED, "That branch is archived and is not accepting members.");
        }

        // I-J2. A member who asks to join again is not an error worth a stack trace, but it is an
        // error: their client is out of date, and creating the request would give an approver a
        // decision that cannot be carried out.
        if (resolver.isMember(caller, clan.id())) {
            throw new ConflictException(
                    ErrorCode.MEMBERSHIP_EXISTS, "You are already a member of this clan.");
        }

        BranchId targetBranchId = targetBranch == null ? null : targetBranch.id();
        // I-J1, checked here for a clear answer and held by `ux_join_pending` for the race.
        if (requests.findPending(clan.id(), targetBranchId, caller).isPresent()) {
            throw duplicatePending();
        }

        Instant now = clock.instant();
        ClanJoinRequest request;
        try {
            request =
                    requests.save(
                            ClanJoinRequest.submit(
                                    ids.newId(),
                                    clan.id(),
                                    targetBranchId,
                                    caller,
                                    command.message(),
                                    now));
        } catch (DataIntegrityViolationException collision) {
            // Two submissions in flight at once; the index caught the second one.
            throw duplicatePending();
        }

        auditWriter.write(
                AuditRecord.inClan(
                        AuditAction.JOIN_REQUEST_SUBMITTED,
                        clan.id(),
                        AuditRecord.TARGET_JOIN_REQUEST,
                        request.id(),
                        caller));
        events.publishEvent(
                new JoinRequestSubmitted(clan.id(), request.id(), targetBranchId, caller, now));

        return view(request, clan, branchNames(clan.id()), null);
    }

    /** Withdraws one's own pending request (FR-J5). */
    @Transactional
    public void cancel(UserId caller, UUID requestId) {
        ClanJoinRequest request =
                requests
                        .findByIdAndRequester(requestId, caller)
                        .orElseThrow(JoinRequestService::noSuchRequest);
        requirePending(request);

        Instant now = clock.instant();
        requests.save(request.cancel(now));

        auditWriter.write(
                AuditRecord.inClan(
                        AuditAction.JOIN_REQUEST_CANCELLED,
                        request.clanId(),
                        AuditRecord.TARGET_JOIN_REQUEST,
                        request.id(),
                        caller));
        events.publishEvent(
                new JoinRequestDecided(
                        request.clanId(),
                        request.id(),
                        caller,
                        JoinRequestDecided.Outcome.CANCELLED,
                        caller,
                        now));
    }

    /**
     * The approver's inbox (doc 05 §4.5), <b>filtered to what this approver may decide</b>. A null
     * status means every status.
     *
     * <p>An approver for Chi Hai sees the requests aimed at Chi Hai and the chi below it, and
     * nothing else — including nothing aimed at the clan as a whole, which only a clan-scoped
     * approver can answer. Showing a request somebody cannot act on is worse than not showing it:
     * they either try and are refused, or they assume it has been dealt with.
     *
     * <p>Filtered in memory, after the page is read. That is honest about its cost — the page total
     * counts requests the caller may not see — and it is the only way to ask the resolver rather
     * than re-implementing scope containment in SQL. Phase 8 revisits it if an inbox ever grows
     * past a page or two; the correctness of the filter is what matters here.
     */
    @Transactional(readOnly = true)
    public Paged<JoinRequestView> inbox(
            UserId caller, ClanId clanId, JoinRequestStatus status, int page, int size) {

        requireApproverSomewhere(caller, clanId);
        Clan clan = requireClan(clanId);

        List<ClanJoinRequest> rows =
                requests.findByClanAndStatus(clanId, status, page * size, size).stream()
                        .filter(request -> mayDecide(caller, clanId, request))
                        .toList();
        long total = requests.countByClanAndStatus(clanId, status);

        Map<BranchId, String> branchNames = branchNames(clanId);
        Map<UserId, UserProfile> profiles =
                users.profiles(rows.stream().map(ClanJoinRequest::requesterUserId).toList());

        List<JoinRequestView> content = new ArrayList<>(rows.size());
        for (ClanJoinRequest request : rows) {
            content.add(view(request, clan, branchNames, profiles.get(request.requesterUserId())));
        }
        return new Paged<>(content, page, size, total);
    }

    /**
     * Approves a request: the membership and the decision, in one transaction (FR-J3).
     *
     * <p><b>Two checks, not one</b> (doc 04 §5.2 in spirit): {@code MEMBER_APPROVE} over the scope
     * the request was aimed at, <i>and</i> over the branch the approver is placing them into. An
     * approver for Chi Hai answering a Chi Hai request cannot drop the new member into Chi Ba on the
     * way past — which is the same shape of escape hatch that multi-endpoint relationship checks
     * close in the tree.
     *
     * @param homeBranchId where to put them; null falls back to the branch they asked for, and then
     *     to no branch at all, which is what "approved into the clan as a whole" means
     */
    @Transactional
    public JoinRequestView approve(
            UserId caller, ClanId clanId, UUID requestId, BranchId homeBranchId) {

        ClanJoinRequest request =
                requests.findById(clanId, requestId).orElseThrow(JoinRequestService::noSuchRequest);
        AccessDecision authorizing =
                resolver.require(
                        caller, Permission.MEMBER_APPROVE, targetOf(clanId, request.targetBranchId()));
        Clan clan = requireClan(clanId);
        requirePending(request);

        BranchId home = homeBranchId != null ? homeBranchId : request.targetBranchId();
        if (home != null && branches.findById(clanId, home).isEmpty()) {
            throw new NotFoundException(ErrorCode.BRANCH_NOT_FOUND, "No such branch.");
        }
        if (home != null && !home.equals(request.targetBranchId())) {
            resolver.require(caller, Permission.MEMBER_APPROVE, Target.branch(clanId, home));
        }

        // I-M1, read-before-write. The index below is what actually holds under a race; this is
        // what turns the common case into a sentence rather than a constraint violation.
        if (memberships.findActive(clanId, request.requesterUserId()).isPresent()) {
            throw new ConflictException(
                    ErrorCode.MEMBERSHIP_EXISTS, "That person is already a member of this clan.");
        }

        Instant now = clock.instant();
        ClanJoinRequest decided = requests.save(request.approve(caller, now));
        ClanMembership membership;
        try {
            membership =
                    memberships.save(
                            ClanMembership.grant(
                                    ids.newId(),
                                    clanId,
                                    request.requesterUserId(),
                                    home,
                                    caller,
                                    now));
        } catch (DataIntegrityViolationException collision) {
            throw new ConflictException(
                    ErrorCode.MEMBERSHIP_EXISTS, "That person is already a member of this clan.");
        }

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.JOIN_REQUEST_APPROVED,
                                clanId,
                                AuditRecord.TARGET_JOIN_REQUEST,
                                requestId,
                                caller)
                        .authorizedBy(authorizing.authorizingGrantId()));
        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.MEMBERSHIP_GRANTED,
                                clanId,
                                AuditRecord.TARGET_MEMBERSHIP,
                                membership.id(),
                                caller)
                        .authorizedBy(authorizing.authorizingGrantId()));
        events.publishEvent(
                new JoinRequestDecided(
                        clanId,
                        requestId,
                        request.requesterUserId(),
                        JoinRequestDecided.Outcome.APPROVED,
                        caller,
                        now));
        events.publishEvent(
                new MembershipGranted(clanId, request.requesterUserId(), home, caller, now));

        log.info(
                "Join request approved clanId={} requestId={} member={} homeBranchId={}",
                clanId,
                requestId,
                request.requesterUserId(),
                home);

        return view(
                decided,
                clan,
                branchNames(clanId),
                users.profiles(List.of(request.requesterUserId())).get(request.requesterUserId()));
    }

    /** Refuses a request, with a reason the requester will read (FR-J3). */
    @Transactional
    public JoinRequestView reject(UserId caller, ClanId clanId, UUID requestId, String reason) {
        ClanJoinRequest request =
                requests.findById(clanId, requestId).orElseThrow(JoinRequestService::noSuchRequest);
        AccessDecision authorizing =
                resolver.require(
                        caller, Permission.MEMBER_APPROVE, targetOf(clanId, request.targetBranchId()));
        Clan clan = requireClan(clanId);
        requirePending(request);

        Instant now = clock.instant();
        ClanJoinRequest decided = requests.save(request.reject(caller, reason, now));

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.JOIN_REQUEST_REJECTED,
                                clanId,
                                AuditRecord.TARGET_JOIN_REQUEST,
                                requestId,
                                caller)
                        .authorizedBy(authorizing.authorizingGrantId()));
        events.publishEvent(
                new JoinRequestDecided(
                        clanId,
                        requestId,
                        request.requesterUserId(),
                        JoinRequestDecided.Outcome.REJECTED,
                        caller,
                        now));

        return view(
                decided,
                clan,
                branchNames(clanId),
                users.profiles(List.of(request.requesterUserId())).get(request.requesterUserId()));
    }

    /**
     * The caller's own outbound requests (FR-J5).
     *
     * <p>Spans clans the caller is not a member of, which is the entire point: this is the only
     * place a pending applicant can see what they asked for and what came of it.
     */
    @Transactional(readOnly = true)
    public List<JoinRequestView> myRequests(UserId caller) {
        List<ClanJoinRequest> rows = requests.findByRequester(caller, MY_REQUESTS_LIMIT);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<ClanId, Clan> clansById = new HashMap<>();
        clans.findAllById(rows.stream().map(ClanJoinRequest::clanId).distinct().toList())
                .forEach(clan -> clansById.put(clan.id(), clan));

        List<JoinRequestView> views = new ArrayList<>(rows.size());
        for (ClanJoinRequest request : rows) {
            Clan clan = clansById.get(request.clanId());
            if (clan == null) {
                // The clan was deleted after the request was made. Nothing useful to show.
                continue;
            }
            // Branch names are not resolved here: the requester is not a member, and the name of a
            // branch inside a private clan is not theirs to read. The card they saw when they
            // applied carried it; the list carries the clan.
            views.add(view(request, clan, Map.of(), null));
        }
        return views;
    }

    /**
     * Closes pending requests older than ninety days (B2.9, doc 02 §2.4).
     *
     * <p>A plain method rather than something only a scheduler can reach, so a test can age the
     * clock and call it. {@code JoinRequestExpiryJob} is the thing with the timetable.
     *
     * @return how many requests were closed
     */
    @Transactional
    public int expireOverdue() {
        Instant now = clock.instant();
        List<ClanJoinRequest> overdue = requests.findExpired(now, EXPIRY_BATCH_SIZE);
        for (ClanJoinRequest request : overdue) {
            requests.save(request.expire(now));
            auditWriter.write(
                    AuditRecord.inClan(
                            AuditAction.JOIN_REQUEST_EXPIRED,
                            request.clanId(),
                            AuditRecord.TARGET_JOIN_REQUEST,
                            request.id(),
                            // No actor: nobody decided this, the calendar did.
                            null));
            events.publishEvent(
                    new JoinRequestDecided(
                            request.clanId(),
                            request.id(),
                            request.requesterUserId(),
                            JoinRequestDecided.Outcome.EXPIRED,
                            null,
                            now));
        }
        if (!overdue.isEmpty()) {
            log.info("Expired {} join requests older than {}", overdue.size(), ClanJoinRequest.LIFETIME);
        }
        return overdue.size();
    }

    /**
     * The target a join request's scope corresponds to.
     *
     * <p>A request carrying a branch code was aimed at that chi and is that chi's to answer; one
     * carrying a clan code was aimed at the family as a whole, and only somebody who approves for
     * the whole family can answer it. Mapping the second case to a branch target would let any
     * branch approver admit strangers to the clan.
     */
    private static Target targetOf(ClanId clanId, BranchId targetBranchId) {
        return targetBranchId == null
                ? Target.clan(clanId)
                : Target.branch(clanId, targetBranchId);
    }

    /** Whether the caller may answer this particular request. Used to filter the inbox. */
    private boolean mayDecide(UserId caller, ClanId clanId, ClanJoinRequest request) {
        return resolver.can(
                caller, Permission.MEMBER_APPROVE, targetOf(clanId, request.targetBranchId()));
    }

    /**
     * The gate on opening the inbox at all: {@code MEMBER_APPROVE} somewhere in the clan.
     *
     * <p>Deliberately scope-blind, because the per-row filter above is what does the scoping. This
     * is here so that a member who approves nothing gets a 403 rather than an empty page they will
     * refresh three times.
     */
    private void requireApproverSomewhere(UserId caller, ClanId clanId) {
        resolver.requireMembership(caller, clanId);
        if (!resolver.permissionsOf(caller, clanId).contains(Permission.MEMBER_APPROVE)) {
            throw new app.giaky.common.error.PermissionDeniedException(
                    "You do not approve join requests in this clan.");
        }
    }

    /** The clan, or a 404 that discloses nothing (doc 04 §7). */
    private Clan requireClan(ClanId clanId) {
        return clans.findById(clanId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        ErrorCode.CLAN_NOT_FOUND,
                                        "That clan does not exist, or you are not a member of it."));
    }

    private static void requirePending(ClanJoinRequest request) {
        if (!request.isPending()) {
            // I-J4. A second approval of the same request is the case this exists for, and 409 is
            // the honest answer: the request is fine, it has simply already been answered.
            throw new ConflictException(
                    ErrorCode.DUPLICATE_PENDING_REQUEST,
                    "That request has already been " + request.status().name().toLowerCase() + ".");
        }
    }

    private Map<BranchId, String> branchNames(ClanId clanId) {
        Map<BranchId, String> names = new HashMap<>();
        for (Branch branch : branches.findAllByClan(clanId)) {
            names.put(branch.id(), branch.name());
        }
        return names;
    }

    private static JoinRequestView view(
            ClanJoinRequest request,
            Clan clan,
            Map<BranchId, String> branchNames,
            UserProfile requester) {

        return new JoinRequestView(
                request.id(),
                request.clanId().value(),
                clan.name(),
                clan.clanCode().value(),
                request.targetBranchId() == null ? null : request.targetBranchId().value(),
                // Guarded rather than looked up blindly: a request aimed at the clan as a whole has
                // no target branch, and an immutable map throws on a null key rather than missing.
                request.targetBranchId() == null
                        ? null
                        : branchNames.get(request.targetBranchId()),
                request.requesterUserId().value(),
                requester == null ? null : requester.displayName(),
                requester == null ? null : requester.email(),
                request.message(),
                request.status().name(),
                request.decidedBy() == null ? null : request.decidedBy().value(),
                request.decidedAt(),
                request.decisionReason(),
                request.createdAt(),
                request.expiresAt());
    }

    private static ConflictException duplicatePending() {
        return new ConflictException(
                ErrorCode.DUPLICATE_PENDING_REQUEST,
                "You have already asked to join. Wait for an answer, or cancel that request first.");
    }

    private static NotFoundException noSuchCode() {
        return new NotFoundException(ErrorCode.CLAN_NOT_FOUND, "No clan uses that code.");
    }

    private static NotFoundException noSuchRequest() {
        return new NotFoundException(ErrorCode.REQUEST_NOT_FOUND, "No such join request.");
    }
}
