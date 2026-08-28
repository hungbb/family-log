-- V9 — the ancestry closure (doc 03 §3.10). The projection SUBTREE scope is built on.
--
-- "Is X a descendant of Y?" is asked on the hot path of every authorization decision made against a
-- subtree grant, and again for every node of a tree render. Answering it with a recursive walk each
-- time was the alternative considered and rejected (doc 03 §3.10): the walk is cheap once and
-- ruinous when it happens per node, per request. So the answer is materialized, and the price is
-- that it has to be kept true.
--
-- **It is maintained inside the transaction that changes a parentage, never in a listener.** Doc 02
-- §6 says so, and risk R3 is why: an asynchronous projection means a window in which authorization
-- reads an ancestry that no longer matches the tree, and the failure mode of that window is
-- somebody editing a branch they were never granted.
--
-- **Soft delete does not touch this table.** A soft-deleted person keeps their self-row and their
-- edges, so restoring them restores their place in the tree rather than requiring a repair. Readers
-- join `persons` and filter `deleted_at IS NULL`; the closure describes the edge graph, not
-- visibility. `rebuild_person_ancestry` below reproduces exactly that, which is what lets the
-- property test compare the two.

CREATE TABLE person_ancestry
(
    clan_id       UUID     NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    ancestor_id   UUID     NOT NULL REFERENCES persons (id) ON DELETE CASCADE,
    descendant_id UUID     NOT NULL REFERENCES persons (id) ON DELETE CASCADE,

    -- Generational distance, 0 for the self-row. Parentage is a DAG, so two people can be connected
    -- by more than one path; this column holds the *shortest*. It is advisory — nothing about
    -- access depends on it — and a single number is what keeps the primary key a pair.
    depth         SMALLINT NOT NULL,

    CONSTRAINT pk_person_ancestry PRIMARY KEY (ancestor_id, descendant_id),
    CONSTRAINT ck_ancestry_depth CHECK (depth >= 0),
    -- The self-row is the only one that may join a person to themselves; anything else at depth 0
    -- would be a cycle that got through.
    CONSTRAINT ck_ancestry_self_row CHECK ((ancestor_id = descendant_id) = (depth = 0))
);

-- "Who are this person's ancestors?" — breadcrumbs, and the coverage check read from the other end.
CREATE INDEX ix_ancestry_descendant ON person_ancestry (descendant_id);

-- "Everyone below this person" — one query per subtree grant when the editable set is built
-- (doc 04 §4.1), and the subtree tree read.
CREATE INDEX ix_ancestry_clan_ancestor ON person_ancestry (clan_id, ancestor_id);

COMMENT ON TABLE person_ancestry IS
    'Transitive ancestor → descendant closure over parentages, including a depth-0 self-row per '
        'person. Maintained in the same transaction as every parentage change (doc 02 §6).';


-- Rebuilds the whole closure for one clan, from the edges (doc 03 §3.10).
--
-- The repair tool, and the reference implementation: the property test of doc 09 §4.5 runs
-- randomized add/remove sequences through the incremental maintenance and then compares the result
-- against a fresh call to this function. If the two ever disagree, the incremental path is wrong,
-- which is precisely the drift risk R3 names.
--
-- Breadth-first rather than one recursive CTE. A recursive UNION ALL over a DAG enumerates *paths*,
-- and a family tree with two parents per child has up to 2^depth of them between one pair — twelve
-- generations is four thousand rows to produce one. The loop below enumerates *pairs*, so its cost
-- is edges × depth and every pair lands at its shortest depth the first time it is reached.
CREATE OR REPLACE FUNCTION rebuild_person_ancestry(p_clan_id UUID)
    RETURNS BIGINT
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_added BIGINT;
    v_total BIGINT := 0;
    v_level SMALLINT := 0;
BEGIN
    DELETE FROM person_ancestry WHERE clan_id = p_clan_id;

    -- Every person is their own ancestor at distance 0 (doc 03 §3.10). This is also what makes a
    -- subtree grant cover the person it names.
    INSERT INTO person_ancestry (clan_id, ancestor_id, descendant_id, depth)
    SELECT p.clan_id, p.id, p.id, 0
    FROM persons p
    WHERE p.clan_id = p_clan_id;

    GET DIAGNOSTICS v_total = ROW_COUNT;

    LOOP
        v_level := v_level + 1;

        INSERT INTO person_ancestry (clan_id, ancestor_id, descendant_id, depth)
        SELECT p_clan_id, a.ancestor_id, e.child_person_id, MIN(a.depth) + 1
        FROM person_ancestry a
                 JOIN parentages e
                      ON e.parent_person_id = a.descendant_id
                          AND e.clan_id = p_clan_id
                          AND e.deleted_at IS NULL
        WHERE a.clan_id = p_clan_id
          AND a.depth = v_level - 1
        -- One row per (ancestor, descendant) pair: ON CONFLICT DO UPDATE refuses to touch the same
        -- row twice in one statement, and two parents sharing an ancestor produce exactly that.
        GROUP BY a.ancestor_id, e.child_person_id
        ON CONFLICT (ancestor_id, descendant_id) DO NOTHING;

        GET DIAGNOSTICS v_added = ROW_COUNT;
        v_total := v_total + v_added;

        -- The depth cap is a guard against corrupt data, not a modelling limit: a cycle that
        -- somehow reached the table would otherwise make this loop forever. Sixty-four generations
        -- is more than twice the deepest gia phả anyone has brought us.
        EXIT WHEN v_added = 0 OR v_level > 64;
    END LOOP;

    RETURN v_total;
END;
$$;

COMMENT ON FUNCTION rebuild_person_ancestry(UUID) IS
    'Recomputes person_ancestry for one clan from persons and parentages. Repair tool and the '
        'reference the closure property test compares incremental maintenance against.';


-- Recomputes the ancestry of one subtree, after an edge above it was removed.
--
-- Removing (P → C) can only change rows that reach *into* C's subtree from outside it: a path
-- between two nodes that are both at or below C cannot have used an edge whose parent is above C.
-- So the repair deletes exactly those incoming rows, re-seeds from whatever edges still enter the
-- subtree, and pushes the result downward — instead of rebuilding a clan of five thousand people
-- because one edge changed.
--
-- Called with C, the child endpoint of the removed edge. Safe to call when C has no subtree and
-- safe to call twice.
CREATE OR REPLACE FUNCTION recompute_person_ancestry_from(p_clan_id UUID, p_root UUID)
    RETURNS BIGINT
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_affected UUID[];
    v_added    BIGINT;
    v_total    BIGINT := 0;
    v_level    SMALLINT := 0;
BEGIN
    -- The subtree as it stands *now*, self-row included. Read before anything is deleted, because
    -- the rows below C are what defines the set and they are the rows this function preserves.
    SELECT coalesce(array_agg(descendant_id), ARRAY [p_root])
    INTO v_affected
    FROM person_ancestry
    WHERE clan_id = p_clan_id
      AND ancestor_id = p_root;

    DELETE
    FROM person_ancestry
    WHERE clan_id = p_clan_id
      AND descendant_id = ANY (v_affected)
      AND NOT (ancestor_id = ANY (v_affected));

    -- Re-seed: every surviving edge that still enters the subtree from outside brings its parent's
    -- entire ancestor set with it. C itself is the usual case, but a node deeper in the subtree can
    -- have a second parent outside it, and that path has to survive the repair.
    INSERT INTO person_ancestry (clan_id, ancestor_id, descendant_id, depth)
    SELECT p_clan_id, a.ancestor_id, e.child_person_id, MIN(a.depth) + 1
    FROM parentages e
             JOIN person_ancestry a
                  ON a.descendant_id = e.parent_person_id AND a.clan_id = p_clan_id
    WHERE e.clan_id = p_clan_id
      AND e.deleted_at IS NULL
      AND e.child_person_id = ANY (v_affected)
      AND NOT (e.parent_person_id = ANY (v_affected))
    GROUP BY a.ancestor_id, e.child_person_id
    ON CONFLICT (ancestor_id, descendant_id)
        DO UPDATE SET depth = EXCLUDED.depth
        WHERE person_ancestry.depth > EXCLUDED.depth;

    GET DIAGNOSTICS v_total = ROW_COUNT;

    -- Push the re-seeded ancestors down through the subtree's own edges until nothing changes.
    LOOP
        v_level := v_level + 1;

        INSERT INTO person_ancestry (clan_id, ancestor_id, descendant_id, depth)
        SELECT p_clan_id, a.ancestor_id, e.child_person_id, MIN(a.depth) + 1
        FROM person_ancestry a
                 JOIN parentages e
                      ON e.parent_person_id = a.descendant_id
                          AND e.clan_id = p_clan_id
                          AND e.deleted_at IS NULL
        WHERE a.clan_id = p_clan_id
          AND a.descendant_id = ANY (v_affected)
          AND e.child_person_id = ANY (v_affected)
        GROUP BY a.ancestor_id, e.child_person_id
        ON CONFLICT (ancestor_id, descendant_id)
            DO UPDATE SET depth = EXCLUDED.depth
            WHERE person_ancestry.depth > EXCLUDED.depth;

        GET DIAGNOSTICS v_added = ROW_COUNT;
        v_total := v_total + v_added;

        EXIT WHEN v_added = 0 OR v_level > 64;
    END LOOP;

    RETURN v_total;
END;
$$;

COMMENT ON FUNCTION recompute_person_ancestry_from(UUID, UUID) IS
    'Repairs person_ancestry for one subtree after a parentage edge above it was removed. Bounded '
        'by the subtree size rather than the clan size.';
