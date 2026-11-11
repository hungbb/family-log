package app.giaky.lineage.application;

import app.giaky.common.error.ConflictException;
import app.giaky.common.error.DomainRuleViolation;
import app.giaky.common.error.ErrorCode;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.IdGenerator;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.domain.AncestryProjection;
import app.giaky.lineage.domain.LineageGraphService;
import app.giaky.lineage.domain.Parentage;
import app.giaky.lineage.domain.ParentRole;
import app.giaky.lineage.domain.ParentageNature;
import app.giaky.lineage.domain.ParentageRepository;
import app.giaky.lineage.domain.Person;
import app.giaky.lineage.domain.TreeVersionRepository;
import app.giaky.lineage.domain.Union;
import app.giaky.lineage.domain.UnionRepository;
import app.giaky.lineage.domain.UnionStatus;
import app.giaky.lineage.domain.UnionType;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one place an edge is written, and therefore the one place the closure is maintained.
 *
 * <p>Two call sites need to create relationships: {@link RelationshipService}, for the explicit
 * endpoints, and {@link PersonService}, because a person created with a placement is created
 * <i>attached</i> — the whole point of doc 05 §4.7's {@code placement} field. Writing that sequence
 * twice is how the second copy eventually forgets a step, and the step it would forget is the
 * closure update, whose absence shows up months later as an authorization decision nobody can
 * explain (risk R3).
 *
 * <p>So the sequence lives here, once, in the order that matters:
 *
 * <ol>
 *   <li>refuse a cycle, <b>before</b> anything is written — the cross product of a cycle is
 *       unbounded;
 *   <li>refuse a duplicate parent for the role (I-R3), so the unique index is never the first thing
 *       to notice;
 *   <li>persist the edge;
 *   <li>extend the closure, in this transaction (doc 02 §6);
 *   <li>restamp <i>đời</i> down the affected subtree (I-R5);
 *   <li>bump the clan's tree version, so every cached tree read revalidates.
 * </ol>
 *
 * <p><b>It asks nobody's permission.</b> That is deliberate and is why it is not a service: the
 * multi-target checks of doc 04 §5.2 need to know <i>why</i> the edge is being created — a create
 * with a placement checks the anchor, an explicit link checks both endpoints — and a component that
 * guessed would guess wrong. Callers check first; {@code AuthorizationCoverageTest} is what makes
 * sure they do.
 */
@Component
public class LineageEdges {

    private final ParentageRepository parentages;
    private final UnionRepository unions;
    private final AncestryProjection ancestry;
    private final LineageGraphService graph;
    private final TreeVersionRepository treeVersions;
    private final IdGenerator ids;
    private final Clock clock;

    public LineageEdges(
            ParentageRepository parentages,
            UnionRepository unions,
            AncestryProjection ancestry,
            LineageGraphService graph,
            TreeVersionRepository treeVersions,
            IdGenerator ids,
            Clock clock) {

        this.parentages = parentages;
        this.unions = unions;
        this.ancestry = ancestry;
        this.graph = graph;
        this.treeVersions = treeVersions;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * Creates a parent → child edge and brings everything derived from it up to date.
     *
     * @throws DomainRuleViolation {@code 422 CYCLE_DETECTED} when the parent is already below the
     *     child
     * @throws ConflictException {@code 409} when the child already has a parent in this role (I-R3)
     */
    public EdgeResult addParentage(
            ClanId clanId,
            PersonId parentId,
            PersonId childId,
            ParentRole role,
            ParentageNature nature,
            UUID unionId,
            UserId actor) {

        graph.requireNoCycle(clanId, parentId, childId);

        // I-R3, checked before the insert so the caller gets a sentence rather than a constraint
        // name. The index is still there and still authoritative under a race.
        Optional<Parentage> existing = parentages.findEdge(clanId, childId, role);
        if (existing.isPresent()) {
            throw new ConflictException(
                    ErrorCode.VERSION_CONFLICT,
                    role == ParentRole.FATHER
                            ? "This person already has a father recorded. Remove that link first."
                            : "This person already has a mother recorded. Remove that link first.");
        }

        // One edge per parent and child. The role index above cannot see this: the same person
        // entered once as father and once as mother breaks no rule about roles, and leaves a child
        // with two parents who are one person.
        boolean alreadyTheirParent =
                parentages.findByChild(clanId, childId).stream()
                        .anyMatch(edge -> edge.parentPersonId().equals(parentId));
        if (alreadyTheirParent) {
            throw new ConflictException(
                    ErrorCode.VERSION_CONFLICT,
                    "This person is already recorded as that child's parent. Correct the existing"
                            + " link rather than adding a second one.");
        }

        Instant now = clock.instant();
        Parentage saved =
                parentages.save(
                        Parentage.create(
                                ids.newId(),
                                clanId,
                                parentId,
                                childId,
                                role,
                                nature,
                                unionId,
                                actor,
                                now));

        ancestry.parentageAdded(clanId, parentId, childId);
        int restamped = graph.recomputeGenerationsFrom(clanId, childId, now);
        treeVersions.bump(clanId);

        return new EdgeResult(saved, restamped);
    }

    /**
     * Removes a parent → child edge and repairs what depended on it.
     *
     * <p>The closure is recomputed rather than subtracted: a pair may still be connected by a second
     * path — a diamond, where a child's two parents share an ancestor — and deleting the pairs this
     * edge contributed would take that connection away with it.
     */
    public EdgeResult removeParentage(Parentage edge, UserId actor) {
        Instant now = clock.instant();
        Parentage removed = parentages.save(edge.remove(actor, now));

        // Order matters: the recompute reads the surviving edges, so the removal has to be visible
        // to it or it will helpfully restore exactly what was just taken away.
        ancestry.parentageRemoved(edge.clanId(), edge.childPersonId());
        int restamped = graph.recomputeGenerationsFrom(edge.clanId(), edge.childPersonId(), now);
        treeVersions.bump(edge.clanId());

        return new EdgeResult(removed, restamped);
    }

    /**
     * Creates a union between two people (I-N1…I-N4).
     *
     * @param force overrides I-N4 — the refusal to marry two people already related as ancestor and
     *     descendant. Callers pass this only after checking the actor holds {@code CLAN_ADMIN}
     * @throws ConflictException {@code 409} when these two already have a live union (I-N2), or when
     *     a second live {@code PRIMARY} union is asked for (I-N3)
     * @throws DomainRuleViolation {@code 422 INVALID_RELATIONSHIP} for I-N4 without {@code force}
     */
    public Union createUnion(
            ClanId clanId,
            Person onePartner,
            Person otherPartner,
            UnionType unionType,
            UnionStatus status,
            app.giaky.common.types.PartialDate startDate,
            app.giaky.common.types.PartialDate endDate,
            String notes,
            boolean force,
            UserId actor) {

        PersonId aId = onePartner.id();
        PersonId bId = otherPartner.id();

        // I-N2. The unique index is canonical-order-only, so asking in either order has to find it.
        if (unions.findByPair(clanId, aId, bId).isPresent()) {
            throw new ConflictException(
                    ErrorCode.VERSION_CONFLICT,
                    "These two are already recorded as a couple. Edit that record instead of adding"
                            + " a second one.");
        }
        // I-N4. A guard against the commonest data-entry error there is — picking the wrong row in
        // a list of people who share a surname — and overridable, because a book that records a
        // marriage between distant cousins is a book we must be able to transcribe faithfully.
        if (!force
                && (ancestry.covers(clanId, aId, bId) || ancestry.covers(clanId, bId, aId))) {
            throw new DomainRuleViolation(
                    ErrorCode.INVALID_RELATIONSHIP,
                    "One of these two is recorded as the other's ancestor. Check you picked the"
                            + " right people; an administrator can record it anyway if the book"
                            + " really says so.");
        }
        if (unionType == UnionType.PRIMARY) {
            // I-N3. A secondary or sequential union alongside a primary one is normal and is the
            // case this product exists to record; a *second* primary one is a contradiction.
            requireNoLivePrimaryUnion(clanId, onePartner);
            requireNoLivePrimaryUnion(clanId, otherPartner);
        }

        Instant now = clock.instant();
        Union saved =
                unions.save(
                        Union.create(
                                ids.newId(),
                                clanId,
                                aId,
                                bId,
                                unionType,
                                status,
                                startDate,
                                endDate,
                                notes,
                                actor,
                                now));
        treeVersions.bump(clanId);
        return saved;
    }

    private void requireNoLivePrimaryUnion(ClanId clanId, Person person) {
        boolean alreadyMarried =
                unions.findByPartner(clanId, person.id()).stream().anyMatch(Union::isActivePrimary);
        if (alreadyMarried) {
            throw new ConflictException(
                    ErrorCode.VERSION_CONFLICT,
                    person.fullName()
                            + " already has a principal marriage recorded. Add this one as a"
                            + " secondary or later marriage instead.");
        }
    }

    /**
     * What an edge change did.
     *
     * @param generationsRestamped how many people had their <i>đời</i> recomputed — a number worth
     *     surfacing, because attaching one node can renumber a whole chi
     */
    public record EdgeResult(Parentage parentage, int generationsRestamped) {}
}
