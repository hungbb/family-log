package app.giaky.common.types;

import java.util.Objects;
import java.util.UUID;

/**
 * A node in the family tree, as a type.
 *
 * <p>Here rather than in {@code lineage.domain} for the same reason {@link BranchId} is: four
 * contexts hold one. Lineage owns persons, ClanOrg points a branch at its root person, Authorization
 * scopes a {@code SUBTREE} grant to one, and Linking maps an account onto one (doc 02 §1.1). A
 * shared id is the only thing those contexts are allowed to share.
 *
 * <p>It earns its keep on the endpoints that carry three ids at once — {@code
 * /clans/{clanId}/persons/{personId}} plus a branch in the body — where raw {@code UUID} parameters
 * are one transposition away from a tenancy bug the compiler would never mention.
 *
 * <p>Authorization's {@code Target} still speaks raw {@code UUID}, deliberately: it was written
 * before persons existed and must stay able to name a scope reference whose kind depends on the
 * scope type. The conversion happens at that one boundary.
 */
public record PersonId(UUID value) {

    public PersonId {
        Objects.requireNonNull(value, "person id must not be null");
    }

    public static PersonId of(UUID value) {
        return new PersonId(value);
    }

    public static PersonId of(String value) {
        return new PersonId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
