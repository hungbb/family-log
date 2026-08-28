package app.giaky.lineage.application;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.lineage.domain.AncestryProjection;
import app.giaky.lineage.domain.Person;
import app.giaky.lineage.domain.PersonRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lineage's published window onto ancestry and placement, for the contexts that need to ask.
 *
 * <p>The same shape, and the same reason, as {@code ClanDirectory}. Authorization has to answer "is
 * this person below that one?" for every {@code SUBTREE}-scoped decision, and that is a fact Lineage
 * owns. Without a service like this, the resolver's adapter would import {@code lineage.domain} and
 * {@code architecture.LayeringTest} rule 4 would be gone by the second thing that wanted it.
 *
 * <p>It answers in raw {@code UUID}s, because that is the vocabulary {@code LineageAncestryPort}
 * speaks — the port was written in Phase 3, before {@link PersonId} existed, and it names a scope
 * reference whose kind depends on the scope type. The conversion happens here, at the one boundary.
 *
 * <p>Every method reads, and none of them decides anything about access. The caller holds the
 * policy; a directory that started refusing questions would become a second one.
 */
@Service
public class LineageDirectory {

    private final AncestryProjection ancestry;
    private final PersonRepository persons;

    public LineageDirectory(AncestryProjection ancestry, PersonRepository persons) {
        this.ancestry = ancestry;
        this.persons = persons;
    }

    /** Whether {@code candidate} is {@code ancestor} or one of their descendants (self included). */
    @Transactional(readOnly = true)
    public boolean isAtOrBelow(ClanId clanId, UUID ancestorId, UUID candidateId) {
        return ancestry.covers(clanId, PersonId.of(ancestorId), PersonId.of(candidateId));
    }

    /**
     * Everyone at or below {@code ancestorId}, that person included.
     *
     * <p>One query per subtree grant when the editable set is built (doc 04 §4.1). Includes
     * soft-deleted people, deliberately: the closure describes the edge graph rather than
     * visibility, and a set that dropped them would make restoring a node change who may edit it.
     */
    @Transactional(readOnly = true)
    public Set<UUID> descendantsOf(ClanId clanId, UUID ancestorId) {
        Set<UUID> result = new LinkedHashSet<>();
        for (PersonId descendant : ancestry.descendantsOf(clanId, PersonId.of(ancestorId))) {
            result.add(descendant.value());
        }
        return result;
    }

    /**
     * Which branch a person is placed in.
     *
     * <p>Empty for an unplaced person, and empty for one who does not exist — the two are the same
     * answer to the one question this is asked: whether a branch scope covers a subtree scope
     * (doc 04 §5.4). Both mean no.
     */
    @Transactional(readOnly = true)
    public Optional<BranchId> placementOf(ClanId clanId, UUID personId) {
        return persons.findById(clanId, PersonId.of(personId)).map(Person::branchId);
    }

    /**
     * Display names for a handful of people, by id.
     *
     * <p>What a screen showing a {@code SUBTREE} grant needs: "TREE_EDIT on Nguyễn Văn An and his
     * descendants" rather than on a UUID. Names only — a label is not a licence to read the node.
     *
     * <p>Ids the clan does not have are simply absent from the result, which is the same answer a
     * caller would want for a grant pointing at a deleted person: no label, rather than a guess.
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> displayNames(ClanId clanId, Collection<UUID> personIds) {
        if (personIds.isEmpty()) {
            return Map.of();
        }
        List<PersonId> ids = personIds.stream().map(PersonId::of).toList();
        Map<UUID, String> names = new LinkedHashMap<>();
        for (Person person : persons.findAllByIds(clanId, ids)) {
            names.put(person.id().value(), person.fullName());
        }
        return names;
    }

    /**
     * Whether the clan has a live person with this id.
     *
     * <p>{@code GrantService} asks before writing a {@code SUBTREE} grant. A grant naming a person
     * who does not exist would cover nobody, and a row nobody can explain is worse than a refusal
     * (the same reasoning that already resolves a branch scope before storing it).
     */
    @Transactional(readOnly = true)
    public boolean exists(ClanId clanId, UUID personId) {
        return persons.findById(clanId, PersonId.of(personId)).isPresent();
    }
}
