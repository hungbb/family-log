package app.giaky.lineage.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import app.giaky.clanorg.support.ClanOrgApiSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * Shared plumbing for the Lineage integration tests: real HTTP, real PostgreSQL, real accounts.
 *
 * <p>Everything goes through the API rather than through a service, because what these tests can say
 * that the unit tests cannot is that Flyway's constraints, the JPA mapping, the closure SQL, the
 * security chain and the services all agree with one another.
 */
public abstract class LineageApiSupport extends ClanOrgApiSupport {

    /** Adds a person and returns the response body. */
    protected JsonNode createPerson(
            Actor actor, UUID clanId, String fullName, Map<String, Object> extras) throws Exception {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", fullName);
        body.putAll(extras);

        return body(
                as(actor, post("/api/v1/clans/" + clanId + "/persons"), body)
                        .andExpect(MockMvcResultMatchers.status().isCreated())
                        .andReturn());
    }

    /** A placement object for the create request. */
    protected static Map<String, Object> placement(String type, String field, UUID id) {
        Map<String, Object> placement = new LinkedHashMap<>();
        placement.put("type", type);
        if (field != null) {
            placement.put(field, id.toString());
        }
        return placement;
    }

    /** The founding ancestor of a clan, placed as the root branch's ancestor. */
    protected UUID foundingAncestor(Actor actor, UUID clanId, UUID rootBranchId, String name)
            throws Exception {

        return UUID.fromString(
                createPerson(
                                actor,
                                clanId,
                                name,
                                Map.of(
                                        "gender", "MALE",
                                        "lifeStatus", "DECEASED",
                                        "foundingAncestor", true,
                                        "placement",
                                                placement("BRANCH_ROOT", "branchId", rootBranchId)))
                        .get("id")
                        .asText());
    }

    /** A child of one parent, inheriting their branch. */
    protected UUID childOf(Actor actor, UUID clanId, UUID parentId, String name, String gender)
            throws Exception {

        return UUID.fromString(
                createPerson(
                                actor,
                                clanId,
                                name,
                                Map.of(
                                        "gender", gender,
                                        "placement", placement("CHILD_OF_PARENT", "personId", parentId)))
                        .get("id")
                        .asText());
    }

    /** A spouse, which records a {@code PRIMARY} union. */
    protected UUID spouseOf(Actor actor, UUID clanId, UUID partnerId, String name, String gender)
            throws Exception {

        return UUID.fromString(
                createPerson(
                                actor,
                                clanId,
                                name,
                                Map.of(
                                        "gender", gender,
                                        "placement", placement("SPOUSE_OF", "personId", partnerId)))
                        .get("id")
                        .asText());
    }

    /** The clan's root branch id, from the clan detail endpoint. */
    protected UUID rootBranchId(JsonNode clan) {
        return UUID.fromString(clan.get("rootBranchId").asText());
    }
}
