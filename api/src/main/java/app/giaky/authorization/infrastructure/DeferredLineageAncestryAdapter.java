package app.giaky.authorization.infrastructure;

import app.giaky.authorization.domain.LineageAncestryPort;
import app.giaky.common.types.BranchId;
import app.giaky.common.types.ClanId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link LineageAncestryPort} before there is any lineage (B3.2, phase 3 §1).
 *
 * <p>{@code person_ancestry} arrives in Phase 4 with {@code persons}. Until it does, this answers
 * {@link #isAvailable()} with false — which is the honest answer, and the one every caller already
 * branches on — and throws for anything else.
 *
 * <p>Throwing rather than returning "no" is the point. A silent empty answer would make a future
 * bug look like a permission denial and take a week to find; a thrown exception names the call that
 * should have checked {@code isAvailable()} first, in the stack trace, on the first run. Nothing
 * reaches these methods today: {@code ScopeCoverage} guards every one of them, and {@code
 * GrantService} refuses to create a {@code SUBTREE} grant at all with {@code 422
 * SUBTREE_SCOPE_UNAVAILABLE}.
 *
 * <p>Phase 4 replaces this class with an adapter over the closure table, and the resolver does not
 * change.
 */
@Component
public class DeferredLineageAncestryAdapter implements LineageAncestryPort {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean covers(ClanId clan, UUID ancestor, UUID candidate) {
        throw unavailable();
    }

    @Override
    public Set<UUID> descendantsOf(ClanId clan, UUID ancestor) {
        throw unavailable();
    }

    @Override
    public Optional<BranchId> placementOf(ClanId clan, UUID personId) {
        throw unavailable();
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException(
                "person_ancestry does not exist until Phase 4; callers must check isAvailable()"
                        + " first (phase 3 §1)");
    }
}
