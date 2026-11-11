package app.giaky.lineage;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.giaky.lineage.support.LineageApiSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A date that is only a lunar reading comes back from every read that carries dates (FR-P3).
 *
 * <p>"Giỗ ngày mồng 3 tháng Chạp", no year: the API accepted it with {@code 201}, the row held it in
 * {@code death_lunar_text}, and no response ever contained it — the detail, the search row and the
 * tree all answered as if the date were absent. Because {@code PUT} replaces, the first save from a
 * form that had been shown nothing then erased it for good.
 *
 * <p>Over HTTP and PostgreSQL because the loss was at the edge: the domain and the columns were
 * right, and only the response mapping dropped it.
 */
class LunarOnlyDateIT extends LineageApiSupport {

    private static final String ANNIVERSARY = "giỗ ngày mồng 3 tháng Chạp";

    private Actor admin;
    private UUID clanId;
    private UUID person;

    @BeforeEach
    void anAncestorKnownOnlyByTheirAnniversary() throws Exception {
        admin = signUp("Nguyễn Văn An");
        JsonNode clan = createClan(admin, "Nguyễn Văn");
        clanId = UUID.fromString(clan.get("id").asText());

        JsonNode created =
                createPerson(
                        admin,
                        clanId,
                        "Lê Thị Hai",
                        Map.of(
                                "gender", "FEMALE",
                                "lifeStatus", "DECEASED",
                                "birth", Map.of("precision", "UNKNOWN", "lunarText", "năm Ất Dậu"),
                                "death", Map.of("precision", "UNKNOWN", "lunarText", ANNIVERSARY),
                                "placement",
                                        placement("BRANCH_ROOT", "branchId", rootBranchId(clan))));
        person = UUID.fromString(created.get("id").asText());
    }

    private String personPath() {
        return "/api/v1/clans/" + clanId + "/persons/" + person;
    }

    @Test
    @DisplayName("the detail returns it, at UNKNOWN precision with the book's words")
    void detailCarriesIt() throws Exception {
        as(admin, get(personPath()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.death.precision").value("UNKNOWN"))
                .andExpect(jsonPath("$.death.lunarText").value(ANNIVERSARY))
                .andExpect(jsonPath("$.death.year").doesNotExist())
                .andExpect(jsonPath("$.birth.lunarText").value("năm Ất Dậu"));
    }

    @Test
    @DisplayName("so do the search row and the tree, which is what the canvas and the list render")
    void listReadsCarryIt() throws Exception {
        as(admin, get("/api/v1/clans/" + clanId + "/persons?q=le thi hai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].death.lunarText").value(ANNIVERSARY));

        as(admin, get("/api/v1/clans/" + clanId + "/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persons[0].death.lunarText").value(ANNIVERSARY))
                .andExpect(jsonPath("$.persons[0].birth.lunarText").value("năm Ất Dậu"));
    }

    @Test
    @DisplayName("a client that sends back what it was given no longer erases it")
    void survivesAReplace() throws Exception {
        JsonNode loaded = body(as(admin, get(personPath())).andExpect(status().isOk()).andReturn());

        // What the edit form does: every field it was shown, plus the version.
        Map<String, Object> replacement = new LinkedHashMap<>();
        replacement.put("fullName", "Lê Thị Hai");
        replacement.put("gender", "FEMALE");
        replacement.put("lifeStatus", "DECEASED");
        replacement.put("birth", objectMapper.convertValue(loaded.get("birth"), Map.class));
        replacement.put("death", objectMapper.convertValue(loaded.get("death"), Map.class));
        replacement.put("version", loaded.get("version").asLong());

        as(admin, put(personPath()), replacement)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.death.lunarText").value(ANNIVERSARY))
                .andExpect(jsonPath("$.birth.lunarText").value("năm Ất Dậu"));
    }

    @Test
    @DisplayName("clearing it is still possible, and an emptied date is absent rather than empty")
    void canBeCleared() throws Exception {
        long version =
                body(as(admin, get(personPath())).andExpect(status().isOk()).andReturn())
                        .get("version")
                        .asLong();

        as(
                        admin,
                        patch(personPath()),
                        Map.of("death", Map.of("precision", "UNKNOWN"), "version", version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.death").doesNotExist())
                .andExpect(jsonPath("$.birth.lunarText").value("năm Ất Dậu"));
    }

    @Test
    @DisplayName("a marriage dated only by the lunar calendar is returned too")
    void unionDatesCarryIt() throws Exception {
        UUID husband =
                UUID.fromString(
                        createPerson(
                                        admin,
                                        clanId,
                                        "Nguyễn Văn Ất",
                                        Map.of(
                                                "gender", "MALE",
                                                "placement",
                                                        Map.of("type", "UNPLACED")))
                                .get("id")
                                .asText());

        Map<String, Object> union = new LinkedHashMap<>();
        union.put("partnerAId", husband.toString());
        union.put("partnerBId", person.toString());
        union.put("unionType", "PRIMARY");
        union.put("startDate", Map.of("precision", "UNKNOWN", "lunarText", "mùa xuân năm Giáp Tý"));

        as(admin, post("/api/v1/clans/" + clanId + "/unions"), union)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate.lunarText").value("mùa xuân năm Giáp Tý"));
    }
}
