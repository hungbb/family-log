package app.giaky.authorization.infrastructure;

import app.giaky.authorization.domain.LineageAncestryPort;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import app.giaky.lineage.application.LineageDirectory;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link LineageAncestryPort} over the {@code person_ancestry} closure (B4.8).
 *
 * <p>Replaces Phase 3's deferred adapter, which answered {@link #isAvailable()} with false and threw
 * for everything else. The resolver and {@code ScopeCoverage} are unchanged: they were written
 * against this interface a phase before it could be answered, and turning {@code SUBTREE} scope on
 * was deleting one class and adding this one.
 *
 * <p>Reads through {@code LineageDirectory} rather than the closure table directly, because
 * {@code architecture.LayeringTest} rule 4 forbids reaching into another context's domain or
 * infrastructure. What crosses the boundary is ids and a boolean — enough to decide containment,
 * nothing that would let Authorization start having opinions about the tree.
 *
 * <p>Not request-cached, unlike {@code ClanOrgBranchHierarchyAdapter}. A clan has tens of branches
 * and loading them all once per request is cheaper than several targeted lookups; a clan has
 * thousands of people, so the equivalent here would be caching a set the size of the tree to answer
 * a question that is one indexed lookup. The batch case that <i>would</i> justify it — a whole
 * tree's {@code canEdit} flags — already reads {@link #descendantsOf} once per grant, by design
 * (doc 04 §4.1).
 */
@Component
public class ClosureLineageAncestryAdapter implements LineageAncestryPort {

    private final LineageDirectory lineage;

    public ClosureLineageAncestryAdapter(LineageDirectory lineage) {
        this.lineage = lineage;
    }

    /**
     * True from Phase 4 on.
     *
     * <p>Kept rather than removed along with the deferred adapter. It is the switch every caller
     * already branches on, and leaving it in place means a future deployment that ever runs without
     * the lineage schema denies subtree decisions instead of throwing from inside the resolver.
     */
    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean personExists(ClanId clan, UUID personId) {
        return lineage.exists(clan, personId);
    }

    @Override
    public boolean covers(ClanId clan, UUID ancestor, UUID candidate) {
        return lineage.isAtOrBelow(clan, ancestor, candidate);
    }

    @Override
    public Set<UUID> descendantsOf(ClanId clan, UUID ancestor) {
        return lineage.descendantsOf(clan, ancestor);
    }

    @Override
    public Map<UUID, String> displayNames(ClanId clan, Collection<UUID> personIds) {
        return lineage.displayNames(clan, personIds);
    }

    @Override
    public Optional<BranchId> placementOf(ClanId clan, UUID personId) {
        return lineage.placementOf(clan, personId);
    }
}
