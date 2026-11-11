package app.giaky.lineage.application;

import app.giaky.authorization.domain.AccessDecision;
import app.giaky.authorization.domain.EditableSet;
import app.giaky.authorization.domain.Permission;
import app.giaky.authorization.domain.PermissionResolver;
import app.giaky.authorization.domain.Target;
import app.giaky.clanorg.application.BranchService;
import app.giaky.clanorg.application.ClanDirectory;
import app.giaky.common.audit.AuditAction;
import app.giaky.common.audit.AuditRecord;
import app.giaky.common.audit.AuditWriter;
import app.giaky.common.error.ConflictException;
import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.error.FieldViolation;
import app.giaky.common.error.NotFoundException;
import app.giaky.common.error.PermissionDeniedException;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.IdGenerator;
import app.giaky.common.types.Paged;
import app.giaky.common.types.PartialDate;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.application.event.PersonCreated;
import app.giaky.lineage.application.event.PersonMovedToBranch;
import app.giaky.lineage.application.event.PersonRestored;
import app.giaky.lineage.application.event.PersonSoftDeleted;
import app.giaky.lineage.application.event.PersonUpdated;
import app.giaky.lineage.domain.AncestryProjection;
import app.giaky.lineage.domain.Gender;
import app.giaky.lineage.domain.LifeStatus;
import app.giaky.lineage.domain.LineageGraphService;
import app.giaky.lineage.domain.NoteSanitizer;
import app.giaky.lineage.domain.Parentage;
import app.giaky.lineage.domain.ParentRole;
import app.giaky.lineage.domain.Person;
import app.giaky.lineage.domain.PersonName;
import app.giaky.lineage.domain.PersonRepository;
import app.giaky.lineage.domain.TreeVersionRepository;
import app.giaky.lineage.domain.Union;
import app.giaky.lineage.domain.UnionRepository;
import app.giaky.lineage.domain.UnionStatus;
import app.giaky.lineage.domain.UnionType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The person node's life cycle: create with a placement, read, edit, move, soft-delete, restore
 * (FR-P1…FR-P11).
 *
 * <p><b>Placement is the idea this service is organized around.</b> Doc 05 §4.7 makes it part of the
 * create request rather than a follow-up call, and phase 4 §8 says why: a node created loose has no
 * branch, and a node with no branch is outside every {@code BRANCH}-scoped grant. Deriving the
 * branch and the permission target from the same field, in the same transaction, is what keeps the
 * branch-isolation guarantee (G5) whole — otherwise "create unplaced, then attach" is a two-step
 * route into a chi the actor cannot edit.
 *
 * <p>Every mutation here bumps the clan's tree version, so a client holding an {@code ETag} for the
 * tree revalidates rather than rendering a stale one (B4.12).
 */
@Service
public class PersonService {

    private static final Logger log = LoggerFactory.getLogger(PersonService.class);

    /**
     * How long a soft-deleted person can be restored for (doc 05 §4.7).
     *
     * <p>Thirty days, matching the clan's own recovery window. After it, the row stays — nothing in
     * this product hard-deletes a person — but the restore endpoint stops offering it, because a
     * node somebody removed a year ago coming back is a surprise rather than a recovery.
     */
    public static final Duration RESTORE_WINDOW = Duration.ofDays(30);

    private final PersonRepository persons;
    private final UnionRepository unions;
    private final AncestryProjection ancestry;
    private final LineageGraphService graph;
    private final LineageEdges edges;
    private final TreeVersionRepository treeVersions;
    private final NoteSanitizer sanitizer;
    private final PermissionResolver resolver;
    private final ClanDirectory clans;
    private final BranchService branchService;
    private final LineageSnapshots snapshots;
    private final AuditWriter auditWriter;
    private final ApplicationEventPublisher events;
    private final IdGenerator ids;
    private final Clock clock;

    public PersonService(
            PersonRepository persons,
            UnionRepository unions,
            AncestryProjection ancestry,
            LineageGraphService graph,
            LineageEdges edges,
            TreeVersionRepository treeVersions,
            NoteSanitizer sanitizer,
            PermissionResolver resolver,
            ClanDirectory clans,
            BranchService branchService,
            LineageSnapshots snapshots,
            AuditWriter auditWriter,
            ApplicationEventPublisher events,
            IdGenerator ids,
            Clock clock) {

        this.persons = persons;
        this.unions = unions;
        this.ancestry = ancestry;
        this.graph = graph;
        this.edges = edges;
        this.treeVersions = treeVersions;
        this.sanitizer = sanitizer;
        this.resolver = resolver;
        this.clans = clans;
        this.branchService = branchService;
        this.snapshots = snapshots;
        this.auditWriter = auditWriter;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
    }

    // --- reads ---------------------------------------------------------------------------------

    /**
     * One person, with their parents, marriages, children and siblings (FR-T6).
     *
     * <p>Reads are clan-wide (doc 04 §5.5): a member who can open the clan sees all of it, because a
     * <i>gia phả</i> is meaningless in fragments. Branch and subtree scopes constrain writes, and
     * {@code canEdit} on each returned card is where that shows up.
     */
    @Transactional(readOnly = true)
    public PersonDetailView detail(UserId caller, ClanId clanId, PersonId personId) {
        resolver.requireMembership(caller, clanId);
        Person person = require(clanId, personId);
        return assembleDetail(caller, clanId, person);
    }

    /** Search and filter (FR-P11, B4.11). Diacritic-insensitive; see {@code PersonQuery}. */
    @Transactional(readOnly = true)
    public Paged<PersonSummaryView> search(
            UserId caller, ClanId clanId, PersonRepository.PersonQuery query) {

        resolver.requireMembership(caller, clanId);
        PersonRepository.PersonPage page = persons.search(clanId, query);
        EditableSet editable = resolver.editableSet(caller, clanId);

        List<PersonSummaryView> content =
                page.content().stream().map(person -> summary(person, editable)).toList();
        return new Paged<>(content, query.page(), query.size(), page.total());
    }

    /**
     * The recently deleted, most recent first (F4.9).
     *
     * <p>{@code CLAN_ADMIN}, because restoring is, and a list of what can be restored is a list of
     * what somebody chose to remove. A branch editor who deleted a duplicate asks an administrator to
     * put it back — which is also the moment the two of them notice they disagreed.
     */
    @Transactional(readOnly = true)
    public List<PersonSummaryView> recentlyDeleted(UserId caller, ClanId clanId) {
        resolver.require(caller, Permission.CLAN_ADMIN, Target.clan(clanId));
        Instant since = clock.instant().minus(RESTORE_WINDOW);
        EditableSet editable = resolver.editableSet(caller, clanId);
        return persons.findDeletedSince(clanId, since).stream()
                .map(person -> summary(person, editable))
                .toList();
    }

    // --- writes --------------------------------------------------------------------------------

    /**
     * Adds a person, attached where the placement says (FR-P1, doc 05 §4.7).
     *
     * <p>The permission check names the <i>anchor</i>, not the new node — the new node has no branch
     * to be checked against until this method gives it one. A child of a person in Chi Hai is a
     * question about Chi Hai; an unplaced node is a question about the clan.
     *
     * @throws PermissionDeniedException the caller cannot edit where the placement points
     * @throws ConflictException the clan already has a founding ancestor (I-P6)
     */
    @Transactional
    public PersonDetailView create(UserId caller, ClanId clanId, CreatePersonCommand command) {
        ResolvedPlacement placement = resolvePlacement(clanId, command.placement());
        AccessDecision authorizing =
                resolver.require(caller, Permission.TREE_EDIT, placement.target());

        if (command.foundingAncestor()) {
            requireNoFoundingAncestorYet(clanId);
        }
        requireCoherentDates(command.lifeStatus(), command.birth(), command.death());

        Instant now = clock.instant();
        NoteSanitizer.SanitizedNote note = sanitizer.sanitize(command.notesHtml());

        Person person =
                persons.save(
                        Person.create(
                                PersonId.of(ids.newId()),
                                clanId,
                                placement.branchId(),
                                new PersonName(
                                        command.fullName(),
                                        command.familyName(),
                                        command.givenName(),
                                        command.alias(),
                                        command.posthumousName()),
                                command.gender(),
                                command.lifeStatus(),
                                command.birth(),
                                command.death(),
                                command.burialPlace(),
                                command.birthOrder(),
                                note.html(),
                                note.text(),
                                command.foundingAncestor(),
                                caller,
                                now));

        // Before any edge: the cross product an edge writes is built from this row, and an edge
        // added to a person with no self-row inserts nothing at all.
        ancestry.personCreated(clanId, person.id());

        person = applyPlacement(caller, clanId, person, command, placement);
        treeVersions.bump(clanId);

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.PERSON_CREATED,
                                clanId,
                                AuditRecord.TARGET_PERSON,
                                person.id().value(),
                                caller)
                        .withAfterJson(snapshots.of(person))
                        .authorizedBy(authorizing.authorizingGrantId()));
        events.publishEvent(
                new PersonCreated(
                        clanId, person.id(), person.branchId(), person.fullName(), caller, now));

        log.info(
                "Person created clanId={} personId={} placement={} branchId={}",
                clanId,
                person.id(),
                command.placement().type(),
                person.branchId());

        return assembleDetail(caller, clanId, person);
    }

    /**
     * Replaces a person's attributes ({@code PUT}, doc 05 §4.7).
     *
     * <p>A null field clears it. That is what {@code PUT} means, and a client that did not intend it
     * should be calling {@link #patch}.
     */
    @Transactional
    public PersonDetailView replace(
            UserId caller, ClanId clanId, PersonId personId, UpdatePersonCommand command) {
        return write(caller, clanId, personId, command, false);
    }

    /** Merges a person's attributes ({@code PATCH}): a null field is left alone. */
    @Transactional
    public PersonDetailView patch(
            UserId caller, ClanId clanId, PersonId personId, UpdatePersonCommand command) {
        return write(caller, clanId, personId, command, true);
    }

    /**
     * Hides a person from the tree (FR-P10).
     *
     * <p>Refused when it would leave children with no other recorded parent <i>and</i> those children
     * are outside the actor's own scope (doc 05 §4.7). The rule is narrower than "never orphan
     * anybody" on purpose: a branch editor tidying their own chi may well strand a node they are
     * responsible for, and blocking that would make the feature unusable — but stranding somebody
     * else's is a change to a part of the tree they were not granted.
     */
    @Transactional
    public void softDelete(UserId caller, ClanId clanId, PersonId personId) {
        Person person = require(clanId, personId);
        AccessDecision authorizing =
                resolver.require(caller, Permission.TREE_EDIT, targetFor(person));

        List<Person> stranded = graph.childrenLeftWithoutAnotherParent(clanId, personId);
        EditableSet editable = resolver.editableSet(caller, clanId);
        List<String> outOfReach =
                stranded.stream()
                        .filter(child -> !editable.contains(child.id().value(), child.branchId()))
                        .map(Person::fullName)
                        .toList();
        if (!outOfReach.isEmpty()) {
            throw new DomainRuleViolation(
                    ErrorCode.INVALID_RELATIONSHIP,
                    "Removing this person would leave "
                            + String.join(", ", outOfReach)
                            + " with no recorded parent, and they are outside the part of the tree"
                            + " you can edit.");
        }

        Instant now = clock.instant();
        String before = snapshots.of(person);
        Person deleted = persons.save(person.softDelete(caller, now));
        treeVersions.bump(clanId);

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.PERSON_DELETED,
                                clanId,
                                AuditRecord.TARGET_PERSON,
                                personId.value(),
                                caller)
                        .withChange(before, snapshots.of(deleted))
                        .authorizedBy(authorizing.authorizingGrantId()));
        events.publishEvent(new PersonSoftDeleted(clanId, personId, caller, now));
    }

    /**
     * Brings a soft-deleted person back (FR-P10).
     *
     * <p>Nothing has to be rebuilt: the edges and the closure rows were never removed, so this is a
     * timestamp. That is the property soft delete was designed for, and the reason the closure
     * describes the edge graph rather than what is visible (V9).
     *
     * <p><b>With one exception: the account that was on the node.</b> A deleted node keeps its link,
     * so that undoing a mistaken delete undoes all of it. But a deleted node is also invisible to
     * I-P4 — its index covers live rows only — so while it was gone the same account may have been
     * given another node, or have left the clan altogether. Restoring it as it was would then put
     * two nodes on one account (the unique index refuses, which used to surface as a {@code 500})
     * or a non-member on the tree (I-P5). In both cases the node comes back unlinked, and the audit
     * trail says so; the account loses nothing it still had.
     */
    @Transactional
    public PersonDetailView restore(UserId caller, ClanId clanId, PersonId personId) {
        AccessDecision authorizing =
                resolver.require(caller, Permission.CLAN_ADMIN, Target.clan(clanId));

        Person person =
                persons.findByIdIncludingDeleted(clanId, personId)
                        .orElseThrow(PersonService::noSuchPerson);
        if (!person.isDeleted()) {
            return assembleDetail(caller, clanId, person);
        }
        Instant now = clock.instant();
        if (person.deletedAt().isBefore(now.minus(RESTORE_WINDOW))) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED,
                    "This was removed more than "
                            + RESTORE_WINDOW.toDays()
                            + " days ago and can no longer be restored from here.");
        }

        String before = snapshots.of(person);
        boolean linkLapsed = linkLapsedWhileDeleted(clanId, person);
        Person revived = person.restore(caller, now);
        Person restored = persons.save(linkLapsed ? revived.withoutLink() : revived);
        treeVersions.bump(clanId);

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.PERSON_RESTORED,
                                clanId,
                                AuditRecord.TARGET_PERSON,
                                personId.value(),
                                caller)
                        .withChange(before, snapshots.of(restored))
                        .authorizedBy(authorizing.authorizingGrantId()));
        if (linkLapsed) {
            // Its own entry: the snapshots above leave the link out on purpose, so without this
            // nothing would record that the node came back without the account it left with.
            auditWriter.write(
                    AuditRecord.inClan(
                                    AuditAction.PERSON_UNLINKED,
                                    clanId,
                                    AuditRecord.TARGET_PERSON,
                                    personId.value(),
                                    caller)
                            .authorizedBy(authorizing.authorizingGrantId()));
            log.info(
                    "Person restored without its former link clanId={} personId={} previousUser={}",
                    clanId,
                    personId,
                    person.linkedUserId());
        }
        events.publishEvent(new PersonRestored(clanId, personId, caller, now));

        return assembleDetail(caller, clanId, restored);
    }

    /**
     * Whether the account on a deleted node can no longer be on it (I-P4, I-P5).
     *
     * <p>Two things can have happened while the node was out of sight: the account was linked to
     * another node of this clan, or its membership ended. Either makes the old link one that {@code
     * PersonUserLinker} would refuse to create today, so restore does not recreate it.
     */
    private boolean linkLapsedWhileDeleted(ClanId clanId, Person deleted) {
        UserId account = deleted.linkedUserId();
        if (account == null) {
            return false;
        }
        return !clans.isActiveMember(clanId, account)
                || persons.findByLinkedUser(clanId, account).isPresent();
    }

    /**
     * Moves a person to another branch (FR-P8, doc 05 §4.7).
     *
     * <p>Two checks, not one (doc 04 §5.2): the person, and the destination. Without the second, a
     * branch editor could move one of their own nodes into somebody else's chi — which is not an
     * edit to their branch, it is an edit to the other one.
     */
    @Transactional
    public PersonDetailView moveToBranch(
            UserId caller, ClanId clanId, PersonId personId, BranchId branchId) {

        Person person = require(clanId, personId);
        AccessDecision authorizing =
                resolver.require(caller, Permission.TREE_EDIT, targetFor(person));
        resolver.require(caller, Permission.TREE_EDIT, Target.branch(clanId, branchId));
        requireBranchOfThisClan(clanId, branchId);

        if (Objects.equals(person.branchId(), branchId)) {
            return assembleDetail(caller, clanId, person);
        }

        Instant now = clock.instant();
        String before = snapshots.of(person);
        BranchId previous = person.branchId();
        Person moved = persons.save(person.movedTo(branchId, caller, now));
        treeVersions.bump(clanId);

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.PERSON_MOVED_TO_BRANCH,
                                clanId,
                                AuditRecord.TARGET_PERSON,
                                personId.value(),
                                caller)
                        .withChange(before, snapshots.of(moved))
                        .authorizedBy(authorizing.authorizingGrantId()));
        events.publishEvent(
                new PersonMovedToBranch(clanId, personId, previous, branchId, caller, now));

        return assembleDetail(caller, clanId, moved);
    }

    // --- internals -----------------------------------------------------------------------------

    private PersonDetailView write(
            UserId caller,
            ClanId clanId,
            PersonId personId,
            UpdatePersonCommand command,
            boolean merge) {

        Person person = require(clanId, personId);
        AccessDecision authorizing =
                resolver.require(caller, Permission.TREE_EDIT, targetFor(person));
        requireVersion(person.version(), command.expectedVersion());

        Instant now = clock.instant();
        String before = snapshots.of(person);

        NoteSanitizer.SanitizedNote note =
                merge && command.notesHtml() == null
                        ? new NoteSanitizer.SanitizedNote(person.notesHtml(), person.notesText())
                        : sanitizer.sanitize(command.notesHtml());

        String fullName = pick(command.fullName(), person.name().fullName(), merge);
        if (fullName == null || fullName.isBlank()) {
            // Only PUT gets here with nothing: it replaces, so a body without a name is a request
            // to clear it — and a person without a name is not a record (I-P1).
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED,
                    "A person needs a name.",
                    List.of(new FieldViolation("fullName", "must not be blank")));
        }
        LifeStatus lifeStatus = pick(command.lifeStatus(), person.lifeStatus(), merge);
        PartialDate birth = pick(command.birth(), person.birth(), merge);
        PartialDate death = pick(command.death(), person.death(), merge);
        requireCoherentDates(lifeStatus, birth, death);

        PersonName name =
                new PersonName(
                        fullName,
                        pick(command.familyName(), person.name().familyName(), merge),
                        pick(command.givenName(), person.name().givenName(), merge),
                        pick(command.alias(), person.name().alias(), merge),
                        pick(command.posthumousName(), person.name().posthumousName(), merge));

        Person updated =
                persons.save(
                        person.update(
                                name,
                                pick(command.gender(), person.gender(), merge),
                                lifeStatus,
                                birth,
                                death,
                                pick(command.burialPlace(), person.burialPlace(), merge),
                                pick(command.birthOrder(), person.birthOrder(), merge),
                                note.html(),
                                note.text(),
                                caller,
                                now));
        treeVersions.bump(clanId);

        auditWriter.write(
                AuditRecord.inClan(
                                AuditAction.PERSON_UPDATED,
                                clanId,
                                AuditRecord.TARGET_PERSON,
                                personId.value(),
                                caller)
                        .withChange(before, snapshots.of(updated))
                        .authorizedBy(authorizing.authorizingGrantId()));
        events.publishEvent(new PersonUpdated(clanId, personId, caller, now));

        return assembleDetail(caller, clanId, updated);
    }

    /**
     * I-P2 and I-P3, answered in the API's vocabulary before the aggregate has to refuse them.
     *
     * <p>{@link Person} enforces both itself, as it must. But an aggregate refuses by throwing
     * {@code IllegalArgumentException} — right for a domain object, and wrong for a client to
     * receive: it surfaced as {@code 500 INTERNAL_ERROR} for what is an ordinary slip on a form, a
     * status changed to "living" with the date of death left in. The questions asked here are the
     * value object's own ({@code isKnown}, {@code isCertainlyAfter}), so each rule still has one
     * definition; this only decides what the refusal is called.
     */
    private static void requireCoherentDates(
            LifeStatus lifeStatus, PartialDate birth, PartialDate death) {

        PartialDate born = birth == null ? PartialDate.unknown() : birth;
        PartialDate died = death == null ? PartialDate.unknown() : death;

        if (lifeStatus == LifeStatus.ALIVE && died.isKnown()) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED,
                    "Somebody recorded as living cannot have a date of death. Change the status,"
                            + " or clear the date.",
                    List.of(
                            new FieldViolation(
                                    "death", "a living person cannot have a date of death")));
        }
        if (born.isCertainlyAfter(died)) {
            throw new DomainRuleViolation(
                    ErrorCode.DATE_RANGE_INVALID,
                    "The date of death is before the date of birth.",
                    List.of(new FieldViolation("death", "must not be before the date of birth")));
        }
    }

    /**
     * The {@code PUT}/{@code PATCH} difference, in one place.
     *
     * <p>{@code merge} is {@code PATCH}: a null means "no opinion", so the stored value stands.
     * Without it, null means "clear this", which is what {@code PUT} promises the client.
     */
    private static <T> T pick(T incoming, T current, boolean merge) {
        return merge && incoming == null ? current : incoming;
    }

    /**
     * Turns a placement into the two things the create path needs: what to check, and where the node
     * lands.
     *
     * <p>Both come from the same field, and that is the point (phase 4 §8). A branch derived from
     * one thing and a permission checked against another is exactly how a node ends up somewhere the
     * actor could not have put it.
     */
    private ResolvedPlacement resolvePlacement(ClanId clanId, PersonPlacement placement) {
        return switch (placement.type()) {
            case CHILD_OF_UNION -> {
                Union union = requireUnion(clanId, placement.unionId());
                Person partnerA = require(clanId, union.partnerAId());
                Person partnerB = require(clanId, union.partnerBId());
                yield new ResolvedPlacement(
                        Target.relationship(
                                clanId,
                                List.of(
                                        personTarget(partnerA),
                                        personTarget(partnerB))),
                        // Patrilineal by default, because that is how the books are organized: a
                        // child belongs to the father's chi. Falls back to the other partner when
                        // the father has no placement of his own.
                        inheritedBranch(partnerA, partnerB),
                        union,
                        partnerA,
                        partnerB);
            }
            case CHILD_OF_PARENT, SPOUSE_OF -> {
                Person anchor = require(clanId, requireAnchor(placement));
                yield new ResolvedPlacement(
                        personTarget(anchor), anchor.branchId(), null, anchor, null);
            }
            case BRANCH_ROOT -> {
                BranchId branchId = placement.branchId();
                if (branchId == null) {
                    throw new DomainRuleViolation(
                            ErrorCode.VALIDATION_FAILED, "Choose a branch for this person.");
                }
                requireBranchOfThisClan(clanId, branchId);
                yield new ResolvedPlacement(
                        Target.branch(clanId, branchId), branchId, null, null, null);
            }
            // No branch, and therefore no branch-scoped grant reaches it — so the check is the clan
            // as a whole (phase 4 §8). This is the hole in G5 if it is ever relaxed.
            case UNPLACED ->
                    new ResolvedPlacement(Target.clan(clanId), null, null, null, null);
        };
    }

    /** Writes whatever edges the placement implies, after the person row exists. */
    private Person applyPlacement(
            UserId caller,
            ClanId clanId,
            Person person,
            CreatePersonCommand command,
            ResolvedPlacement placement) {

        switch (command.placement().type()) {
            case CHILD_OF_UNION -> {
                Person father = fatherOf(placement.anchor(), placement.secondAnchor());
                Person mother =
                        father.id().equals(placement.anchor().id())
                                ? placement.secondAnchor()
                                : placement.anchor();
                edges.addParentage(
                        clanId,
                        father.id(),
                        person.id(),
                        ParentRole.FATHER,
                        command.nature(),
                        placement.union().id(),
                        caller);
                edges.addParentage(
                        clanId,
                        mother.id(),
                        person.id(),
                        ParentRole.MOTHER,
                        command.nature(),
                        placement.union().id(),
                        caller);
            }
            case CHILD_OF_PARENT ->
                    edges.addParentage(
                            clanId,
                            placement.anchor().id(),
                            person.id(),
                            roleOf(command.parentRole(), placement.anchor()),
                            command.nature(),
                            null,
                            caller);
            case SPOUSE_OF ->
                    edges.createUnion(
                            clanId,
                            placement.anchor(),
                            person,
                            UnionType.PRIMARY,
                            UnionStatus.UNKNOWN,
                            PartialDate.unknown(),
                            PartialDate.unknown(),
                            null,
                            // A brand-new node cannot be anybody's ancestor, so I-N4 has nothing to
                            // object to and there is nothing for a force flag to override.
                            false,
                            caller);
            case BRANCH_ROOT ->
                    branchService.assignRootPerson(
                            caller, clanId, placement.branchId(), person.id().value());
            case UNPLACED -> {
                // Nothing to attach. The node exists and waits for somebody to place it.
            }
        }
        // Re-read: the edge writes restamped generations through a bulk update, so the row in hand
        // is one version behind the database on exactly the field the caller will render.
        return persons.findById(clanId, person.id()).orElse(person);
    }

    /** The couple's father — {@link ParentRole#fatherAmong}, the one definition of that rule. */
    private static Person fatherOf(Person partnerA, Person partnerB) {
        return ParentRole.fatherAmong(partnerA, partnerB);
    }

    private static ParentRole roleOf(ParentRole requested, Person parent) {
        if (requested != null) {
            return requested;
        }
        return parent.gender() == Gender.FEMALE ? ParentRole.MOTHER : ParentRole.FATHER;
    }

    private static BranchId inheritedBranch(Person partnerA, Person partnerB) {
        Person father = fatherOf(partnerA, partnerB);
        Person other = father.id().equals(partnerA.id()) ? partnerB : partnerA;
        return father.branchId() != null ? father.branchId() : other.branchId();
    }

    private PersonDetailView assembleDetail(UserId caller, ClanId clanId, Person person) {
        EditableSet editable = resolver.editableSet(caller, clanId);

        List<Parentage> parentEdges = graph.parentEdgesOf(clanId, person.id());
        Map<PersonId, Person> parentsById = byId(clanId, parentEdges);
        List<PersonDetailView.ParentLink> parents = new ArrayList<>();
        for (Parentage edge : parentEdges) {
            Person parent = parentsById.get(edge.parentPersonId());
            if (parent != null) {
                parents.add(
                        new PersonDetailView.ParentLink(
                                edge.id(),
                                edge.parentRole(),
                                edge.nature(),
                                summary(parent, editable)));
            }
        }

        List<PersonDetailView.UnionLink> unionLinks = new ArrayList<>();
        for (Union union : unions.findByPartner(clanId, person.id())) {
            persons.findById(clanId, union.partnerOf(person.id()))
                    .ifPresent(
                            partner ->
                                    unionLinks.add(
                                            new PersonDetailView.UnionLink(
                                                    UnionView.of(union),
                                                    summary(partner, editable))));
        }

        List<PersonSummaryView> children =
                graph.childrenOf(clanId, person.id()).stream()
                        .map(child -> summary(child, editable))
                        .toList();
        List<PersonSummaryView> siblings =
                graph.siblingsOf(clanId, person.id()).stream()
                        .map(sibling -> summary(sibling, editable))
                        .toList();

        return PersonDetailView.of(
                person,
                editable.contains(person.id().value(), person.branchId()),
                parents,
                unionLinks,
                children,
                siblings);
    }

    private Map<PersonId, Person> byId(ClanId clanId, List<Parentage> parentEdges) {
        List<PersonId> parentIds = parentEdges.stream().map(Parentage::parentPersonId).toList();
        Map<PersonId, Person> byId = new HashMap<>();
        for (Person parent : persons.findAllByIds(clanId, parentIds)) {
            byId.put(parent.id(), parent);
        }
        return byId;
    }

    static PersonSummaryView summary(Person person, EditableSet editable) {
        return PersonSummaryView.of(
                person, editable.contains(person.id().value(), person.branchId()));
    }

    private static Target.PersonTarget personTarget(Person person) {
        return new Target.PersonTarget(person.clanId(), person.id().value(), person.branchId());
    }

    private static Target targetFor(Person person) {
        return personTarget(person);
    }

    private Person require(ClanId clanId, PersonId personId) {
        if (personId == null) {
            throw noSuchPerson();
        }
        return persons.findById(clanId, personId).orElseThrow(PersonService::noSuchPerson);
    }

    private Union requireUnion(ClanId clanId, java.util.UUID unionId) {
        if (unionId == null) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED, "Choose the marriage this child belongs to.");
        }
        return unions.findById(clanId, unionId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        ErrorCode.RESOURCE_NOT_FOUND, "No such marriage record."));
    }

    private static PersonId requireAnchor(PersonPlacement placement) {
        PersonId anchor = placement.anchorPersonId();
        if (anchor == null) {
            throw new DomainRuleViolation(
                    ErrorCode.VALIDATION_FAILED,
                    "Choose the person this one attaches to.");
        }
        return anchor;
    }

    /** I-P8, and the 404 a branch from another clan gets. */
    private void requireBranchOfThisClan(ClanId clanId, BranchId branchId) {
        Optional<ClanDirectory.BranchRef> branch = clans.branch(clanId, branchId);
        if (branch.isEmpty()) {
            throw new NotFoundException(ErrorCode.BRANCH_NOT_FOUND, "No such branch.");
        }
    }

    /** I-P6, checked before the partial unique index has to. */
    private void requireNoFoundingAncestorYet(ClanId clanId) {
        persons.findFoundingAncestor(clanId)
                .ifPresent(
                        existing -> {
                            throw new ConflictException(
                                    ErrorCode.VERSION_CONFLICT,
                                    existing.fullName()
                                            + " is already recorded as this family's founding"
                                            + " ancestor.");
                        });
    }

    private static NotFoundException noSuchPerson() {
        return new NotFoundException(ErrorCode.PERSON_NOT_FOUND, "No such person.");
    }

    static void requireVersion(long actual, long expected) {
        if (actual != expected) {
            throw new ConflictException(
                    ErrorCode.VERSION_CONFLICT,
                    "This changed since you loaded it. Reload and try again.");
        }
    }

    /**
     * A placement, resolved into what the create path acts on.
     *
     * @param target what {@code TREE_EDIT} is checked against
     * @param branchId the branch the new node inherits; null for an unplaced one
     * @param union the marriage, for a {@code CHILD_OF_UNION} placement
     * @param anchor the person the placement hangs from; null for {@code BRANCH_ROOT} and
     *     {@code UNPLACED}
     * @param secondAnchor the other partner, for {@code CHILD_OF_UNION}
     */
    private record ResolvedPlacement(
            Target target, BranchId branchId, Union union, Person anchor, Person secondAnchor) {}
}
