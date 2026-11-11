package app.giaky.lineage.support;

import app.giaky.common.types.ClanId;
import app.giaky.common.types.PersonId;
import app.giaky.lineage.domain.Union;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A five-thousand-person clan, twelve generations deep, across eight branches (B4.15).
 *
 * <p>The subject of every performance assertion from this phase on (NFR-1). Two things about it are
 * deliberate:
 *
 * <ul>
 *   <li><b>It writes rows, not requests.</b> Building the tree through the API would take minutes
 *       and would be measuring the fixture rather than the query. The closure is produced at the end
 *       by {@code rebuild_person_ancestry}, which is the same function the property test compares
 *       incremental maintenance against — so the fixture's ancestry is correct by the same standard.
 *   <li><b>It is shaped like a family, not like a balanced tree.</b> Branching factor varies, some
 *       lines die out, some are much deeper than others, and about half the adults have a spouse.
 *       A perfectly balanced tree would flatter every index; a real <i>gia phả</i> is lumpy, and the
 *       lumpiness is what the closure has to survive.
 * </ul>
 *
 * <p>Seeded, so a slow run is reproducible (doc 09 §4.6).
 */
public final class LargeTreeGenerator {

    public static final int TARGET_PEOPLE = 5_000;
    public static final int GENERATIONS = 12;
    public static final int BRANCHES = 8;

    private static final long SEED = 20260404L;

    /** How many people are written per statement batch. */
    private static final int BATCH = 500;

    private final JdbcTemplate jdbc;

    public LargeTreeGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Builds the clan and returns what a test needs to ask questions about it.
     *
     * @param founderUserId an existing user to record as the creator; may be null
     */
    public LargeTree generate(UUID founderUserId) {
        Random random = new Random(SEED);
        ClanId clanId = ClanId.of(UUID.randomUUID());
        String suffix =
                clanId.value()
                        .toString()
                        .substring(0, 4)
                        .toUpperCase()
                        .replaceAll("[^A-HJKMNP-TV-Z0-9]", "9");

        jdbc.update(
                """
                INSERT INTO clans (id, clan_code, name, visibility, created_by)
                VALUES (?, ?, 'Nguyễn Đại Tộc', 'PRIVATE', ?)
                """,
                clanId.value(),
                "NGUYEN-" + suffix,
                founderUserId);

        List<UUID> branchIds = new ArrayList<>();
        UUID rootBranch = UUID.randomUUID();
        String rootPath = "/" + segment(rootBranch) + "/";
        insertBranch(clanId, rootBranch, null, "HO-" + suffix, "Họ Nguyễn", rootPath, 0);
        branchIds.add(rootBranch);
        for (int index = 1; index < BRANCHES; index++) {
            UUID branchId = UUID.randomUUID();
            insertBranch(
                    clanId,
                    branchId,
                    rootBranch,
                    "CHI" + index + "-" + suffix,
                    "Chi " + index,
                    rootPath + segment(branchId) + "/",
                    1);
            branchIds.add(branchId);
        }

        List<Object[]> people = new ArrayList<>();
        List<Object[]> edges = new ArrayList<>();
        List<Object[]> marriages = new ArrayList<>();

        PersonId founder = PersonId.of(UUID.randomUUID());
        people.add(personRow(clanId, founder, rootBranch, "Nguyễn Phúc Tổ", 1, 1, true));

        List<PersonId> currentGeneration = new ArrayList<>(List.of(founder));
        List<PersonId> everyone = new ArrayList<>(currentGeneration);
        PersonId deepest = founder;

        for (int generation = 2; generation <= GENERATIONS && everyone.size() < TARGET_PEOPLE; generation++) {
            List<PersonId> next = new ArrayList<>();
            for (PersonId parent : currentGeneration) {
                if (everyone.size() >= TARGET_PEOPLE) {
                    break;
                }
                // A line that dies out — every real book has them, and they are what stops the
                // fixture from being a perfect n-ary tree.
                int children = random.nextInt(5);
                UUID branchId = branchIds.get(random.nextInt(branchIds.size()));

                for (int index = 0; index < children && everyone.size() < TARGET_PEOPLE; index++) {
                    PersonId child = PersonId.of(UUID.randomUUID());
                    people.add(
                            personRow(
                                    clanId,
                                    child,
                                    branchId,
                                    "Nguyễn Văn " + everyone.size(),
                                    generation,
                                    index + 1,
                                    false));
                    edges.add(edgeRow(clanId, parent, child, "FATHER"));
                    next.add(child);
                    everyone.add(child);
                    deepest = child;

                    // Roughly half of the adults have a recorded wife, who married in and therefore
                    // has no parents in this clan — the case that makes rootPersonIds plural.
                    if (random.nextBoolean() && everyone.size() < TARGET_PEOPLE) {
                        PersonId spouse = PersonId.of(UUID.randomUUID());
                        people.add(
                                personRow(
                                        clanId,
                                        spouse,
                                        branchId,
                                        "Trần Thị " + everyone.size(),
                                        generation,
                                        null,
                                        false));
                        everyone.add(spouse);
                        // The marriage itself, not just the two people. Phase 5 draws couples as one
                        // layout slot and secondary unions distinctly, so a fixture with wives but no
                        // union rows would measure a tree the canvas never renders.
                        marriages.add(unionRow(clanId, child, spouse, "PRIMARY"));
                    }
                }
            }
            currentGeneration = next;
            if (currentGeneration.isEmpty()) {
                break;
            }
        }

        insertPeople(people);
        insertEdges(edges);
        insertUnions(marriages);
        jdbc.queryForObject("SELECT rebuild_person_ancestry(?)", Long.class, clanId.value());

        return new LargeTree(
                clanId, rootBranch, founder, deepest, everyone.size(), marriages.size());
    }

    /**
     * What was built.
     *
     * @param deepest the last person written, which is at or near the bottom of the deepest line —
     *     the worst case for an ancestry walk
     */
    public record LargeTree(
            ClanId clanId,
            UUID rootBranchId,
            PersonId founder,
            PersonId deepest,
            int personCount,
            int unionCount) {}

    private void insertPeople(List<Object[]> rows) {
        for (int from = 0; from < rows.size(); from += BATCH) {
            jdbc.batchUpdate(
                    """
                    INSERT INTO persons
                      (id, clan_id, branch_id, full_name, gender, life_status, generation,
                       birth_order, is_founding_ancestor)
                    VALUES (?, ?, ?, ?, ?, 'DECEASED', ?, ?, ?)
                    """,
                    rows.subList(from, Math.min(from + BATCH, rows.size())));
        }
    }

    private void insertUnions(List<Object[]> rows) {
        for (int from = 0; from < rows.size(); from += BATCH) {
            jdbc.batchUpdate(
                    """
                    INSERT INTO unions
                      (id, clan_id, partner_a_id, partner_b_id, union_type, status)
                    VALUES (?, ?, ?, ?, ?, 'UNKNOWN')
                    """,
                    rows.subList(from, Math.min(from + BATCH, rows.size())));
        }
    }

    private void insertEdges(List<Object[]> rows) {
        for (int from = 0; from < rows.size(); from += BATCH) {
            jdbc.batchUpdate(
                    """
                    INSERT INTO parentages
                      (id, clan_id, parent_person_id, child_person_id, parent_role, nature)
                    VALUES (?, ?, ?, ?, ?, 'BIOLOGICAL')
                    """,
                    rows.subList(from, Math.min(from + BATCH, rows.size())));
        }
    }

    private Object[] personRow(
            ClanId clanId,
            PersonId id,
            UUID branchId,
            String name,
            int generation,
            Integer birthOrder,
            boolean founding) {

        return new Object[] {
            id.value(),
            clanId.value(),
            branchId,
            name,
            birthOrder == null ? "FEMALE" : "MALE",
            (short) generation,
            birthOrder == null ? null : birthOrder.shortValue(),
            founding
        };
    }

    /** Partners in the canonical order {@code ck_union_canonical_order} insists on. */
    private Object[] unionRow(ClanId clanId, PersonId one, PersonId other, String type) {
        boolean ordered = Union.precedes(one, other);
        return new Object[] {
            UUID.randomUUID(),
            clanId.value(),
            ordered ? one.value() : other.value(),
            ordered ? other.value() : one.value(),
            type
        };
    }

    private Object[] edgeRow(ClanId clanId, PersonId parent, PersonId child, String role) {
        return new Object[] {
            UUID.randomUUID(), clanId.value(), parent.value(), child.value(), role
        };
    }

    private void insertBranch(
            ClanId clanId, UUID id, UUID parentId, String code, String name, String path, int depth) {

        jdbc.update(
                """
                INSERT INTO branches (id, clan_id, parent_branch_id, branch_code, name, path, depth)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                clanId.value(),
                parentId,
                code,
                name,
                path,
                depth);
    }

    /** The eight-character short form a materialized path uses for a branch (doc 02 §3). */
    private static String segment(UUID branchId) {
        return branchId.toString().substring(0, 8);
    }
}
