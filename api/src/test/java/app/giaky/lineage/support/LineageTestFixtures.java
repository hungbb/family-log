package app.giaky.lineage.support;

import app.giaky.authorization.infrastructure.ClosureLineageAncestryAdapter;
import app.giaky.clanorg.support.ClanOrgTestFixtures;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.application.CreatePersonCommand;
import app.giaky.lineage.application.LineageDirectory;
import app.giaky.lineage.application.LineageEdges;
import app.giaky.lineage.application.LineageSnapshots;
import app.giaky.lineage.application.PersonPlacement;
import app.giaky.lineage.application.PersonService;
import app.giaky.lineage.application.RelationshipService;
import app.giaky.lineage.application.TreeQueryService;
import app.giaky.lineage.domain.AncestryProjection;
import app.giaky.lineage.domain.Gender;
import app.giaky.lineage.domain.LifeStatus;
import app.giaky.lineage.domain.LineageGraphService;
import app.giaky.lineage.domain.NoteSanitizer;
import app.giaky.lineage.infrastructure.OwaspNoteSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The whole Lineage application graph, wired in memory, on top of {@link ClanOrgTestFixtures}.
 *
 * <p>Every service here is the real one, including the permission resolver and the note sanitizer.
 * What is replaced is the four repositories, and one of those — {@link InMemoryAncestryRepository} —
 * is replaced by an oracle rather than a second incremental implementation, for the reason its own
 * Javadoc gives.
 *
 * <p><b>The resolver is wired to this module's closure.</b> {@code ClanOrgTestFixtures} builds the
 * resolver in a field initializer with a map-backed ancestry port; the constructor here points that
 * port at a real {@link LineageDirectory} over these repositories, so a {@code SUBTREE} grant issued
 * in a test is resolved against the same tree the test is building. Without that, subtree isolation
 * tests would pass against an ancestry nobody wrote to.
 */
public final class LineageTestFixtures {

    /** Clans, branches, memberships, grants — everything Lineage sits on top of. */
    public final ClanOrgTestFixtures clanOrg = new ClanOrgTestFixtures();

    public final InMemoryPersonRepository persons = new InMemoryPersonRepository();
    public final InMemoryParentageRepository parentages = new InMemoryParentageRepository();
    public final InMemoryUnionRepository unions = new InMemoryUnionRepository();
    public final InMemoryAncestryRepository ancestryRows =
            new InMemoryAncestryRepository(persons, parentages);
    public final InMemoryTreeVersionRepository treeVersions = new InMemoryTreeVersionRepository();
    public final InMemoryTreeSnapshotRepository treeSnapshots =
            new InMemoryTreeSnapshotRepository(persons, parentages, unions);
    public final InMemoryTreeSnapshotCache treeSnapshotCache = new InMemoryTreeSnapshotCache();

    public final AncestryProjection ancestry = new AncestryProjection(ancestryRows);
    public final LineageGraphService graph =
            new LineageGraphService(persons, parentages, ancestry);
    public final NoteSanitizer sanitizer = new OwaspNoteSanitizer();
    public final LineageDirectory directory = new LineageDirectory(ancestry, persons);
    public final LineageSnapshots snapshots = new LineageSnapshots(new ObjectMapper());

    public final LineageEdges edges =
            new LineageEdges(
                    parentages,
                    unions,
                    ancestry,
                    graph,
                    treeVersions,
                    clanOrg.ids,
                    clanOrg.clock);

    public final PersonService personService =
            new PersonService(
                    persons,
                    unions,
                    ancestry,
                    graph,
                    edges,
                    treeVersions,
                    sanitizer,
                    clanOrg.resolver,
                    clanOrg.clanDirectory,
                    clanOrg.branchService,
                    snapshots,
                    clanOrg.audit,
                    clanOrg.events,
                    clanOrg.ids,
                    clanOrg.clock);

    public final RelationshipService relationshipService =
            new RelationshipService(
                    persons,
                    parentages,
                    unions,
                    edges,
                    treeVersions,
                    clanOrg.resolver,
                    snapshots,
                    clanOrg.audit,
                    clanOrg.events,
                    clanOrg.clock);

    public final TreeQueryService treeQueryService =
            new TreeQueryService(
                    persons,
                    treeSnapshots,
                    treeSnapshotCache,
                    ancestry,
                    treeVersions,
                    clanOrg.resolver,
                    clanOrg.clanDirectory,
                    clanOrg.clock);

    public LineageTestFixtures() {
        // The one wire that has to be made after both halves exist. See the class note.
        clanOrg.ancestry.backedBy(new ClosureLineageAncestryAdapter(directory));
    }

    // --- shorthands the tests read better for ---------------------------------------------------

    /** The clan's root branch — where a founding ancestor goes. */
    public BranchId rootBranch(ClanId clanId) {
        return clanOrg.clanDirectory.branches(clanId).stream()
                .filter(branch -> branch.parentBranchId() == null)
                .findFirst()
                .orElseThrow()
                .id();
    }

    /** A founding ancestor — đời 1, placed in the clan's root branch. */
    public PersonId founder(UserId actor, ClanId clanId, BranchId branchId, String name) {
        return PersonId.of(
                personService
                        .create(
                                actor,
                                clanId,
                                person(name, Gender.MALE)
                                        .foundingAncestor(true)
                                        .placement(PersonPlacement.branchRoot(branchId))
                                        .build())
                        .id());
    }

    /** A child of one parent, inheriting that parent's branch. */
    public PersonId childOf(
            UserId actor, ClanId clanId, PersonId parentId, String name, Gender gender) {

        return PersonId.of(
                personService
                        .create(
                                actor,
                                clanId,
                                person(name, gender)
                                        .placement(PersonPlacement.childOf(parentId))
                                        .build())
                        .id());
    }

    /** A spouse, which creates a {@code PRIMARY} union with the person named. */
    public PersonId spouseOf(
            UserId actor, ClanId clanId, PersonId partnerId, String name, Gender gender) {

        return PersonId.of(
                personService
                        .create(
                                actor,
                                clanId,
                                person(name, gender)
                                        .placement(PersonPlacement.spouseOf(partnerId))
                                        .build())
                        .id());
    }

    /** A node attached to nothing, which needs clan-wide {@code TREE_EDIT}. */
    public PersonId unplaced(UserId actor, ClanId clanId, String name) {
        return PersonId.of(
                personService
                        .create(
                                actor,
                                clanId,
                                person(name, Gender.MALE)
                                        .placement(PersonPlacement.unplaced())
                                        .build())
                        .id());
    }

    public static Builder person(String fullName, Gender gender) {
        return new Builder(fullName, gender);
    }

    /**
     * A create command with sensible defaults.
     *
     * <p>Doc 09 §4.6 prefers fixtures over factories with defaults, so a test's preconditions are
     * obvious. This one earns its keep by defaulting only the things a test never means to say
     * anything about — a name and a gender are always given, everything structural is explicit.
     */
    public static final class Builder {

        private final String fullName;
        private final Gender gender;
        private LifeStatus lifeStatus = LifeStatus.UNKNOWN;
        private app.giaky.common.types.PartialDate birth =
                app.giaky.common.types.PartialDate.unknown();
        private app.giaky.common.types.PartialDate death =
                app.giaky.common.types.PartialDate.unknown();
        private Integer birthOrder;
        private String notesHtml;
        private boolean foundingAncestor;
        private PersonPlacement placement = PersonPlacement.unplaced();

        private Builder(String fullName, Gender gender) {
            this.fullName = fullName;
            this.gender = gender;
        }

        public Builder lifeStatus(LifeStatus value) {
            this.lifeStatus = value;
            return this;
        }

        public Builder birth(app.giaky.common.types.PartialDate value) {
            this.birth = value;
            return this;
        }

        public Builder death(app.giaky.common.types.PartialDate value) {
            this.death = value;
            return this;
        }

        public Builder birthOrder(Integer value) {
            this.birthOrder = value;
            return this;
        }

        public Builder notesHtml(String value) {
            this.notesHtml = value;
            return this;
        }

        public Builder foundingAncestor(boolean value) {
            this.foundingAncestor = value;
            return this;
        }

        public Builder placement(PersonPlacement value) {
            this.placement = value;
            return this;
        }

        public CreatePersonCommand build() {
            return new CreatePersonCommand(
                    fullName,
                    null,
                    null,
                    null,
                    null,
                    gender,
                    lifeStatus,
                    birth,
                    death,
                    null,
                    birthOrder,
                    notesHtml,
                    foundingAncestor,
                    placement,
                    null,
                    null);
        }
    }
}
