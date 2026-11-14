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

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PartialDate;
import app.giaky.common.types.PersonId;
import app.giaky.common.types.UserId;
import app.giaky.lineage.domain.Gender;
import app.giaky.lineage.domain.LifeStatus;
import app.giaky.lineage.domain.Person;
import app.giaky.lineage.domain.PersonName;
import app.giaky.lineage.domain.PersonRepository;
import app.giaky.lineage.support.LineageApiSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired private PersonRepository persons;

    private Actor admin;
    private UUID clanId;
    private UUID rootBranchId;
    private String clanCode;

    @BeforeEach
    void foundAClan() throws Exception {
        admin = signUp("Nguyễn Văn An");
        JsonNode clan = createClan(admin, "Nguyễn Văn");
        clanId = UUID.fromString(clan.get("id").asText());
        rootBranchId = rootBranchId(clan);
        clanCode = clan.get("clanCode").asText();
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
    @DisplayName("attaching a child to a marriage names the man as father, and nobody as both parents")
    void parentRolesOnAttach() throws Exception {
        // The wife first: the smaller id, so she is stored as partner A — the slot the attach route
        // once made the father regardless of who sat in it. Roles must follow gender, not order.
        UUID wife = unplaced("Trần Thị Hoa", Map.of("gender", "FEMALE"));
        UUID husband = unplaced("Nguyễn Văn Hai", Map.of("gender", "MALE"));
        UUID child = unplaced("Nguyễn Văn Con", Map.of());
        JsonNode union =
                body(
                        as(
                                        admin,
                                        post("/api/v1/clans/" + clanId + "/unions"),
                                        Map.of(
                                                "partnerAId", husband.toString(),
                                                "partnerBId", wife.toString(),
                                                "unionType", "PRIMARY"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.partnerAId").value(wife.toString()))
                                .andReturn());

        as(
                        admin,
                        post("/api/v1/clans/" + clanId + "/unions/" + union.get("id").asText() + "/children"),
                        Map.of("personId", child.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.parentRole == 'FATHER')].parentPersonId").value(husband.toString()))
                .andExpect(jsonPath("$[?(@.parentRole == 'MOTHER')].parentPersonId").value(wife.toString()));

        // And the father cannot be entered a second time as an adoptive mother's slot is free.
        UUID orphan = unplaced("Nguyễn Văn Út", Map.of());
        as(
                        admin,
                        post("/api/v1/clans/" + clanId + "/parentages"),
                        Map.of(
                                "parentPersonId", husband.toString(),
                                "childPersonId", orphan.toString(),
                                "parentRole", "FATHER"))
                .andExpect(status().isCreated());
        as(
                        admin,
                        post("/api/v1/clans/" + clanId + "/parentages"),
                        Map.of(
                                "parentPersonId", husband.toString(),
                                "childPersonId", orphan.toString(),
                                "parentRole", "MOTHER"))
                .andExpect(status().isConflict());
    }

    private UUID unplaced(String fullName, Map<String, Object> extras) throws Exception {
        return UUID.fromString(createPerson(admin, clanId, fullName, extras).get("id").asText());
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
    @DisplayName("a marriage is recorded whichever side of the UUID sign bit the two ids fall on")
    void unionAcrossTheSignBit() throws Exception {
        // The suite's sequential ids never set the top bit, and that is the only place where
        // java.util.UUID's signed ordering and PostgreSQL's disagree — so the pair is written
        // directly, with the two ids production's random generator produces half the time.
        UUID belowTheSignBit = UUID.fromString("7fffffff-ffff-4fff-bfff-ffffffffffff");
        UUID aboveTheSignBit = UUID.fromString("80000000-0000-4000-8000-000000000000");
        unplacedPerson(belowTheSignBit, "Nguyễn Phúc Tổ", Gender.MALE);
        unplacedPerson(aboveTheSignBit, "Trần Thị Cả", Gender.FEMALE);

        as(
                        admin,
                        post("/api/v1/clans/" + clanId + "/unions"),
                        Map.of(
                                "partnerAId", aboveTheSignBit.toString(),
                                "partnerBId", belowTheSignBit.toString(),
                                "unionType", "PRIMARY"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partnerAId").value(belowTheSignBit.toString()))
                .andExpect(jsonPath("$.partnerBId").value(aboveTheSignBit.toString()));

        // Found again from either side: the duplicate is a 409, not a second row and not a 500.
        as(
                        admin,
                        post("/api/v1/clans/" + clanId + "/unions"),
                        Map.of(
                                "partnerAId", belowTheSignBit.toString(),
                                "partnerBId", aboveTheSignBit.toString(),
                                "unionType", "SECONDARY"))
                .andExpect(status().isConflict());
        as(admin, get("/api/v1/clans/" + clanId + "/persons/" + aboveTheSignBit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unions.length()").value(1))
                .andExpect(jsonPath("$.unions[0].partner.id").value(belowTheSignBit.toString()));
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
    @DisplayName("one member's ETag does not answer 304 for another, whose canEdit differs (#49)")
    void treeEtagIsPerCaller() throws Exception {
        foundingAncestor(admin, clanId, rootBranchId, "Nguyễn Phúc Tổ");

        Actor member = signUp("Người xem");
        joinClan(member, admin, clanId, clanCode, rootBranchId);

        String adminEtag =
                as(admin, get("/api/v1/clans/" + clanId + "/tree"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.persons[0].canEdit").value(true))
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.ETAG);

        // The defect: same clan, same tree version, and a validator that could not tell the two
        // representations apart — so a cache revalidating for the member kept the admin's flags.
        as(
                        member,
                        get("/api/v1/clans/" + clanId + "/tree")
                                .header(HttpHeaders.IF_NONE_MATCH, adminEtag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons[0].canEdit").value(false));

        // And the member's own tag still revalidates for the member.
        String memberEtag =
                as(member, get("/api/v1/clans/" + clanId + "/tree"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.ETAG);
        as(
                        member,
                        get("/api/v1/clans/" + clanId + "/tree")
                                .header(HttpHeaders.IF_NONE_MATCH, memberEtag))
                .andExpect(status().isNotModified());
    }

    @Test
    @DisplayName("a whole-tree ETag does not answer 304 for a subtree of it (#49)")
    void treeEtagIsPerQuery() throws Exception {
        UUID doi1 = foundingAncestor(admin, clanId, rootBranchId, "Nguyễn Phúc Tổ");
        UUID doi2 = childOf(admin, clanId, doi1, "Nguyễn Văn An", "MALE");

        String wholeTree =
                as(admin, get("/api/v1/clans/" + clanId + "/tree"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.persons.length()").value(2))
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.ETAG);

        // rootPersonId selects a different body out of the very same rows at the very same version.
        as(
                        admin,
                        get("/api/v1/clans/" + clanId + "/tree")
                                .param("rootPersonId", doi2.toString())
                                .header(HttpHeaders.IF_NONE_MATCH, wholeTree))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons.length()").value(1));
    }

    @Test
    @DisplayName("renaming a branch invalidates the tree ETag, with no lineage edit (#54)")
    void treeEtagCoversTheBranchBlock() throws Exception {
        foundingAncestor(admin, clanId, rootBranchId, "Nguyễn Phúc Tổ");
        JsonNode chiHai = createBranch(admin, clanId, rootBranchId, "Chi Hai");
        UUID chiHaiId = UUID.fromString(chiHai.get("id").asText());

        String etag =
                as(admin, get("/api/v1/clans/" + clanId + "/tree"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.ETAG);

        // A branch mutation and nothing else: tree_version counts lineage edits, so before #54 the
        // validator could not see this and the old name stayed in every cache.
        as(
                admin,
                patch("/api/v1/clans/" + clanId + "/branches/" + chiHaiId),
                Map.of(
                        "name",
                        "Chi Thứ Hai",
                        "expectedVersion",
                        chiHai.get("version").asLong()))
                .andExpect(status().isOk());

        as(
                        admin,
                        get("/api/v1/clans/" + clanId + "/tree")
                                .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.branches[?(@.id == '" + chiHaiId + "')].name")
                                .value("Chi Thứ Hai"));
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
                                "gender", "MALE",
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
                                "gender", "MALE",
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

    /**
     * A person with an id of the test's choosing, which no endpoint will hand out.
     *
     * <p>Through the repository port rather than raw SQL, so the row is exactly what {@code
     * PersonService} would have written — minus the placement, which a union does not need.
     */
    private void unplacedPerson(UUID id, String fullName, Gender gender) {
        persons.save(
                Person.create(
                        PersonId.of(id),
                        ClanId.of(clanId),
                        null,
                        new PersonName(fullName, null, null, null, null),
                        gender,
                        LifeStatus.DECEASED,
                        PartialDate.unknown(),
                        PartialDate.unknown(),
                        null,
                        null,
                        null,
                        null,
                        false,
                        UserId.of(admin.userId()),
                        Instant.parse("2026-01-01T00:00:00Z")));
        flushAndClear();
    }
}
