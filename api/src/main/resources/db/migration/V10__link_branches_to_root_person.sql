-- V10 — the foreign key V4 promised (I-C5).
--
-- `branches.branch_root_person_id` has existed since Phase 2 and stayed null, because the table it
-- points at did not exist yet. Now it does. Declaring the constraint here rather than adding the
-- column here is the whole reason V4 created it unconstrained: the alternative was a second
-- migration adding a column to a populated `branches` table for no benefit.
--
-- RESTRICT, not CASCADE or SET NULL. A branch root is the person a whole chi is defined by, and a
-- hard delete of that row should stop rather than quietly orphan the branch — soft delete is the
-- operation the product actually offers, and it leaves the id intact.

ALTER TABLE branches
    ADD CONSTRAINT fk_branches_root_person
        FOREIGN KEY (branch_root_person_id) REFERENCES persons (id) ON DELETE RESTRICT;

-- The lookup behind one authorization rule: whether a SUBTREE grant covers a whole branch, which
-- asks whether that branch's root person is in the grant's descendant set (doc 04 §5.4). Partial,
-- because almost every branch has no root person recorded and those rows answer nothing.
CREATE INDEX ix_branches_root_person ON branches (clan_id, branch_root_person_id)
    WHERE branch_root_person_id IS NOT NULL AND deleted_at IS NULL;

COMMENT ON COLUMN branches.branch_root_person_id IS
    'The person this branch descends from (I-C5). Set from Phase 4; a person placed with '
        'placement.type = BRANCH_ROOT becomes one.';
