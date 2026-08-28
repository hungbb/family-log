package app.giaky.authorization.domain;

import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Descendant lookups for {@code SUBTREE} scope, backed by {@code person_ancestry} (doc 03 §3.10).
 *
 * <p>Nothing implements this against a real table until Phase 4. The port exists now because the
 * resolver's coverage rules are written once and tested once, and a resolver that had to grow a
 * third scope later is a resolver whose highest-risk method gets rewritten after it has already
 * shipped.
 *
 * <p>Until then the adapter answers {@link #isAvailable()} with false and the resolver denies any
 * decision that would have to consult it — deny by default, not a fallthrough (doc 04 §1.5) — while
 * {@code GrantService} refuses to create such a grant at all, with {@code 422
 * SUBTREE_SCOPE_UNAVAILABLE}. The two together mean a subtree scope cannot exist yet, and would be
 * harmless if one somehow did.
 */
public interface LineageAncestryPort {

    /** Whether the closure table exists and can be queried. False until Phase 4. */
    boolean isAvailable();

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
     * Which branch a person is placed in.
     *
     * <p>Used by one rule only: whether a branch scope covers a subtree scope (doc 04 §5.4). Empty
     * for an unplaced person, and empty for everybody until Phase 4 creates {@code persons}.
     */
    Optional<BranchId> placementOf(ClanId clan, UUID personId);
}
