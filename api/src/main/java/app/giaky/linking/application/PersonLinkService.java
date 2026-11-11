package app.giaky.linking.application;

import app.giaky.authorization.domain.AccessDecision;
import app.giaky.authorization.domain.Permission;
import app.giaky.authorization.domain.PermissionResolver;
import app.giaky.authorization.domain.Target;
import app.giaky.common.audit.AuditAction;
import app.giaky.common.audit.AuditRecord;
import app.giaky.common.audit.AuditWriter;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.Paged;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.identity.application.UserDirectory;
import app.giaky.identity.application.UserProfile;
import app.giaky.lineage.application.PersonLinkGateway;
import app.giaky.lineage.application.PersonSummaryView;
import app.giaky.linking.application.event.PersonLinked;
import app.giaky.linking.application.event.PersonUnlinked;
import app.giaky.linking.domain.LinkableNode;
import app.giaky.linking.domain.PersonUserLinker;
import app.giaky.linking.domain.ProfilePropagationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Linking without the request-and-approve round trip: direct assignment, unlinking, and the
 * candidate browser those two act on (FR-N4, FR-N5, B6.6).
 *
 * <p>Split from {@link ClaimService} by <i>who is asking</i> rather than by resource. A claim is a
 * conversation between a member and an approver, with a row that records how it went; an assignment
 * is one authorized person saying "this is her", which needs no row and no waiting. Folding them
 * into one service would mean every method carrying a claim it might not have.
 */
@Service
public class PersonLinkService {

    private static final Logger log = LoggerFactory.getLogger(PersonLinkService.class);

    /** The largest page of candidates the browser will serve. */
    public static final int MAX_PAGE_SIZE = 50;

    private final PersonUserLinker linker;
    private final PersonLinkGateway lineage;
    private final ProfilePropagationRepository propagation;
    private final PermissionResolver resolver;
    private final UserDirectory users;
    private final AuditWriter auditWriter;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PersonLinkService(
            PersonUserLinker linker,
            PersonLinkGateway lineage,
            ProfilePropagationRepository propagation,
            PermissionResolver resolver,
            UserDirectory users,
            AuditWriter auditWriter,
            ApplicationEventPublisher events,
            Clock clock) {

        this.linker = linker;
        this.lineage = lineage;
        this.propagation = propagation;
        this.resolver = resolver;
        this.users = users;
        this.auditWriter = auditWriter;
        this.events = events;
        this.clock = clock;
    }

    /**
     * An editor attaches a member to an unlinked node (FR-N4).
     *
     * <p>The check is {@code NODE_CLAIM_APPROVE} over the node, the same permission approving a
     * claim needs — because it is the same decision, made without waiting to be asked. What it is
     * <b>not</b> is {@code TREE_EDIT}: linking somebody is not an edit to the genealogical record.
     *
     * <p>Whether the target is an active member is I-P5's question and is answered inside {@link
     * PersonUserLinker}, which returns {@code 422} for somebody who is not. That keeps direct
     * assignment and claim approval answering it identically.
     */
    @Transactional
    public LinkedNodeView assign(
            UserId caller, ClanId clanId, PersonId personId, UserId targetUserId) {

        LinkableNode node = linker.require(clanId, personId);
        AccessDecision authorizing =
                resolver.require(caller, Permission.NODE_CLAIM_APPROVE, targetOf(node));

        Instant now = clock.instant();
        LinkableNode linked = linker.link(clanId, personId, targetUserId, caller, now);

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.PERSON_LINKED,
                                clanId,
                                AuditRecord.TARGET_PERSON,
                                personId.value(),
                                caller)
                        .authorizedBy(authorizing.authorizingGrantId()));
        events.publishEvent(
                new PersonLinked(
                        clanId,
                        personId,
                        linked.fullName(),
                        targetUserId,
                        caller,
                        // Not via a claim: nobody asked, which is exactly why the person being
                        // linked needs to be told (B6.9).
                        false,
                        now));

        log.info(
                "Person linked directly clanId={} personId={} userId={} actor={}",
                clanId,
                personId,
                targetUserId,
                caller);

        return view(linked, caller);
    }

    /**
     * Removes a link (FR-N5).
     *
     * <p><b>Two people may do this</b>: an approver covering the node, and the person the node
     * belongs to. The second is not a courtesy — a member who has been linked to the wrong relative
     * must be able to undo it without finding an editor first, and doc 05 §4.10 says so.
     *
     * <p>The opt-in propagation flags go with the link. Leaving them would mean the next person
     * linked to the node inherits somebody else's choice about their own photo.
     */
    @Transactional
    public void unlink(UserId caller, ClanId clanId, PersonId personId) {
        LinkableNode node = linker.require(clanId, personId);
        boolean self = node.isLinkedTo(caller);

        AccessDecision authorizing = null;
        if (self) {
            // Their own link, and nothing else on the node. A permission would make "I am not this
            // person" a request rather than a correction.
            resolver.requireMembership(caller, clanId);
        } else {
            authorizing = resolver.require(caller, Permission.NODE_CLAIM_APPROVE, targetOf(node));
        }

        UserId previous = node.linkedUserId();
        Instant now = clock.instant();
        linker.unlink(clanId, personId, caller, now);
        propagation.clear(clanId, personId);

        if (previous == null) {
            // Nothing was attached. The caller asked for a state the world is already in.
            return;
        }

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.PERSON_UNLINKED,
                                clanId,
                                AuditRecord.TARGET_PERSON,
                                personId.value(),
                                caller)
                        .authorizedBy(authorizing == null ? null : authorizing.authorizingGrantId()));
        events.publishEvent(
                new PersonUnlinked(clanId, personId, node.fullName(), previous, caller, self, now));

        log.info(
                "Person unlinked clanId={} personId={} previousUser={} actor={} self={}",
                clanId,
                personId,
                previous,
                caller,
                self);
    }

    /**
     * The claiming candidates: live nodes with no account attached (B6.6, F6.2).
     *
     * <p>Every member may browse them, because reads are clan-wide (doc 04 §5.5) and because the
     * whole point is that somebody who holds nothing can find themselves. Each candidate carries its
     * parents and children, which is what lets a person tell two same-named relatives apart.
     */
    @Transactional(readOnly = true)
    public Paged<UnlinkedCandidateView> unlinkedCandidates(
            UserId caller,
            ClanId clanId,
            String text,
            Integer generation,
            BranchId branchId,
            int page,
            int size) {

        int bounded = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Paged<PersonSummaryView> found =
                lineage.unlinkedCandidates(caller, clanId, text, generation, branchId, page, bounded);

        List<PersonId> ids =
                found.content().stream().map(person -> PersonId.of(person.id())).toList();
        Map<PersonId, PersonLinkGateway.NodeContext> contexts = lineage.context(clanId, ids);

        List<UnlinkedCandidateView> content = new ArrayList<>(found.content().size());
        for (PersonSummaryView person : found.content()) {
            PersonLinkGateway.NodeContext context =
                    contexts.getOrDefault(
                            PersonId.of(person.id()), PersonLinkGateway.NodeContext.empty());
            content.add(
                    new UnlinkedCandidateView(
                            person, context.parentNames(), context.childNames()));
        }
        return new Paged<>(content, found.page(), found.size(), found.totalElements());
    }

    /**
     * The node the caller is recorded as in this clan, if any.
     *
     * <p>What the onboarding banner reads to decide whether to appear (F6.1), and what the tree uses
     * to put "this is you" on one card (F6.7).
     */
    @Transactional(readOnly = true)
    public Optional<LinkedNodeView> myNode(UserId caller, ClanId clanId) {
        resolver.requireMembership(caller, clanId);
        return linker.nodeOf(clanId, caller).map(node -> view(node, caller));
    }

    /** Who is on a node, for the detail drawer. Any member may ask; reads are clan-wide. */
    @Transactional(readOnly = true)
    public LinkedNodeView linkOf(UserId caller, ClanId clanId, PersonId personId) {
        resolver.requireMembership(caller, clanId);
        return view(linker.require(clanId, personId), caller);
    }

    // --- collaborators inside the context ------------------------------------------------------

    /**
     * Takes back the node of somebody whose membership has just ended (I-P5).
     *
     * <p>Package-private and unguarded for the reason {@code GrantService.revokeAllFor} is: the only
     * caller is {@link LinkLifecycleListener}, acting on a removal or a departure that was already
     * authorized. There is no permission to ask for here — the person leaving need hold none, and
     * the administrator removing them is not exercising {@code NODE_CLAIM_APPROVE}.
     *
     * <p>Everything else is {@link #unlink}: the propagation choices go with the link, the audit
     * trail says who caused it, and the event tells the person — unless they are the one who left.
     *
     * @return whether there was a node to take back
     */
    boolean releaseNodeOf(ClanId clanId, UserId member, UserId actor, boolean selfInitiated) {
        Optional<LinkableNode> held = linker.nodeOf(clanId, member);
        if (held.isEmpty()) {
            return false;
        }
        LinkableNode node = held.get();
        Instant now = clock.instant();
        linker.unlink(clanId, node.id(), actor, now);
        propagation.clear(clanId, node.id());

        auditWriter.write(
                AuditRecord.inClan(
                        AuditAction.PERSON_UNLINKED,
                        clanId,
                        AuditRecord.TARGET_PERSON,
                        node.id().value(),
                        actor));
        events.publishEvent(
                new PersonUnlinked(
                        clanId, node.id(), node.fullName(), member, actor, selfInitiated, now));

        log.info(
                "Person unlinked on membership end clanId={} personId={} previousUser={} actor={}",
                clanId,
                node.id(),
                member,
                actor);
        return true;
    }

    // --- internals -----------------------------------------------------------------------------

    private LinkedNodeView view(LinkableNode node, UserId caller) {
        UserProfile profile =
                node.linkedUserId() == null
                        ? null
                        : users.profiles(List.of(node.linkedUserId())).get(node.linkedUserId());

        return new LinkedNodeView(
                node.id().value(),
                node.fullName(),
                node.clanId().value(),
                node.linkedUserId() == null ? null : node.linkedUserId().value(),
                profile == null ? null : profile.displayName(),
                node.isLinkedTo(caller));
    }

    private static Target targetOf(LinkableNode node) {
        return Target.person(node.clanId(), node.id().value(), node.branchId());
    }
}
