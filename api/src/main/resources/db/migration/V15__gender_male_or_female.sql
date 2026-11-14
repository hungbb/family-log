-- V15 — a person's gender is male or female.
--
-- OTHER and UNKNOWN are retired. Every existing person recorded with either becomes MALE — the role
-- the server already gave such a person when it had to pick a parent role — and the column loses its
-- default, so a person is never created without a gender being chosen.
--
-- The rewritten rows get a new version and updated_at, and their clans a new tree version, so an
-- open editor's optimistic lock and a cached tree read (B4.12 ETag) both see the change.

WITH rewritten AS (
    UPDATE persons
    SET gender     = 'MALE',
        version    = version + 1,
        updated_at = now()
    WHERE gender NOT IN ('MALE', 'FEMALE')
    RETURNING clan_id
)
UPDATE clan_tree_versions
SET tree_version = tree_version + 1,
    updated_at   = now()
WHERE clan_id IN (SELECT DISTINCT clan_id FROM rewritten);

ALTER TABLE persons
    ALTER COLUMN gender DROP DEFAULT,
    DROP CONSTRAINT ck_persons_gender,
    ADD CONSTRAINT ck_persons_gender CHECK (gender IN ('MALE', 'FEMALE'));
