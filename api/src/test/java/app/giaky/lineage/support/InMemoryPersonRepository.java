package app.giaky.lineage.support;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.domain.Person;
import app.giaky.lineage.domain.PersonRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * {@link PersonRepository} in a map, for the unit tests.
 *
 * <p>Faithful about the two things the services depend on: soft-deleted rows are invisible to every
 * read that does not name them, and every read is clan-scoped. Search is a simplified match — the
 * diacritic folding that {@code f_unaccent} does in the database is done here by hand, well enough
 * to test the service's filter plumbing. What it cannot test is the SQL; {@code PersonSearchIT}
 * does that against real PostgreSQL, which is where the {@code tsvector} and the trigram index live.
 */
public final class InMemoryPersonRepository implements PersonRepository {

    private final Map<PersonId, Person> rows = new LinkedHashMap<>();

    @Override
    public Optional<Person> findById(ClanId clanId, PersonId id) {
        return findByIdIncludingDeleted(clanId, id).filter(person -> !person.isDeleted());
    }

    @Override
    public Optional<Person> findByIdIncludingDeleted(ClanId clanId, PersonId id) {
        return Optional.ofNullable(rows.get(id)).filter(person -> person.clanId().equals(clanId));
    }

    @Override
    public List<Person> findAllByClan(ClanId clanId) {
        return live(clanId).toList();
    }

    @Override
    public List<Person> findAllByIds(ClanId clanId, Collection<PersonId> ids) {
        return live(clanId).filter(person -> ids.contains(person.id())).toList();
    }

    @Override
    public List<Person> findByBranch(ClanId clanId, BranchId branchId) {
        return live(clanId).filter(person -> branchId.equals(person.branchId())).toList();
    }

    @Override
    public List<Person> findDeletedSince(ClanId clanId, Instant deletedSince) {
        return rows.values().stream()
                .filter(person -> person.clanId().equals(clanId))
                .filter(person -> person.isDeleted() && !person.deletedAt().isBefore(deletedSince))
                .sorted(Comparator.comparing(Person::deletedAt).reversed())
                .toList();
    }

    @Override
    public Optional<Person> findFoundingAncestor(ClanId clanId) {
        return live(clanId).filter(Person::foundingAncestor).findFirst();
    }

    @Override
    public PersonPage search(ClanId clanId, PersonQuery query) {
        List<Person> matches =
                live(clanId)
                        .filter(person -> matchesText(person, query.text()))
                        .filter(
                                person ->
                                        query.generation() == null
                                                || query.generation().equals(person.generation()))
                        .filter(
                                person ->
                                        query.lifeStatus() == null
                                                || query.lifeStatus() == person.lifeStatus())
                        .filter(
                                person ->
                                        query.branchId() == null
                                                || query.branchId().equals(person.branchId()))
                        .filter(
                                person ->
                                        query.linked() == null
                                                || query.linked()
                                                        == (person.linkedUserId() != null))
                        .sorted(InMemoryPersonRepository::inBookOrder)
                        .toList();

        int from = Math.min(query.page() * query.size(), matches.size());
        int to = Math.min(from + query.size(), matches.size());
        return new PersonPage(List.copyOf(matches.subList(from, to)), matches.size());
    }

    @Override
    public long countByClan(ClanId clanId) {
        return live(clanId).count();
    }

    /**
     * Stores the person with their version incremented, the way {@code @Version} does.
     *
     * <p>Not a detail: the whole optimistic-concurrency contract of doc 05 §1 rests on the version
     * moving when a row is written, and a double that left it alone would make {@code 409
     * VERSION_CONFLICT} untestable without a database — which is exactly the test most worth having
     * close to the service.
     */
    @Override
    public Person save(Person person) {
        Person stored =
                new Person(
                        person.id(),
                        person.clanId(),
                        person.branchId(),
                        person.name(),
                        person.gender(),
                        person.lifeStatus(),
                        person.birth(),
                        person.death(),
                        person.burialPlace(),
                        person.generation(),
                        person.birthOrder(),
                        person.avatarMediaId(),
                        person.notesHtml(),
                        person.notesText(),
                        person.linkedUserId(),
                        person.foundingAncestor(),
                        person.version() + 1,
                        person.createdBy(),
                        person.updatedBy(),
                        person.createdAt(),
                        person.updatedAt(),
                        person.deletedAt());
        rows.put(stored.id(), stored);
        return stored;
    }

    @Override
    public int updateGenerations(
            ClanId clanId, Collection<GenerationAssignment> assignments, Instant now) {

        int changed = 0;
        for (GenerationAssignment assignment : assignments) {
            Person person = rows.get(assignment.personId());
            if (person == null || !person.clanId().equals(clanId) || person.isDeleted()) {
                continue;
            }
            rows.put(person.id(), person.withGeneration(assignment.generation(), now));
            changed++;
        }
        return changed;
    }

    // --- the link column (Phase 6) --------------------------------------------------------------

    @Override
    public Optional<Person> findByLinkedUser(ClanId clanId, UserId linkedUserId) {
        return rows.values().stream()
                .filter(person -> person.clanId().equals(clanId))
                .filter(person -> !person.isDeleted())
                .filter(person -> linkedUserId.equals(person.linkedUserId()))
                .findFirst();
    }

    @Override
    public List<Person> findLinkedTo(ClanId clanId, Collection<UserId> linkedUserIds) {
        return rows.values().stream()
                .filter(person -> person.clanId().equals(clanId))
                .filter(person -> !person.isDeleted())
                .filter(person -> person.linkedUserId() != null)
                .filter(person -> linkedUserIds.contains(person.linkedUserId()))
                .toList();
    }

    @Override
    public List<Person> findAllByLinkedUser(UserId linkedUserId) {
        return rows.values().stream()
                .filter(person -> !person.isDeleted())
                .filter(person -> linkedUserId.equals(person.linkedUserId()))
                .toList();
    }

    /**
     * The compare-and-set of B6.11, single-threaded.
     *
     * <p>A double cannot reproduce the race the real statement resolves — nothing here runs
     * concurrently — but it reproduces the <i>rule</i>: a node that is already linked refuses the
     * write. The race itself is covered by an integration test against real Postgres, which is the
     * only place it can be covered honestly.
     */
    @Override
    public boolean linkUserIfUnlinked(
            ClanId clanId, PersonId personId, UserId userId, UserId actor, Instant now) {

        Person person = live(clanId, personId);
        if (person == null || person.linkedUserId() != null) {
            return false;
        }
        rows.put(personId, withLink(person, userId, actor, now));
        return true;
    }

    @Override
    public boolean unlinkUser(ClanId clanId, PersonId personId, UserId actor, Instant now) {
        Person person = live(clanId, personId);
        if (person == null || person.linkedUserId() == null) {
            return false;
        }
        rows.put(personId, withLink(person, null, actor, now));
        return true;
    }

    @Override
    public boolean updateSelfServiceFields(
            ClanId clanId,
            PersonId personId,
            UserId linkedUserId,
            java.util.UUID avatarMediaId,
            String notesHtml,
            String notesText,
            Instant now) {

        Person person = live(clanId, personId);
        if (person == null || !linkedUserId.equals(person.linkedUserId())) {
            return false;
        }
        rows.put(
                personId,
                copy(person, avatarMediaId, person.name().alias(), notesHtml, notesText, linkedUserId, now));
        return true;
    }

    @Override
    public boolean updatePropagatedFields(
            ClanId clanId,
            PersonId personId,
            UserId linkedUserId,
            boolean avatar,
            java.util.UUID avatarMediaId,
            boolean displayName,
            String alias,
            Instant now) {

        Person person = live(clanId, personId);
        if (person == null || !linkedUserId.equals(person.linkedUserId())) {
            return false;
        }
        rows.put(
                personId,
                copy(
                        person,
                        avatar ? avatarMediaId : person.avatarMediaId(),
                        displayName ? alias : person.name().alias(),
                        person.notesHtml(),
                        person.notesText(),
                        linkedUserId,
                        now));
        return true;
    }

    private Person live(ClanId clanId, PersonId personId) {
        Person person = rows.get(personId);
        if (person == null || !person.clanId().equals(clanId) || person.isDeleted()) {
            return null;
        }
        return person;
    }

    private static Person withLink(Person person, UserId userId, UserId actor, Instant now) {
        return rebuild(
                person,
                person.avatarMediaId(),
                person.name().alias(),
                person.notesHtml(),
                person.notesText(),
                userId,
                actor,
                now);
    }

    private static Person copy(
            Person person,
            java.util.UUID avatarMediaId,
            String alias,
            String notesHtml,
            String notesText,
            UserId actor,
            Instant now) {

        return rebuild(
                person,
                avatarMediaId,
                alias,
                notesHtml,
                notesText,
                person.linkedUserId(),
                actor,
                now);
    }

    /** Like the native statements, this leaves {@code version} alone. */
    private static Person rebuild(
            Person person,
            java.util.UUID avatarMediaId,
            String alias,
            String notesHtml,
            String notesText,
            UserId linkedUserId,
            UserId actor,
            Instant now) {

        return new Person(
                person.id(),
                person.clanId(),
                person.branchId(),
                new app.giaky.lineage.domain.PersonName(
                        person.name().fullName(),
                        person.name().familyName(),
                        person.name().givenName(),
                        alias,
                        person.name().posthumousName()),
                person.gender(),
                person.lifeStatus(),
                person.birth(),
                person.death(),
                person.burialPlace(),
                person.generation(),
                person.birthOrder(),
                avatarMediaId,
                notesHtml,
                notesText,
                linkedUserId,
                person.foundingAncestor(),
                person.version(),
                person.createdBy(),
                actor,
                person.createdAt(),
                now,
                person.deletedAt());
    }

    /** Everything currently stored, deleted rows included — for assertions, never for the code. */
    public List<Person> all() {
        return new ArrayList<>(rows.values());
    }

    private java.util.stream.Stream<Person> live(ClanId clanId) {
        return rows.values().stream()
                .filter(person -> person.clanId().equals(clanId))
                .filter(person -> !person.isDeleted());
    }

    private static boolean matchesText(Person person, String text) {
        if (text == null) {
            return true;
        }
        String needle = fold(text);
        return fold(person.fullName()).contains(needle)
                || fold(person.name().alias()).contains(needle)
                || fold(person.name().posthumousName()).contains(needle);
    }

    /**
     * A poor person's {@code f_unaccent}.
     *
     * <p>Enough Vietnamese to make the tests mean something: the tone marks and the đ that a person
     * typing on a plain keyboard leaves out. {@code java.text.Normalizer} handles the combining
     * accents; đ has no decomposition and is spelled out.
     */
    private static String fold(String value) {
        if (value == null) {
            return "";
        }
        String decomposed =
                java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{M}+", "");
        return decomposed.replace("đ", "d").replace("Đ", "D").toLowerCase(Locale.ROOT);
    }

    /** đời, then birth order, then name — the order the native query sorts by. */
    private static int inBookOrder(Person left, Person right) {
        int byGeneration = nullsLast(left.generation(), right.generation());
        if (byGeneration != 0) {
            return byGeneration;
        }
        int byOrder = nullsLast(left.birthOrder(), right.birthOrder());
        return byOrder != 0 ? byOrder : left.fullName().compareTo(right.fullName());
    }

    private static int nullsLast(Integer left, Integer right) {
        if (java.util.Objects.equals(left, right)) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return Integer.compare(left, right);
    }
}
