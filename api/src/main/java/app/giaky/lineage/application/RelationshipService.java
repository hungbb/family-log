package app.giaky.lineage.application;

import app.giaky.authorization.domain.AccessDecision;
import app.giaky.authorization.domain.Permission;
import app.giaky.authorization.domain.PermissionResolver;
import app.giaky.authorization.domain.Target;
import app.giaky.common.audit.AuditAction;
import app.giaky.common.audit.AuditRecord;
import app.giaky.common.audit.AuditWriter;
import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.FieldViolation;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PartialDate;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.application.event.ParentageAdded;
import app.giaky.lineage.application.event.ParentageRemoved;
import app.giaky.lineage.application.event.UnionCreated;
import app.giaky.lineage.application.event.UnionRemoved;
import app.giaky.lineage.application.event.UnionUpdated;
import app.giaky.lineage.domain.Gender;
import app.giaky.lineage.domain.Parentage;
import app.giaky.lineage.domain.ParentRole;
import app.giaky.lineage.domain.ParentageRepository;
import app.giaky.lineage.domain.Person;
import app.giaky.lineage.domain.PersonRepository;
import app.giaky.lineage.domain.TreeVersionRepository;
import app.giaky.lineage.domain.Union;
import app.giaky.lineage.domain.UnionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The relationships between people: parentage edges and marriages (doc 05 §4.8).
 *
 * <p><b>Every operation here is multi-target</b>, and that is what makes this file the branch
 * isolation guarantee in practice. Doc 04 §5.2 says an edge needs {@code TREE_EDIT} on <i>all</i> of
 * its endpoints, because a branch editor who could link one of their own nodes to somebody else's
 * would be editing the other branch by writing into their own. Each method below builds a {@code
 * RelationshipTarget} naming every person involved, and {@code ScopeCoverage} refuses unless all of
 * them are covered.
 *
 * <p>Doc 04 §5.3's branch-root rule falls out of the same check rather than needing its own:
 * re-attaching a branch root means an edge whose other endpoint is the new parent, which sits in the
 * scope above — so a branch editor alone cannot re-hang their chi anywhere.
 *
 * <p>The writes themselves go through {@link LineageEdges}, which owns the order in which the edge,
 * the closure and the generation numbers are updated.
 */
@Service
public class RelationshipService {

    private static final Logger log = LoggerFactory.getLogger(RelationshipService.class);

    private final PersonRepository persons;
    private final ParentageRepository parentages;
    private final UnionRepository unions;
    private final LineageEdges edges;
    private final TreeVersionRepository treeVersions;
    private final PermissionResolver resolver;
    private final LineageSnapshots snapshots;
    private final AuditWriter auditWriter;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RelationshipService(
            PersonRepository persons,
            ParentageRepository parentages,
            UnionRepository unions,
            LineageEdges edges,
            TreeVersionRepository treeVersions,
            PermissionResolver resolver,
            LineageSnapshots snapshots,
            AuditWriter auditWriter,
            ApplicationEventPublisher events,
            Clock clock) {

        this.persons = persons;
        this.parentages = parentages;
        this.unions = unions;
        this.edges = edges;
        this.treeVersions = treeVersions;
        this.resolver = resolver;
        this.snapshots = snapshots;
        this.auditWriter = auditWriter;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Links a parent to a child (FR-P5, doc 05 §4.8).
     *
     * @throws DomainRuleViolation {@code 422 CYCLE_DETECTED} when the parent already sits below the
     *     child
     */
    @Transactional
    public ParentageView addParentage(UserId caller, ClanId clanId, AddParentageCommand command) {
        Person parent = require(clanId, command.parentPersonId());
        Person child = require(clanId, command.childPersonId());
        AccessDecision authorizing = requireEditOfAll(caller, clanId, parent, child);

        LineageEdges.EdgeResult result =
                edges.addParentage(
                        clanId,
                        parent.id(),
                        child.id(),
                        roleOf(command.parentRole(), parent),
                        command.nature(),
                        command.unionId(),
                        caller);

        Instant now = clock.instant();
        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.PARENTAGE_ADDED,
                                clanId,
                                AuditRecord.TARGET_PARENTAGE,
                                result.parentage().id(),
                                caller)
                        .withAfterJson(snapshots.of(result.parentage()))
                        .authorizedBy(authorizing.authorizingGrantId()));
        events.publishEvent(
                new ParentageAdded(
                        clanId,
                        result.parentage().id(),
                        parent.id(),
                        child.id(),
                        result.generationsRestamped(),
                        caller,
                        now));

        log.info(
                "Parentage added clanId={} parentId={} childId={} generationsRestamped={}",
                clanId,
                parent.id(),
                child.id(),
                result.generationsRestamped());

        return ParentageView.of(result.parentage());
    }

    /**
     * Unlinks a parent from a child (doc 05 §4.8).
     *
     * <p>Permission on both endpoints again, for the mirror-image reason: detaching a node from a
     * branch you do not edit is as much a change to that branch as attaching one.
     */
    @Transactional
    public void removeParentage(UserId caller, ClanId clanId, UUID parentageId) {
        Parentage edge =
                parentages
                        .findById(clanId, parentageId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "No such parent link."));

        Person parent = require(clanId, edge.parentPersonId());
        Person child = require(clanId, edge.childPersonId());
        AccessDecision authorizing = requireEditOfAll(caller, clanId, parent, child);

        String before = snapshots.of(edge);
        LineageEdges.EdgeResult result = edges.removeParentage(edge, caller);
        Instant now = clock.instant();

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.PARENTAGE_REMOVED,
                                clanId,
                                AuditRecord.TARGET_PARENTAGE,
                                parentageId,
                                caller)
                        .withChange(before, snapshots.of(result.parentage()))
                        .authorizedBy(authorizing.authorizingGrantId()));
        events.publishEvent(
                new ParentageRemoved(
                        clanId,
                        parentageId,
                        parent.id(),
                        child.id(),
                        result.generationsRestamped(),
                        caller,
                        now));
    }

    /**
     * Records a marriage (FR-P4, doc 05 §4.8).
     *
     * <p>{@code force} overrides I-N4 and needs {@code CLAN_ADMIN}. The check is here rather than in
     * {@link LineageEdges} because "who may override a data-entry guard" is a policy question, and
     * policy questions belong at the layer that holds the resolver.
     */
    @Transactional
    public UnionView createUnion(UserId caller, ClanId clanId, CreateUnionCommand command) {
        Person partnerA = require(clanId, command.partnerAId());
        Person partnerB = require(clanId, command.partnerBId());
        AccessDecision authorizing = requireEditOfAll(caller, clanId, partnerA, partnerB);

        requireOrdered(command.startDate(), command.endDate());

        if (command.force() && !resolver.can(caller, Permission.CLAN_ADMIN, Target.clan(clanId))) {
            throw new DomainRuleViolation(
                    ErrorCode.INVALID_RELATIONSHIP,
                    "Only an administrator can record a marriage between two people the tree already"
                            + " relates as ancestor and descendant.");
        }

        Union union =
                edges.createUnion(
                        clanId,
                        partnerA,
                        partnerB,
                        command.unionType(),
                        command.status(),
                        command.startDate(),
                        command.endDate(),
                        command.notes(),
                        command.force(),
                        caller);

        Instant now = clock.instant();
        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.UNION_CREATED,
                                clanId,
                                AuditRecord.TARGET_UNION,
                                union.id(),
                                caller)
                        .withAfterJson(snapshots.of(union))
                        .authorizedBy(authorizing.authorizingGrantId()));
        events.publishEvent(
                new UnionCreated(
                        clanId,
                        union.id(),
                        union.partnerAId(),
                        union.partnerBId(),
                        union.unionType().name(),
                        caller,
                        now));

        return UnionView.of(union);
    }

    /** Corrects a marriage record. The partners are not editable; see {@link UpdateUnionCommand}. */
    @Transactional
    public UnionView updateUnion(
            UserId caller, ClanId clanId, UUID unionId, UpdateUnionCommand command) {

        Union union = requireUnion(clanId, unionId);
        Person partnerA = require(clanId, union.partnerAId());
        Person partnerB = require(clanId, union.partnerBId());
        AccessDecision authorizing = requireEditOfAll(caller, clanId, partnerA, partnerB);

        requireOrdered(command.startDate(), command.endDate());

        Instant now = clock.instant();
        String before = snapshots.of(union);
        Union updated =
                unions.save(
                        union.update(
                                command.unionType(),
                                command.status(),
                                command.startDate(),
                                command.endDate(),
                                command.notes(),
                                caller,
                                now));
        treeVersions.bump(clanId);

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.UNION_UPDATED,
                                clanId,
                                AuditRecord.TARGET_UNION,
                                unionId,
                                caller)
                        .withChange(before, snapshots.of(updated))
                        .authorizedBy(authorizing.authorizingGrantId()));
        events.publishEvent(new UnionUpdated(clanId, unionId, caller, now));

        return UnionView.of(updated);
    }

    /**
     * Removes a marriage record (doc 05 §4.8).
     *
     * <p>Refused while parentage edges still point at it. Deleting it anyway would leave those edges
     * naming a union that no longer exists — the children would stay in the tree, correctly, but the
     * fact that they belong to <i>this</i> wife rather than that one would be gone, and that is
     * precisely what a book with several wives is recording.
     *
     * <p>Only children still in the tree count. A soft-deleted child is invisible to every read
     * (I-P7), so nobody can see — let alone detach — what would be blocking them (#50). Their edges
     * to the partners stay, so restoring the child still restores their parents; they just stop
     * naming the marriage, which no longer exists to be named.
     */
    @Transactional
    public void deleteUnion(UserId caller, ClanId clanId, UUID unionId) {
        Union union = requireUnion(clanId, unionId);
        Person partnerA = require(clanId, union.partnerAId());
        Person partnerB = require(clanId, union.partnerBId());
        AccessDecision authorizing = requireEditOfAll(caller, clanId, partnerA, partnerB);

        List<Parentage> attached = parentages.findByUnion(clanId, unionId);
        Set<PersonId> childIds =
                attached.stream().map(Parentage::childPersonId).collect(Collectors.toSet());
        List<Person> liveChildren = persons.findAllByIds(clanId, childIds);
        if (!liveChildren.isEmpty()) {
            String who =
                    liveChildren.size() == 1
                            ? "1 child is"
                            : liveChildren.size() + " children are";
            String names =
                    liveChildren.stream()
                            .map(Person::fullName)
                            .sorted()
                            .collect(Collectors.joining(", "));
            throw new DomainRuleViolation(
                    ErrorCode.INVALID_RELATIONSHIP,
                    who
                            + " recorded as belonging to this marriage ("
                            + names
                            + "). Detach them first if the record is wrong.");
        }

        Instant now = clock.instant();
        for (Parentage edge : attached) {
            String edgeBefore = snapshots.of(edge);
            Parentage detached = parentages.save(edge.withoutUnion(caller, now));
            auditWriter.write(
                    AuditRecord.inClan(
                                    AuditAction.PARENTAGE_DETACHED_FROM_UNION,
                                    clanId,
                                    AuditRecord.TARGET_PARENTAGE,
                                    edge.id(),
                                    caller)
                            .withChange(edgeBefore, snapshots.of(detached))
                            .authorizedBy(authorizing.authorizingGrantId()));
        }

        String before = snapshots.of(union);
        Union removed = unions.save(union.remove(caller, now));
        treeVersions.bump(clanId);

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.UNION_REMOVED,
                                clanId,
                                AuditRecord.TARGET_UNION,
                                unionId,
                                caller)
                        .withChange(before, snapshots.of(removed))
                        .authorizedBy(authorizing.authorizingGrantId()));
        events.publishEvent(new UnionRemoved(clanId, unionId, caller, now));
    }

    /**
     * Attaches an existing person as a child of a marriage (doc 05 §4.8).
     *
     * <p>Three targets: the child and both partners (doc 04 §5.2). Writes one edge per partner, both
     * carrying the union id, which is what lets the tree group children under the marriage they came
     * from rather than under a father with two wives and one undifferentiated list.
     *
     * <p>A partner who is already recorded as this child's parent is skipped rather than refused —
     * attaching a child whose father was already linked is a completion, not a conflict.
     */
    @Transactional
    public List<ParentageView> attachChildToUnion(
            UserId caller, ClanId clanId, UUID unionId, PersonId childPersonId) {

        Union union = requireUnion(clanId, unionId);
        Person partnerA = require(clanId, union.partnerAId());
        Person partnerB = require(clanId, union.partnerBId());
        Person child = require(clanId, childPersonId);
        AccessDecision authorizing = requireEditOfAll(caller, clanId, partnerA, partnerB, child);

        Person father = ParentRole.fatherAmong(partnerA, partnerB);
        Person mother = father.id().equals(partnerA.id()) ? partnerB : partnerA;

        Instant now = clock.instant();
        List<ParentageView> created = new java.util.ArrayList<>();
        for (Person parent : List.of(father, mother)) {
            ParentRole role = parent.id().equals(father.id()) ? ParentRole.FATHER : ParentRole.MOTHER;
            if (parentages.findEdge(clanId, child.id(), role).isPresent()) {
                continue;
            }
            LineageEdges.EdgeResult result =
                    edges.addParentage(
                            clanId,
                            parent.id(),
                            child.id(),
                            role,
                            app.giaky.lineage.domain.ParentageNature.BIOLOGICAL,
                            unionId,
                            caller);
            created.add(ParentageView.of(result.parentage()));

            auditWriter.write(
                    AuditRecord.inClan(
                                    AuditAction.PARENTAGE_ADDED,
                                    clanId,
                                    AuditRecord.TARGET_PARENTAGE,
                                    result.parentage().id(),
                                    caller)
                            .withAfterJson(snapshots.of(result.parentage()))
                            .authorizedBy(authorizing.authorizingGrantId()));
            events.publishEvent(
                    new ParentageAdded(
                            clanId,
                            result.parentage().id(),
                            parent.id(),
                            child.id(),
                            result.generationsRestamped(),
                            caller,
                            now));
        }
        return List.copyOf(created);
    }

    // --- internals -----------------------------------------------------------------------------

    /**
     * Doc 04 §5.2 in one call: {@code TREE_EDIT} over every person the operation touches.
     *
     * <p>One {@code RelationshipTarget} rather than a {@code require} per person, so a refusal names
     * the operation instead of whichever endpoint happened to be checked first — and so that the
     * "all of them" rule is expressed once, where {@code ScopeCoverage} can enforce it, rather than
     * assembled by each caller.
     */
    private AccessDecision requireEditOfAll(
            UserId caller, ClanId clanId, Person... involved) {

        List<Target.PersonTarget> endpoints =
                java.util.Arrays.stream(involved)
                        .map(
                                person ->
                                        new Target.PersonTarget(
                                                clanId, person.id().value(), person.branchId()))
                        .toList();
        return resolver.require(
                caller, Permission.TREE_EDIT, Target.relationship(clanId, endpoints));
    }

    /**
     * A marriage cannot end before it began — {@link Union}'s own invariant, answered as {@code 422
     * DATE_RANGE_INVALID} rather than as the {@code IllegalArgumentException} the record throws,
     * which reached the client as a 500.
     */
    private static void requireOrdered(PartialDate startDate, PartialDate endDate) {
        PartialDate start = startDate == null ? PartialDate.unknown() : startDate;
        PartialDate end = endDate == null ? PartialDate.unknown() : endDate;
        if (start.isCertainlyAfter(end)) {
            throw new DomainRuleViolation(
                    ErrorCode.DATE_RANGE_INVALID,
                    "A marriage cannot end before it began.",
                    List.of(new FieldViolation("endDate", "must not be before the start date")));
        }
    }

    private static ParentRole roleOf(ParentRole requested, Person parent) {
        if (requested != null) {
            return requested;
        }
        return parent.gender() == Gender.FEMALE ? ParentRole.MOTHER : ParentRole.FATHER;
    }

    private Person require(ClanId clanId, PersonId personId) {
        if (personId == null) {
            throw new NotFoundException(ErrorCode.PERSON_NOT_FOUND, "No such person.");
        }
        return persons.findById(clanId, personId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        ErrorCode.PERSON_NOT_FOUND, "No such person."));
    }

    private Union requireUnion(ClanId clanId, UUID unionId) {
        return unions.findById(clanId, unionId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        ErrorCode.RESOURCE_NOT_FOUND, "No such marriage record."));
    }
}
