package app.giaky.common.types;

import java.util.Objects;
import java.util.UUID;

/**
 * A branch — a <i>chi</i> or <i>nhánh</i> — as a type.
 *
 * <p>Lives here rather than in {@code clanorg.domain} because three contexts hold one: ClanOrg owns
 * branches, Lineage stores a person's placement as one, and Authorization scopes a grant to one
 * (doc 02 §1.1). A shared id is the only thing they are allowed to share.
 *
 * <p>The wrapper earns its keep on the endpoints that take two ids at once — {@code
 * /clans/{clanId}/branches/{branchId}} — where two raw {@code UUID} parameters are one transposition
 * away from a tenancy bug the compiler would never mention.
 */
public record BranchId(UUID value) {

    public BranchId {
        Objects.requireNonNull(value, "branch id must not be null");
    }

    public static BranchId of(UUID value) {
        return new BranchId(value);
    }

    public static BranchId of(String value) {
        return new BranchId(UUID.fromString(value));
    }

    /**
     * The path segment that stands for this branch in a materialized path.
     *
     * <p>Eight hex characters, per doc 02 §3 ("segments are branch UUID short-forms"). Short enough
     * that a ten-deep path stays far inside {@code branches.path}'s 512 characters, and long enough
     * that a collision inside one clan is a curiosity rather than a risk — and {@code
     * ux_branches_path} turns even that curiosity into a loud failure at insert time rather than a
     * quiet mis-scoped read.
     */
    public String pathSegment() {
        return value.toString().substring(0, 8);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
