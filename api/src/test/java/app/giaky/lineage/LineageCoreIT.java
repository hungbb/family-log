package app.giaky.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.giaky.lineage.support.LineageApiSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * The phase's end-to-end story: build a four-generation tree over HTTP and check what the API
 * refuses (phase 4 §5).
 *
 * <p>Not one test per endpoint. The point of these is that the layers agree — that a cycle is
 * refused by the service before the closure SQL has to cope with it, that a soft delete really does
 * disappear from the tree read, that the {@code ETag} really does answer {@code 304}. Each of those
 * crosses three layers, and none of them can be seen from inside one.
 */
class LineageCoreIT extends LineageApiSupport {

    private Actor admin;
    private UUID clanId;
    private UUID rootBranchId;

    @BeforeEach
    void foundAClan() throws Exception {
        admin = signUp("Nguyễn Văn An");
        JsonNode clan = createClan(admin, "Nguyễn Văn");
        clanId = UUID.fromString(clan.get("id").asText());
        rootBranchId = rootBranchId(clan);
    }

    @Test
    @DisplayName("a four-generation tree, built through the API, comes back as one flat payload")
    void fourGenerations() throws Exception {
        UUID doi1 = foundingAncestor(admin, clanId, rootBranchId, "Nguyễn Phúc Tổ");
        UUID doi2 = childOf(admin, clanId, doi1, "Nguyễn Văn An", "MALE");
        UUID doi3 = childOf(admin, clanId, doi2, "Nguyễn Văn Bình", "MALE");
        childOf(admin, clanId, doi3, "Nguyễn Văn Cường", "MALE");

        as(admin, get("/api/v1/clans/" + clanId + "/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons.length()").value(4))
                .andExpect(jsonPath("$.parentages.length()").value(3))
                .andExpect(jsonPath("$.meta.maxGeneration").value(4))
                .andExpect(jsonPath("$.rootPersonIds.length()").value(1));

        // đời is computed from the edges, not supplied by the client (I-R5).
        as(admin, get("/api/v1/clans/" + clanId + "/persons/" + doi3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generation").value(3));
    }

    @Test
    @DisplayName("a second wife and her children sit under the same father (FR-P4)")
    void secondWifeAndHerChildren() throws Exception {
        UUID husband = foundingAncestor(admin, clanId, rootBranchId, "Nguyễn Phúc Tổ");
        UUID firstWife = spouseOf(admin, clanId, husband, "Trần Thị Cả", "FEMALE");
        UUID secondWife =
                UUID.fromString(
                        createPerson(
                                        admin,
                                        clanId,
                                        "Lê Thị Hai",
                                        Map.of(
                                                "gender", "FEMALE",
                                                "placement",
                                                        placement("UNPLACED", null, null)))
                                .get("id")
                                .asText());

        JsonNode secondUnion =
                body(
                        as(
                                        admin,
                                        post("/api/v1/clans/" + clanId + "/unions"),
                                        Map.of(
                                                "partnerAId", husband.toString(),
                                                "partnerBId", secondWife.toString(),
                                                "unionType", "SECONDARY",
                                                "status", "ACTIVE"))
                                .andExpect(status().isCreated())
                                .andReturn());

        // A child of the second marriage, attached through the union so the book can tell whose
        // child is whose.
        createPerson(
                admin,
                clanId,
                "Nguyễn Văn Út",
                Map.of(
                        "gender",
                        "MALE",
                        "placement",
                        placement(
                                "CHILD_OF_UNION",
                                "unionId",
                                UUID.fromString(secondUnion.get("id").asText()))));

        as(admin, get("/api/v1/clans/" + clanId + "/persons/" + husband))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unions.length()").value(2))
                .andExpect(jsonPath("$.children.length()").value(1));

        // The first marriage is still the principal one, and a second PRIMARY is refused.
        as(
                        admin,
                        post("/api/v1/clans/" + clanId + "/unions"),
                        Map.of(
                                "partnerAId", husband.toString(),
                                "partnerBId", firstWife.toString(),
                                "unionType", "PRIMARY"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a cycle is refused with 422 CYCLE_DETECTED")
    void cycleIsRefused() throws Exception {
        UUID grandparent = foundingAncestor(admin, clanId, rootBranchId, "Nguyễn Phúc Tổ");
        UUID parent = childOf(admin, clanId, grandparent, "Nguyễn Văn An", "MALE");
        UUID child = childOf(admin, clanId, parent, "Nguyễn Văn Bình", "MALE");

        as(
                        admin,
                        post("/api/v1/clans/" + clanId + "/parentages"),
                        Map.of(
                                "parentPersonId", child.toString(),
                                "childPersonId", grandparent.toString(),
                                "parentRole", "FATHER"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CYCLE_DETECTED"));
    }

    @Test
    @DisplayName("a second father is refused with 409")
    void secondFatherIsRefused() throws Exception {
        UUID founder = foundingAncestor(admin, clanId, rootBranchId, "Nguyễn Phúc Tổ");
        UUID child = childOf(admin, clanId, founder, "Nguyễn Văn An", "MALE");
        UUID pretender = childOf(admin, clanId, founder, "Nguyễn Văn Khác", "MALE");

        as(
                        admin,
                        post("/api/v1/clans/" + clanId + "/parentages"),
                        Map.of(
                                "parentPersonId", pretender.toString(),
                                "childPersonId", child.toString(),
                                "parentRole", "FATHER"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a duplicate union is refused with 409, in either order")
    void duplicateUnionIsRefused() throws Exception {
        UUID husband = foundingAncestor(admin, clanId, rootBranchId, "Nguyễn Phúc Tổ");
        UUID wife = spouseOf(admin, clanId, husband, "Trần Thị Hoa", "FEMALE");

        as(
                        admin,
                        post("/api/v1/clans/" + clanId + "/unions"),
                        Map.of(
                                "partnerAId", wife.toString(),
                                "partnerBId", husband.toString(),
                                "unionType", "SECONDARY"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a stale version is 409 VERSION_CONFLICT rather than a silent overwrite")
    void staleVersionConflicts() throws Exception {
        UUID person = foundingAncestor(admin, clanId, rootBranchId, "Nguyễn Phúc Tổ");
        JsonNode loaded =
                body(
                        as(admin, get("/api/v1/clans/" + clanId + "/persons/" + person))
                                .andExpect(status().isOk())
                                .andReturn());
        long version = loaded.get("version").asLong();

        as(
                        admin,
                        patch("/api/v1/clans/" + clanId + "/persons/" + person),
                        Map.of("fullName", "Nguyễn Phúc Tổ (sửa)", "version", version))
                .andExpect(status().isOk());

        as(
                        admin,
                        patch("/api/v1/clans/" + clanId + "/persons/" + person),
                        Map.of("fullName", "Ghi đè", "version", version))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    @DisplayName("soft delete hides a node from the tree and from search; restore brings it back")
    void softDeleteAndRestore() throws Exception {
        UUID founder = foundingAncestor(admin, clanId, rootBranchId, "Nguyễn Phúc Tổ");
        UUID child = childOf(admin, clanId, founder, "Nguyễn Văn An", "MALE");

        as(admin, delete("/api/v1/clans/" + clanId + "/persons/" + child))
                .andExpect(status().isNoContent());

        as(admin, get("/api/v1/clans/" + clanId + "/tree"))
                .andExpect(jsonPath("$.persons.length()").value(1));
        as(admin, get("/api/v1/clans/" + clanId + "/persons?q=Nguyễn Văn An"))
                .andExpect(jsonPath("$.totalElements").value(0));
        as(admin, get("/api/v1/clans/" + clanId + "/persons/deleted"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        as(admin, post("/api/v1/clans/" + clanId + "/persons/" + child + "/restore"))
                .andExpect(status().isOk());

        as(admin, get("/api/v1/clans/" + clanId + "/tree"))
                .andExpect(jsonPath("$.persons.length()").value(2))
                .andExpect(jsonPath("$.parentages.length()").value(1));
    }

    @Test
    @DisplayName("search is diacritic-insensitive in both directions (FR-P11)")
    void diacriticInsensitiveSearch() throws Exception {
        foundingAncestor(admin, clanId, rootBranchId, "Nguyễn Văn An");

        as(admin, get("/api/v1/clans/" + clanId + "/persons?q=nguyen van an"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Nguyễn Văn An"));

        as(admin, get("/api/v1/clans/" + clanId + "/persons?q=NGUYỄN"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("the tree carries an ETag, and an unchanged tree answers 304")
    void treeEtag() throws Exception {
        foundingAncestor(admin, clanId, rootBranchId, "Nguyễn Phúc Tổ");

        String etag =
                as(admin, get("/api/v1/clans/" + clanId + "/tree"))
                        .andExpect(status().isOk())
                        .andExpect(header().exists(HttpHeaders.ETAG))
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.ETAG);

        as(
                        admin,
                        get("/api/v1/clans/" + clanId + "/tree")
                                .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());

        // Any lineage mutation bumps the version, so the cached copy revalidates (B4.12).
        createPerson(
                admin,
                clanId,
                "Nguyễn Văn Mới",
                Map.of("placement", placement("UNPLACED", null, null)));

        as(
                        admin,
                        get("/api/v1/clans/" + clanId + "/tree")
                                .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the cached tree never outlives the edit that changed it (B5.3)")
    void cachedTreeIsBustedByAnEdit() throws Exception {
        UUID doi1 = foundingAncestor(admin, clanId, rootBranchId, "Nguyễn Phúc Tổ");

        // Read twice: the second is the cache hit, and the one a stale entry would be served from.
        as(admin, get("/api/v1/clans/" + clanId + "/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons.length()").value(1));
        as(admin, get("/api/v1/clans/" + clanId + "/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons.length()").value(1));

        childOf(admin, clanId, doi1, "Nguyễn Văn An", "MALE");

        // No conditional header: this is the read that would show a stale tree if the cache key did
        // not carry the tree version.
        as(admin, get("/api/v1/clans/" + clanId + "/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons.length()").value(2))
                .andExpect(jsonPath("$.parentages.length()").value(1));

        // The statistics read the same snapshot, so they must move with it.
        as(admin, get("/api/v1/clans/" + clanId + "/tree/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personCount").value(2))
                .andExpect(jsonPath("$.maxGeneration").value(2));
    }

    @Test
    @DisplayName("partial dates round-trip at every precision (F4.1)")
    void partialDatesRoundTrip() throws Exception {
        JsonNode person =
                createPerson(
                        admin,
                        clanId,
                        "Nguyễn Văn An",
                        Map.of(
                                "lifeStatus",
                                "DECEASED",
                                "birth",
                                Map.of("year", 1901, "precision", "YEAR", "circa", true),
                                "death",
                                Map.of(
                                        "year",
                                        1968,
                                        "month",
                                        3,
                                        "precision",
                                        "MONTH",
                                        "lunarText",
                                        "tháng 2 năm Mậu Thân"),
                                "placement",
                                placement("UNPLACED", null, null)));

        as(admin, get("/api/v1/clans/" + clanId + "/persons/" + person.get("id").asText()))
                .andExpect(jsonPath("$.birth.year").value(1901))
                .andExpect(jsonPath("$.birth.precision").value("YEAR"))
                .andExpect(jsonPath("$.birth.circa").value(true))
                .andExpect(jsonPath("$.death.month").value(3))
                .andExpect(jsonPath("$.death.lunarText").value("tháng 2 năm Mậu Thân"));
    }

    @Test
    @DisplayName("a birth after a death is refused with 422")
    void impossibleDatesAreRefused() throws Exception {
        as(
                        admin,
                        post("/api/v1/clans/" + clanId + "/persons"),
                        Map.of(
                                "fullName", "Nguyễn Văn An",
                                "lifeStatus", "DECEASED",
                                "birth", Map.of("year", 1968, "precision", "YEAR"),
                                "death", Map.of("year", 1901, "precision", "YEAR"),
                                "placement", placement("UNPLACED", null, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DATE_RANGE_INVALID"))
                .andExpect(jsonPath("$.errors[0].field").value("death"));
    }

    @Test
    @DisplayName("what the aggregates refuse is answered 422, on create and on every kind of edit")
    void brokenInvariantsAreNotServerErrors() throws Exception {
        String persons = "/api/v1/clans/" + clanId + "/persons";

        // I-P3: recorded as living, with a date of death.
        as(
                        admin,
                        post(persons),
                        Map.of(
                                "fullName", "Nguyễn Văn An",
                                "lifeStatus", "ALIVE",
                                "death", Map.of("year", 2001, "precision", "YEAR")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("death"));

        JsonNode an =
                createPerson(
                        admin,
                        clanId,
                        "Nguyễn Văn An",
                        Map.of(
                                "lifeStatus", "DECEASED",
                                "birth", Map.of("year", 1901, "precision", "YEAR")));
        String anUrl = persons + "/" + an.get("id").asText();
        long version = an.get("version").asLong();

        // The same two rules against what is already stored, which only the service can see.
        as(admin, patch(anUrl), Map.of("death", Map.of("year", 1890, "precision", "YEAR"), "version", version))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DATE_RANGE_INVALID"));
        as(
                        admin,
                        patch(anUrl),
                        Map.of(
                                "lifeStatus", "ALIVE",
                                "death", Map.of("year", 1968, "precision", "YEAR"),
                                "version", version))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // A name of spaces on PATCH, and no name at all on PUT — which replaces, so it clears.
        as(admin, patch(anUrl), Map.of("fullName", "   ", "version", version))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("fullName"));
        as(admin, put(anUrl), Map.of("alias", "Cụ An", "version", version))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("fullName"));

        // None of that was applied.
        as(admin, get(anUrl))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$.version").value(version));

        // A marriage that ends before it begins, recorded and then edited.
        UUID wife = spouseOf(admin, clanId, UUID.fromString(an.get("id").asText()), "Trần Thị Bé", "FEMALE");
        String unionId =
                body(as(admin, get(anUrl)).andReturn()).get("unions").get(0).get("union").get("id").asText();
        as(
                        admin,
                        patch("/api/v1/clans/" + clanId + "/unions/" + unionId),
                        Map.of(
                                "unionType", "PRIMARY",
                                "startDate", Map.of("year", 1950, "precision", "YEAR"),
                                "endDate", Map.of("year", 1940, "precision", "YEAR")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DATE_RANGE_INVALID"));
        UUID second =
                UUID.fromString(
                        createPerson(admin, clanId, "Lê Thị Hai", Map.of("gender", "FEMALE"))
                                .get("id")
                                .asText());
        as(
                        admin,
                        post("/api/v1/clans/" + clanId + "/unions"),
                        Map.of(
                                "partnerAId", an.get("id").asText(),
                                "partnerBId", second.toString(),
                                "unionType", "SECONDARY",
                                "startDate", Map.of("year", 1950, "precision", "YEAR"),
                                "endDate", Map.of("year", 1940, "precision", "YEAR")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DATE_RANGE_INVALID"));
        assertThat(wife).isNotNull();
    }

    @Test
    @DisplayName("notes are sanitized before they are stored, not on the way out")
    void notesAreSanitizedOnWrite() throws Exception {
        JsonNode person =
                createPerson(
                        admin,
                        clanId,
                        "Nguyễn Văn An",
                        Map.of(
                                "notesHtml",
                                "<p>Di cư vào Nam năm 1954.</p><script>alert(1)</script>",
                                "placement",
                                placement("UNPLACED", null, null)));

        as(admin, get("/api/v1/clans/" + clanId + "/persons/" + person.get("id").asText()))
                .andExpect(jsonPath("$.notesHtml").value("<p>Di cư vào Nam năm 1954.</p>"));
    }

    @Test
    @DisplayName("the tree read is clan-scoped: another clan's person id is a 404")
    void tenancy() throws Exception {
        UUID mine = foundingAncestor(admin, clanId, rootBranchId, "Nguyễn Phúc Tổ");

        Actor other = signUp("Trần Văn Bốn");
        JsonNode otherClan = createClan(other, "Trần Văn");
        UUID otherClanId = UUID.fromString(otherClan.get("id").asText());

        as(other, get("/api/v1/clans/" + otherClanId + "/persons/" + mine))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"));
    }
}
