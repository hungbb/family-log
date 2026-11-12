-- V14 — make the subtree recompute cost the subtree (doc 03 §3.10).
--
-- V9's recompute_person_ancestry_from kept the subtree as a UUID[] and tested membership with
-- `= ANY (array)`, which is a linear scan of the array for every row it filters. Its propagation
-- loop also re-joined the subtree's whole ancestry on every pass, not just what the previous pass
-- changed. Together that is roughly generations × closure rows × subtree size: about 1.1 s for the
-- 5,000-person fixture's largest line in LargeTreePerformanceIT, against a 150 ms budget.
--
-- Same contract, same result, two changes:
--   1. The subtree is a temporary table with a primary key, so membership is a hash or index probe.
--   2. Semi-naive propagation: each pass extends only the rows the previous pass inserted or
--      shortened (RETURNING). Depth only ever decreases and every improvement derives from a row
--      that changed, so the fixpoint is the one the full re-join reached.
--
-- AncestryProjectionPropertyIT compares the result with a fresh rebuild after randomised edits.

CREATE OR REPLACE FUNCTION recompute_person_ancestry_from(p_clan_id UUID, p_root UUID)
    RETURNS BIGINT
    LANGUAGE plpgsql
    -- Quiet the "relation already exists, skipping" notice on a second call in one transaction.
    SET client_min_messages = warning
AS
$$
DECLARE
    v_added BIGINT;
    v_total BIGINT   := 0;
    v_level SMALLINT := 0;
BEGIN
    -- Scratch tables live for the transaction. IF NOT EXISTS plus the DELETEs below make a second
    -- call in the same transaction (a person losing both parents) start clean.
    CREATE TEMP TABLE IF NOT EXISTS recompute_affected
    (
        person_id UUID PRIMARY KEY
    ) ON COMMIT DROP;
    CREATE TEMP TABLE IF NOT EXISTS recompute_delta
    (
        ancestor_id   UUID     NOT NULL,
        descendant_id UUID     NOT NULL,
        depth         SMALLINT NOT NULL
    ) ON COMMIT DROP;
    CREATE TEMP TABLE IF NOT EXISTS recompute_next
    (
        ancestor_id   UUID     NOT NULL,
        descendant_id UUID     NOT NULL,
        depth         SMALLINT NOT NULL
    ) ON COMMIT DROP;
    DELETE FROM recompute_affected;
    DELETE FROM recompute_delta;
    DELETE FROM recompute_next;

    -- The subtree as it stands *now*, self-row included. Read before anything is deleted, because
    -- the rows below C are what defines the set and they are the rows this function preserves.
    INSERT INTO recompute_affected (person_id)
    SELECT descendant_id
    FROM person_ancestry
    WHERE clan_id = p_clan_id
      AND ancestor_id = p_root
    UNION
    SELECT p_root;
    ANALYZE recompute_affected;

    DELETE
    FROM person_ancestry pa
        USING recompute_affected d
    WHERE pa.clan_id = p_clan_id
      AND pa.descendant_id = d.person_id
      AND NOT EXISTS (SELECT 1 FROM recompute_affected x WHERE x.person_id = pa.ancestor_id);

    -- Re-seed: every surviving edge that still enters the subtree from outside brings its parent's
    -- entire ancestor set with it. C itself is the usual case, but a node deeper in the subtree can
    -- have a second parent outside it, and that path has to survive the repair.
    WITH changed AS (
        INSERT INTO person_ancestry (clan_id, ancestor_id, descendant_id, depth)
            SELECT p_clan_id, a.ancestor_id, e.child_person_id, MIN(a.depth) + 1
            FROM parentages e
                     JOIN recompute_affected c ON c.person_id = e.child_person_id
                     JOIN person_ancestry a
                          ON a.descendant_id = e.parent_person_id AND a.clan_id = p_clan_id
            WHERE e.clan_id = p_clan_id
              AND e.deleted_at IS NULL
              AND NOT EXISTS (SELECT 1 FROM recompute_affected p WHERE p.person_id = e.parent_person_id)
            GROUP BY a.ancestor_id, e.child_person_id
            ON CONFLICT (ancestor_id, descendant_id)
                DO UPDATE SET depth = EXCLUDED.depth
                WHERE person_ancestry.depth > EXCLUDED.depth
            RETURNING ancestor_id, descendant_id, depth)
    INSERT
    INTO recompute_delta
    SELECT ancestor_id, descendant_id, depth
    FROM changed;

    GET DIAGNOSTICS v_total = ROW_COUNT;

    -- Push only what changed down through the subtree's own edges until nothing changes.
    LOOP
            v_level := v_level + 1;
            EXIT WHEN v_level > 64;

            WITH changed AS (
                INSERT INTO person_ancestry (clan_id, ancestor_id, descendant_id, depth)
                    SELECT p_clan_id, d.ancestor_id, e.child_person_id, MIN(d.depth) + 1
                    FROM recompute_delta d
                             JOIN parentages e
                                  ON e.parent_person_id = d.descendant_id
                                      AND e.clan_id = p_clan_id
                                      AND e.deleted_at IS NULL
                             JOIN recompute_affected c ON c.person_id = e.child_person_id
                    GROUP BY d.ancestor_id, e.child_person_id
                    ON CONFLICT (ancestor_id, descendant_id)
                        DO UPDATE SET depth = EXCLUDED.depth
                        WHERE person_ancestry.depth > EXCLUDED.depth
                    RETURNING ancestor_id, descendant_id, depth)
            INSERT
            INTO recompute_next
            SELECT ancestor_id, descendant_id, depth
            FROM changed;

            GET DIAGNOSTICS v_added = ROW_COUNT;
            EXIT WHEN v_added = 0;
            v_total := v_total + v_added;

            DELETE FROM recompute_delta;
            INSERT INTO recompute_delta SELECT * FROM recompute_next;
            DELETE FROM recompute_next;
        END LOOP;

    RETURN v_total;
END;
$$;

COMMENT ON FUNCTION recompute_person_ancestry_from(UUID, UUID) IS
    'Repairs person_ancestry for one subtree after a parentage edge above it was removed. Bounded '
        'by the subtree size rather than the clan size; propagates by delta (V14).';
