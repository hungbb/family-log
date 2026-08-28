package app.giaky.authorization.domain;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Descendant lookups for {@code SUBTREE} scope, backed by {@code person_ancestry} (doc 03 §3.10).
 *
 * <p>The port was written in Phase 3, before any of it could be answered. That was the point: the
 * resolver's coverage rules are written once and tested once, and a resolver that had to grow a
 * third scope afterwards is a resolver whose highest-risk method gets rewritten after it has already
 * shipped. Phase 4 supplied the closure table and an adapter over it, and the resolver did not
 * change — which is the thing that design was buying.
 *
 * <p>{@link #isAvailable()} survives the transition. It reports whether the closure can be
 * consulted, and every caller still branches on it, so a deployment whose lineage schema is somehow
 * absent denies subtree decisions rather than throwing on the authorization path — deny by default,
 * never a fallthrough (doc 04 §1.5).
 */
public interface LineageAncestryPort {

    /** Whether the closure table exists and can be queried. True from Phase 4 onward. */
    boolean isAvailable();

    /**
     * Whether the clan has a live person with this id.
     *
     * <p>Asked by {@code GrantService} before a {@code SUBTREE} grant is stored, for the same reason
     * a branch scope is resolved before storing one: a grant naming somebody who does not exist
     * covers nobody, and a row nobody can explain is worse than a refusal.
     */
    boolean personExists(ClanId clan, UUID personId);

    /**
     * Whether {@code candidate} is {@code ancestor} or one of their descendants.
     *
     * <p>Reflexive, because {@code person_ancestry} carries a self-row at depth 0: a subtree grant
     * covers the person it names (doc 04 §3.1).
     */
    boolean covers(ClanId clan, UUID ancestor, UUID candidate);

    /** Every descendant of {@code ancestor}, including {@code ancestor} — one query per grant. */
    Set<UUID> descendantsOf(ClanId clan, UUID ancestor);

    /**
     * Display names for the people a page's {@code SUBTREE} grants name.
     *
     * <p>Strictly speaking not ancestry, and here anyway. This port is Authorization's whole window
     * onto Lineage — {@link #placementOf} is not ancestry either — and a second port for one map
     * would mean a second adapter, a second test double, and a second thing to remember to wire.
     * What crosses is a name, which is what a permissions screen needs to say "TREE_EDIT on Nguyễn
     * Văn An and his descendants" instead of showing a UUID.
     *
     * <p>Ids the clan does not have are absent from the result rather than mapped to a placeholder:
     * a grant pointing at a removed person should read as unlabelled, not as somebody.
     */
    Map<UUID, String> displayNames(ClanId clan, Collection<UUID> personIds);

    /**
     * Which branch a person is placed in.
     *
     * <p>Used by one rule only: whether a branch scope covers a subtree scope (doc 04 §5.4). Empty
     * for an unplaced person, and empty for everybody until Phase 4 creates {@code persons}.
     */
    Optional<BranchId> placementOf(ClanId clan, UUID personId);
}
