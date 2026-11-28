package app.giaky.linking.application;

import app.giaky.authorization.domain.AccessDecision;
import app.giaky.authorization.domain.Permission;
import app.giaky.authorization.domain.PermissionResolver;
import app.giaky.authorization.domain.Target;
import app.giaky.clanorg.application.ClanDirectory;
import app.giaky.common.audit.AuditAction;
import app.giaky.common.audit.AuditRecord;
import app.giaky.common.audit.AuditWriter;
import app.giaky.common.error.ConflictException;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.error.PermissionDeniedException;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.IdGenerator;
import app.giaky.common.types.Paged;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.identity.application.UserDirectory;
import app.giaky.identity.application.UserProfile;
import app.giaky.lineage.application.PersonLinkGateway;
import app.giaky.linking.application.event.ClaimDecided;
import app.giaky.linking.application.event.ClaimSubmitted;
import app.giaky.linking.application.event.PersonLinked;
import app.giaky.linking.domain.ClaimStatus;
import app.giaky.linking.domain.LinkableNode;
import app.giaky.linking.domain.NodeClaimRequest;
import app.giaky.linking.domain.NodeClaimRequestRepository;
import app.giaky.linking.domain.PersonUserLinker;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claiming a node, from both sides: submit, cancel, and the approver's inbox (FR-N1…FR-N3).
 *
 * <p><b>Approval is the method that has to be exactly right.</b> It writes {@code
 * person.linked_user_id}, and writing it twice — or writing it for two people — is the failure this
 * whole phase exists to prevent. Four things stop it, in increasing order of stubbornness: the state
 * machine in {@link NodeClaimRequest} (I-K5), the read-before-write in {@link PersonUserLinker}, the
 * conditional {@code UPDATE} underneath that (B6.11), and {@code ux_persons_linked_user} (I-P4)
 * underneath all of it.
 *
 * <p><b>Approval grants nothing.</b> Doc 04 §6 keeps being <i>in</i> the tree and being able to
 * <i>edit</i> it apart, and phase 6 §8 names quietly granting {@code TREE_EDIT} here as the most
 * likely mistake of the phase. {@code ClaimApprovalGrantsNothingIT} asserts it; there is deliberately
 * no {@code GrantService} on this class.
 */
@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    /** How many of a caller's own claims {@code GET /me/claims} returns. */
    public static final int MY_CLAIMS_LIMIT = 50;

    /** The most claims the inbox reads to find the ones a caller may decide. */
    static final int INBOX_SCAN_LIMIT = 2_000;

    /** The page size of that scan. */
    static final int INBOX_SCAN_CHUNK = 200;

    /** The reason given on a claim for a node that somebody else has just been linked to. */
    static final String REASON_NODE_TAKEN = "Somebody else has been linked to this person.";

    /** The reason given on a claim whose requester has just been linked to a different node. */
    static final String REASON_REQUESTER_PLACED =
            "You have been linked to %s, and an account holds one place in each family.";

    private final NodeClaimRequestRepository claims;
    private final PersonUserLinker linker;
    private final PersonLinkGateway lineage;
    private final PermissionResolver resolver;
    private final ClanDirectory clans;
    private final UserDirectory users;
    private final AuditWriter auditWriter;
    private final ApplicationEventPublisher events;
    private final IdGenerator ids;
    private final Clock clock;

    public ClaimService(
            NodeClaimRequestRepository claims,
            PersonUserLinker linker,
            PersonLinkGateway lineage,
            PermissionResolver resolver,
            ClanDirectory clans,
            UserDirectory users,
            AuditWriter auditWriter,
            ApplicationEventPublisher events,
            IdGenerator ids,
            Clock clock) {

        this.claims = claims;
        this.linker = linker;
        this.lineage = lineage;
        this.resolver = resolver;
        this.clans = clans;
        this.users = users;
        this.auditWriter = auditWriter;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * Asks to be recognised as a node (FR-N1).
     *
     * <p>Open to any active member, which is why there is no {@code require(...)} here: claiming is
     * what somebody does <i>before</i> they hold anything, and a permission would make the feature
     * unreachable by exactly the people it is for.
     *
     * @throws ConflictException the node is already somebody's (I-K1), the caller already holds a
     *     node in this clan (I-P4), or they have a live claim on this one (I-K3)
     */
    @Transactional
    public NodeClaimView submit(UserId caller, ClanId clanId, SubmitClaimCommand command) {
        resolver.requireMembership(caller, clanId); // I-K2
        if (command.personId() == null) {
            throw new NotFoundException(ErrorCode.PERSON_NOT_FOUND, "No such person.");
        }
        LinkableNode node = linker.require(clanId, command.personId());

        // I-K1 at submission time. Re-checked at approval, because a node can be taken in between —
        // which is the whole reason approval re-checks rather than trusting this.
        if (node.isLinkedTo(caller)) {
            throw new ConflictException(
                    ErrorCode.NODE_ALREADY_LINKED, "You are already linked to this person.");
        }
        if (node.isLinked()) {
            throw new ConflictException(
                    ErrorCode.NODE_ALREADY_LINKED,
                    "Somebody has already been linked to this person.");
        }
        // I-P4, checked before the claim rather than at approval, so the person is told now instead
        // of after waiting for an answer that could never be yes.
        Optional<LinkableNode> mine = linker.nodeOf(clanId, caller);
        if (mine.isPresent()) {
            throw new ConflictException(
                    ErrorCode.USER_ALREADY_LINKED_IN_CLAN,
                    "You are already recorded as "
                            + mine.get().fullName()
                            + " in this family. Unlink that node first.");
        }
        // I-K3, checked here for a clear answer and held by ux_claim_pending for the race.
        if (claims.findPending(clanId, command.personId(), caller).isPresent()) {
            throw duplicatePending();
        }

        Instant now = clock.instant();
        NodeClaimRequest claim;
        try {
            claim =
                    claims.save(
                            NodeClaimRequest.submit(
                                    ids.newId(),
                                    clanId,
                                    command.personId(),
                                    caller,
                                    command.evidence(),
                                    now));
        } catch (DataIntegrityViolationException collision) {
            throw duplicatePending();
        }

        auditWriter.write(
                AuditRecord.inClan(
                        AuditAction.CLAIM_SUBMITTED,
                        clanId,
                        AuditRecord.TARGET_CLAIM,
                        claim.id(),
                        caller));
        events.publishEvent(
                new ClaimSubmitted(
                        clanId,
                        claim.id(),
                        node.id(),
                        node.branchId(),
                        node.fullName(),
                        caller,
                        now));

        log.info(
                "Claim submitted clanId={} claimId={} personId={} requester={}",
                clanId,
                claim.id(),
                node.id(),
                caller);

        return view(claim, node.fullName(), Map.of(), Map.of());
    }

    /**
     * Withdraws one's own pending claim.
     *
     * <p>Looked up by id <i>and</i> requester, so a claim id belonging to somebody else is a 404
     * rather than an authorization question — the same shape as cancelling a join request.
     */
    @Transactional
    public void cancel(UserId caller, UUID claimId) {
        NodeClaimRequest claim =
                claims.findByIdAndRequester(claimId, caller).orElseThrow(ClaimService::noSuchClaim);
        requirePending(claim);

        Instant now = clock.instant();
        claims.save(claim.cancel(now));

        auditWriter.write(
                AuditRecord.inClan(
                        AuditAction.CLAIM_DECIDED,
                        claim.clanId(),
                        AuditRecord.TARGET_CLAIM,
                        claim.id(),
                        caller));
        events.publishEvent(
                new ClaimDecided(
                        claim.clanId(),
                        claim.id(),
                        claim.personId(),
                        nameOf(claim.clanId(), claim.personId()),
                        claim.requesterUserId(),
                        ClaimDecided.Outcome.CANCELLED,
                        null,
                        caller,
                        now));
    }

    /**
     * The approver's inbox (doc 05 §4.10), <b>filtered to what this approver may decide</b> (FR-N2).
     *
     * <p>An approver for Chi Hai sees claims on Chi Hai nodes and nothing else. Showing a claim
     * somebody cannot act on is worse than not showing it: they either try and are refused, or they
     * assume it has been dealt with.
     *
     * <p>Filtered in memory, because asking the resolver is the only way to decide scope without
     * re-implementing containment in SQL. <b>The filter runs before the page is cut</b>, so the
     * page and its total describe the same set: what this approver may decide. Cutting first and
     * filtering after, as this did until the phase-6 walkthrough, made the total count every claim
     * in the clan. The badge on the inbox tab then told a Chi Giáp editor there were two requests
     * waiting when one of them was a Chi Ất claim they could not see, which is both a wrong number
     * and a small leak of another chi's business. It could also return short or empty pages
     * before the last one.
     *
     * <p>The scan stops at {@link #INBOX_SCAN_LIMIT} rows, newest first. A clan with more open
     * claims than that has a problem no inbox fixes. Below it the cost is a node lookup per row;
     * the caller's grants are read once, through the authorization request cache.
     */
    @Transactional(readOnly = true)
    public Paged<NodeClaimView> inbox(
            UserId caller, ClanId clanId, ClaimStatus status, int page, int size) {

        requireApproverSomewhere(caller, clanId);

        List<NodeClaimRequest> decidable = new ArrayList<>();
        for (int offset = 0; offset < INBOX_SCAN_LIMIT; offset += INBOX_SCAN_CHUNK) {
            List<NodeClaimRequest> chunk =
                    claims.findByClanAndStatus(clanId, status, offset, INBOX_SCAN_CHUNK);
            chunk.stream()
                    .filter(claim -> mayDecide(caller, clanId, claim))
                    .forEach(decidable::add);
            if (chunk.size() < INBOX_SCAN_CHUNK) {
                break;
            }
        }

        int from = (int) Math.min((long) page * size, decidable.size());
        int to = Math.min(from + size, decidable.size());
        return new Paged<>(
                enrich(clanId, decidable.subList(from, to)), page, size, decidable.size());
    }

    /**
     * Approves a claim: the decision and the link, in one transaction (FR-N3).
     *
     * <p><b>The node is re-checked here</b>, not trusted from submission. Between somebody claiming
     * a node and an editor answering, another editor may have assigned it directly — and the honest
     * answer to "approve this" is then {@code 409 NODE_ALREADY_LINKED}, not a silent overwrite of
     * the other decision.
     *
     * <p>The claims this answers are refused in the same transaction: other live claims on the
     * same node, and the requester's own claims on other nodes. {@link #closeClaimsSettledBy} does
     * it, from the {@link PersonLinked} this publishes. Leaving them pending would leave an inbox
     * full of decisions that can only end in a 409, which reads as a broken screen rather than as a
     * resolved question.
     *
     * @throws PermissionDeniedException the caller does not hold {@code NODE_CLAIM_APPROVE} over the
     *     node, or is the requester and is not a clan administrator (I-K4)
     * @throws ConflictException the node was linked in the meantime (I-K1), or the claim has already
     *     been answered (I-K5)
     */
    @Transactional
    public NodeClaimView approve(UserId caller, ClanId clanId, UUID claimId) {
        NodeClaimRequest claim =
                claims.findById(clanId, claimId).orElseThrow(ClaimService::noSuchClaim);
        LinkableNode node = linker.require(clanId, claim.personId());
        AccessDecision authorizing =
                resolver.require(caller, Permission.NODE_CLAIM_APPROVE, targetOf(node));
        requireNotSelfApproval(caller, clanId, claim);
        requireNotTakenByAnother(node, claim);
        requirePending(claim);

        Instant now = clock.instant();
        // The only call to the linker on the approval path. Everything I-K1, I-P4 and I-P5 have to
        // say about this link is said there, once, for every route into it.
        LinkableNode linked =
                linker.link(clanId, node.id(), claim.requesterUserId(), caller, now);

        // Saved before PersonLinked is published below, so the claims that event closes (other
        // claims on this node, and this requester's claims elsewhere) never include this one.
        NodeClaimRequest decided = claims.save(claim.approve(caller, now));

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.CLAIM_DECIDED,
                                clanId,
                                AuditRecord.TARGET_CLAIM,
                                claimId,
                                caller)
                        .withAfterJson("{\"outcome\":\"APPROVED\"}")
                        .authorizedBy(authorizing.authorizingGrantId()));
        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.PERSON_LINKED,
                                clanId,
                                AuditRecord.TARGET_PERSON,
                                node.id().value(),
                                caller)
                        .authorizedBy(authorizing.authorizingGrantId()));

        events.publishEvent(
                new ClaimDecided(
                        clanId,
                        claimId,
                        node.id(),
                        linked.fullName(),
                        claim.requesterUserId(),
                        ClaimDecided.Outcome.APPROVED,
                        null,
                        caller,
                        now));
        events.publishEvent(
                new PersonLinked(
                        clanId,
                        node.id(),
                        linked.fullName(),
                        claim.requesterUserId(),
                        caller,
                        true,
                        now));

        log.info(
                "Claim approved clanId={} claimId={} personId={} linkedUser={} approver={}",
                clanId,
                claimId,
                node.id(),
                claim.requesterUserId(),
                caller);

        return single(clanId, decided, linked.fullName());
    }

    /** Refuses a claim, with a reason the requester will read (FR-N2). */
    @Transactional
    public NodeClaimView reject(UserId caller, ClanId clanId, UUID claimId, String reason) {
        NodeClaimRequest claim =
                claims.findById(clanId, claimId).orElseThrow(ClaimService::noSuchClaim);
        LinkableNode node = linker.require(clanId, claim.personId());
        AccessDecision authorizing =
                resolver.require(caller, Permission.NODE_CLAIM_APPROVE, targetOf(node));
        requireNotSelfApproval(caller, clanId, claim);
        requirePending(claim);

        Instant now = clock.instant();
        NodeClaimRequest decided = claims.save(claim.reject(caller, reason, now));

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.CLAIM_DECIDED,
                                clanId,
                                AuditRecord.TARGET_CLAIM,
                                claimId,
                                caller)
                        .withAfterJson("{\"outcome\":\"REJECTED\"}")
                        .authorizedBy(authorizing.authorizingGrantId()));
        events.publishEvent(
                new ClaimDecided(
                        clanId,
                        claimId,
                        node.id(),
                        node.fullName(),
                        claim.requesterUserId(),
                        ClaimDecided.Outcome.REJECTED,
                        decided.decisionReason(),
                        caller,
                        now));

        return single(clanId, decided, node.fullName());
    }

    /**
     * The caller's own claims, across every clan (F6.4).
     *
     * <p>Where somebody who has asked to be recognised goes to find out what came of it — including
     * the reason a refusal gave, which is the entire point of requiring one.
     */
    @Transactional(readOnly = true)
    public List<NodeClaimView> myClaims(UserId caller) {
        List<NodeClaimRequest> rows = claims.findByRequester(caller, MY_CLAIMS_LIMIT);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<ClanId, String> clanNames =
                clans.namesOf(rows.stream().map(NodeClaimRequest::clanId).distinct().toList());

        List<NodeClaimView> views = new ArrayList<>(rows.size());
        for (NodeClaimRequest claim : rows) {
            // Names, not tree context: the requester is looking at their own history, and the
            // parents-and-children card is the approver's verification tool.
            views.add(
                    new NodeClaimView(
                            claim.id(),
                            claim.clanId().value(),
                            clanNames.get(claim.clanId()),
                            claim.personId().value(),
                            nameOf(claim.clanId(), claim.personId()),
                            null,
                            List.of(),
                            List.of(),
                            claim.requesterUserId().value(),
                            null,
                            null,
                            claim.evidence(),
                            claim.status().name(),
                            claim.decidedBy() == null ? null : claim.decidedBy().value(),
                            null,
                            claim.decidedAt(),
                            claim.decisionReason(),
                            claim.createdAt()));
        }
        return views;
    }

    // --- collaborators inside the context ------------------------------------------------------

    /**
     * Closes every claim somebody still has open in a clan they are no longer a member of (I-K2).
     *
     * <p>Package-private and unguarded for the reason {@code GrantService.revokeAllFor} is: the only
     * caller is {@link LinkLifecycleListener}, acting on a removal or a departure that was already
     * authorized. Left open, these would sit in the approvers' inbox as questions with one possible
     * answer — approving one is refused, because {@link PersonUserLinker} will not link a
     * non-member (I-P5).
     *
     * <p>Cancelled rather than rejected. Nobody judged the claim; the person asking is simply no
     * longer there to be answered, which is what a withdrawal is.
     *
     * @return how many claims were closed
     */
    int withdrawAllOf(ClanId clanId, UserId requester, UserId actor) {
        List<NodeClaimRequest> open = claims.findPendingByRequester(clanId, requester);
        Instant now = clock.instant();
        for (NodeClaimRequest claim : open) {
            claims.save(claim.cancel(now));
            auditWriter.write(
                    AuditRecord.inClan(
                            AuditAction.CLAIM_DECIDED,
                            clanId,
                            AuditRecord.TARGET_CLAIM,
                            claim.id(),
                            actor));
            events.publishEvent(
                    new ClaimDecided(
                            clanId,
                            claim.id(),
                            claim.personId(),
                            nameOf(clanId, claim.personId()),
                            requester,
                            ClaimDecided.Outcome.CANCELLED,
                            null,
                            actor,
                            now));
        }
        return open.size();
    }

    // --- internals -----------------------------------------------------------------------------

    /**
     * I-K4: nobody answers their own claim, unless they administer the clan.
     *
     * <p>The exception is not a loophole, it is the bootstrap: the founder who has just created a
     * clan and entered their grandfather is the only person who could recognise themselves in it,
     * and a rule with no exception would leave the first member of every family permanently
     * unlinked.
     */
    private void requireNotSelfApproval(UserId caller, ClanId clanId, NodeClaimRequest claim) {
        if (!caller.equals(claim.requesterUserId())) {
            return;
        }
        if (resolver.can(caller, Permission.CLAN_ADMIN, Target.clan(clanId))) {
            return;
        }
        throw new PermissionDeniedException(
                ErrorCode.PERMISSION_DENIED,
                "You cannot decide your own claim. Ask somebody else who approves claims here.");
    }

    /**
     * Refuses the live claims that a new link has answered, whichever route made the link.
     *
     * <p>Two sets of claims become questions with one possible answer the moment {@code linkedUser}
     * is attached to {@code personId}: everybody else's claims on that node (I-K1: it is no longer
     * unlinked), and the linked user's own claims on other nodes in the clan (I-P4: they now hold
     * their one place). Approving any of them is refused with a 409. Left open, they sit in an
     * approver's inbox as decisions that cannot be made, and on the requester's own page as
     * "Waiting" for something that will never come.
     *
     * <p>Approval used to close the first set itself and nothing closed the second; direct
     * assignment closed neither. Both routes publish {@link PersonLinked}, and {@link
     * LinkLifecycleListener} calls this from it inside the linking transaction, so there is one
     * place that does it for both.
     *
     * <p>Rejected with a reason, not cancelled: the requester did not withdraw anything, and they
     * are a relative who will read why.
     *
     * <p>Package-private and unguarded, like {@link #withdrawAllOf}: the link that triggers it was
     * already authorized.
     *
     * @return how many claims were closed
     */
    int closeClaimsSettledBy(
            ClanId clanId, PersonId personId, String personName, UserId linkedUser, UserId actor) {

        Map<UUID, NodeClaimRequest> settled = new LinkedHashMap<>();
        claims.findPendingForPerson(clanId, personId).forEach(c -> settled.put(c.id(), c));
        claims.findPendingByRequester(clanId, linkedUser).forEach(c -> settled.put(c.id(), c));

        Instant now = clock.instant();
        for (NodeClaimRequest claim : settled.values()) {
            String reason =
                    claim.requesterUserId().equals(linkedUser)
                            ? REASON_REQUESTER_PLACED.formatted(personName)
                            : REASON_NODE_TAKEN;
            claims.save(claim.reject(actor, reason, now));
            auditWriter.write(
                    AuditRecord.inClan(
                                    AuditAction.CLAIM_DECIDED,
                                    clanId,
                                    AuditRecord.TARGET_CLAIM,
                                    claim.id(),
                                    actor)
                            .withAfterJson("{\"outcome\":\"REJECTED\",\"supersededByLink\":true}"));
            events.publishEvent(
                    new ClaimDecided(
                            clanId,
                            claim.id(),
                            claim.personId(),
                            nameOf(clanId, claim.personId()),
                            claim.requesterUserId(),
                            ClaimDecided.Outcome.REJECTED,
                            reason,
                            actor,
                            now));
        }
        return settled.size();
    }

    /** Whether the caller may answer this particular claim. Used to filter the inbox. */
    private boolean mayDecide(UserId caller, ClanId clanId, NodeClaimRequest claim) {
        return lineage.node(clanId, claim.personId())
                .map(
                        node ->
                                resolver.can(
                                        caller,
                                        Permission.NODE_CLAIM_APPROVE,
                                        Target.person(clanId, node.id().value(), node.branchId())))
                .orElse(false);
    }

    /**
     * The gate on opening the inbox at all: {@code NODE_CLAIM_APPROVE} somewhere in the clan.
     *
     * <p>Deliberately scope-blind, because the per-row filter is what does the scoping. This exists
     * so that a member who approves nothing gets a 403 rather than an empty page they refresh three
     * times.
     */
    private void requireApproverSomewhere(UserId caller, ClanId clanId) {
        resolver.requireMembership(caller, clanId);
        if (!resolver.permissionsOf(caller, clanId).contains(Permission.NODE_CLAIM_APPROVE)) {
            throw new PermissionDeniedException("You do not decide node claims in this clan.");
        }
    }

    private List<NodeClaimView> enrich(ClanId clanId, List<NodeClaimRequest> rows) {

        Set<UserId> userIds = new LinkedHashSet<>();
        List<PersonId> personIds = new ArrayList<>();
        for (NodeClaimRequest claim : rows) {
            userIds.add(claim.requesterUserId());
            if (claim.decidedBy() != null) {
                userIds.add(claim.decidedBy());
            }
            personIds.add(claim.personId());
        }
        Map<UserId, UserProfile> profiles = users.profiles(List.copyOf(userIds));
        Map<PersonId, PersonLinkGateway.NodeContext> contexts = lineage.context(clanId, personIds);

        Map<PersonId, String> names = new HashMap<>();
        for (PersonId personId : personIds) {
            names.put(personId, nameOf(clanId, personId));
        }

        List<NodeClaimView> views = new ArrayList<>(rows.size());
        for (NodeClaimRequest claim : rows) {
            views.add(view(claim, names.get(claim.personId()), profiles, contexts));
        }
        return views;
    }

    private NodeClaimView single(ClanId clanId, NodeClaimRequest claim, String personName) {
        return view(
                claim,
                personName,
                users.profiles(
                        claim.decidedBy() == null
                                ? List.of(claim.requesterUserId())
                                : List.of(claim.requesterUserId(), claim.decidedBy())),
                lineage.context(clanId, List.of(claim.personId())));
    }

    private NodeClaimView view(
            NodeClaimRequest claim,
            String personName,
            Map<UserId, UserProfile> profiles,
            Map<PersonId, PersonLinkGateway.NodeContext> contexts) {

        UserProfile requester = profiles.get(claim.requesterUserId());
        UserProfile decider = claim.decidedBy() == null ? null : profiles.get(claim.decidedBy());
        PersonLinkGateway.NodeContext context =
                contexts.getOrDefault(claim.personId(), PersonLinkGateway.NodeContext.empty());

        return new NodeClaimView(
                claim.id(),
                claim.clanId().value(),
                null,
                claim.personId().value(),
                personName,
                lineage.node(claim.clanId(), claim.personId())
                        .map(PersonLinkGateway.NodeRef::generation)
                        .orElse(null),
                context.parentNames(),
                context.childNames(),
                claim.requesterUserId().value(),
                requester == null ? null : requester.displayName(),
                requester == null ? null : requester.email(),
                claim.evidence(),
                claim.status().name(),
                claim.decidedBy() == null ? null : claim.decidedBy().value(),
                decider == null ? null : decider.displayName(),
                claim.decidedAt(),
                claim.decisionReason(),
                claim.createdAt());
    }

    private String nameOf(ClanId clanId, PersonId personId) {
        return lineage.node(clanId, personId)
                .map(PersonLinkGateway.NodeRef::fullName)
                // A claim whose node has since been soft-deleted still has to render on the
                // requester's own list; an empty name is better than a missing row.
                .orElse(null);
    }

    private static Target targetOf(LinkableNode node) {
        return Target.person(node.clanId(), node.id().value(), node.branchId());
    }

    /**
     * B6.3: a node somebody else holds answers {@code 409 NODE_ALREADY_LINKED}.
     *
     * <p>Asked before {@link #requirePending}, because the link that took the node also closed
     * this claim ({@link #closeClaimsSettledBy}). An approver on a stale screen should hear that
     * the node is taken, which is the reason, rather than that the claim was "already rejected",
     * which is only the consequence. {@link PersonUserLinker} asks the same question again, and its {@code
     * UPDATE} is what settles a race; this is only the better sentence.
     */
    private static void requireNotTakenByAnother(LinkableNode node, NodeClaimRequest claim) {
        if (node.isLinked() && !node.isLinkedTo(claim.requesterUserId())) {
            throw new ConflictException(
                    ErrorCode.NODE_ALREADY_LINKED,
                    "Somebody else has already been linked to this person.");
        }
    }

    private static void requirePending(NodeClaimRequest claim) {
        if (!claim.isPending()) {
            // I-K5. A second approval of the same claim is the case this exists for, and 409 is the
            // honest answer: the claim is fine, it has simply already been answered.
            throw new ConflictException(
                    ErrorCode.DUPLICATE_PENDING_REQUEST,
                    "That claim has already been " + claim.status().name().toLowerCase() + ".");
        }
    }

    private static ConflictException duplicatePending() {
        return new ConflictException(
                ErrorCode.DUPLICATE_PENDING_REQUEST,
                "You have already claimed this person. Wait for an answer, or withdraw that claim"
                    + " first.");
    }

    private static NotFoundException noSuchClaim() {
        return new NotFoundException(ErrorCode.REQUEST_NOT_FOUND, "No such claim.");
    }
}
