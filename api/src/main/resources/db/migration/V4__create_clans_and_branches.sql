-- V4 — clans and their branch structure (doc 03 §3.3–3.4).
--
-- Two tables that reference each other: a branch belongs to a clan, and a clan names one branch as
-- its root. The cycle is real and unavoidable — I-C2 says a clan has exactly one root branch — so
-- `clans.root_branch_id` carries a DEFERRABLE foreign key and is filled in later in the same
-- transaction that inserts the clan and its root branch.
--
-- `branch_root_person_id` is created here without its foreign key. Persons do not exist until
-- Phase 4, and adding the column then would mean a second migration against a populated table for
-- no benefit; V10 adds the constraint alongside `persons`.

CREATE TABLE clans
(
    id             UUID PRIMARY KEY,

    -- I-C1: generated once, never changed, never reissued. The uniqueness index below deliberately
    -- carries no `WHERE deleted_at IS NULL` predicate — a soft-deleted clan must keep its code
    -- reserved, or a code somebody shared last year could start pointing at a different family.
    clan_code      VARCHAR(32)  NOT NULL,

    name           VARCHAR(200) NOT NULL,
    description    TEXT,
    origin_place   VARCHAR(255),
    cover_media_id UUID REFERENCES media_objects (id) ON DELETE RESTRICT,
    visibility     VARCHAR(20)  NOT NULL DEFAULT 'PRIVATE',

    -- Nullable only for the instant between the clan insert and the root-branch insert that
    -- follows it. `ClanService.create` closes that gap inside one transaction; the deferrable FK
    -- added at the foot of this file is what lets the two statements be written in that order.
    root_branch_id UUID,

    version        BIGINT       NOT NULL DEFAULT 0,
    created_by     UUID REFERENCES users (id) ON DELETE RESTRICT,
    updated_by     UUID REFERENCES users (id) ON DELETE RESTRICT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMPTZ,

    -- PRIVATE is the only value v1 has (doc 02 §2.2). Listed as a CHECK rather than assumed so the
    -- day a second one appears, it appears in a migration and not in a surprise.
    CONSTRAINT ck_clans_visibility CHECK (visibility IN ('PRIVATE')),
    CONSTRAINT ck_clans_name CHECK (length(btrim(name)) > 0),
    -- SURNAME-XXXX, where the suffix is Crockford base32 — the alphabet with I, L, O and U removed,
    -- because those are the characters a person reading a code aloud gets wrong.
    CONSTRAINT ck_clans_code_shape CHECK (clan_code ~ '^[A-Z0-9]{1,20}-[0-9A-HJKMNP-TV-Z]{4,8}$'),
    CONSTRAINT ck_clans_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX ux_clans_code ON clans (clan_code);

COMMENT ON COLUMN clans.clan_code IS
    'Immutable, shareable join code (I-C1). Unique across all clans including deleted ones, so a '
        'code is never reissued.';
COMMENT ON COLUMN clans.root_branch_id IS
    'The clan single root branch (I-C2). Set within the creating transaction; the FK is deferred '
        'so the clan row may be written before the branch it points at.';


CREATE TABLE branches
(
    id                    UUID PRIMARY KEY,
    clan_id               UUID         NOT NULL REFERENCES clans (id) ON DELETE CASCADE,

    -- NULL marks the root branch. `ux_branches_one_root` below is what turns "the root" into a
    -- fact the database holds rather than a convention the application remembers (I-C2).
    parent_branch_id      UUID REFERENCES branches (id) ON DELETE RESTRICT,

    branch_code           VARCHAR(32)  NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    description           TEXT,

    -- Phase 4 adds `FOREIGN KEY ... REFERENCES persons (id)` in V10 (I-C5). Until then this column
    -- exists and stays null; the phase-2 API does not accept a value for it.
    branch_root_person_id UUID,

    -- The materialized path of doc 02 §3: '/' plus one eight-hex-character segment per ancestor,
    -- ending in '/'. A root branch's path is '/<own segment>/'. Descendant queries are then a
    -- prefix match, which `ix_branches_path` serves.
    path                  VARCHAR(512) NOT NULL,
    depth                 SMALLINT     NOT NULL,

    archived_at           TIMESTAMPTZ,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_by            UUID REFERENCES users (id) ON DELETE RESTRICT,
    updated_by            UUID REFERENCES users (id) ON DELETE RESTRICT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ,

    -- I-C7. The cap is not arbitrary: it bounds the path length, bounds the subtree rewrite a move
    -- performs, and bounds how far the UI has to indent.
    CONSTRAINT ck_branches_depth CHECK (depth >= 0 AND depth <= 10),
    -- I-C4, the cheap half: only the root sits at depth 0, and the root is the only parentless
    -- branch. The path itself is checked in the application, where the ancestry is known.
    CONSTRAINT ck_branches_root_depth CHECK ((parent_branch_id IS NULL) = (depth = 0)),
    CONSTRAINT ck_branches_not_own_parent CHECK (parent_branch_id IS DISTINCT FROM id),
    CONSTRAINT ck_branches_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_branches_code_shape CHECK (branch_code ~ '^[A-Z0-9]{1,20}-[0-9A-HJKMNP-TV-Z]{4,8}$'),
    CONSTRAINT ck_branches_path_shape CHECK (path LIKE '/%/' AND length(path) >= 10),
    CONSTRAINT ck_branches_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX ux_branches_code ON branches (branch_code);
CREATE INDEX ix_branches_clan ON branches (clan_id) WHERE deleted_at IS NULL;

-- Powers `path LIKE '/a1b2c3d4/%'`. `varchar_pattern_ops` is required for a prefix match to use the
-- index under any collation other than C (doc 03 §3.4).
CREATE INDEX ix_branches_path ON branches (clan_id, path varchar_pattern_ops);

-- I-C2: exactly one root per clan.
CREATE UNIQUE INDEX ux_branches_one_root ON branches (clan_id)
    WHERE parent_branch_id IS NULL AND deleted_at IS NULL;

-- Path segments are eight-character short forms of a UUID, so two branches in one clan colliding is
-- vanishingly unlikely — but a collision would silently make one branch's descendant query return
-- the other's subtree, which is a tenancy-shaped bug inside a single clan. This index turns it into
-- an insert failure that the creating service retries with a fresh id instead.
CREATE UNIQUE INDEX ux_branches_path ON branches (clan_id, path) WHERE deleted_at IS NULL;

-- Listing a branch's children, which is every render of the branch tree.
CREATE INDEX ix_branches_parent ON branches (clan_id, parent_branch_id) WHERE deleted_at IS NULL;

COMMENT ON COLUMN branches.path IS
    'Materialized ancestry, e.g. /a1b2c3d4/9f8e7d6c/. Rewritten for the whole subtree when a branch '
        'is re-parented (I-C4).';
COMMENT ON COLUMN branches.branch_root_person_id IS
    'The person this branch descends from. Null until Phase 4 wires it up; the FK arrives in V10.';


-- The other half of the cycle. DEFERRABLE INITIALLY DEFERRED so a transaction may insert the clan,
-- insert its root branch, and point the clan at it, in that order, without an intermediate state
-- the constraint would reject.
ALTER TABLE clans
    ADD CONSTRAINT fk_clans_root_branch
        FOREIGN KEY (root_branch_id) REFERENCES branches (id) ON DELETE RESTRICT
            DEFERRABLE INITIALLY DEFERRED;


-- Promised by V3: `media_objects.clan_id` has waited for this table since Phase 1. RESTRICT rather
-- than CASCADE — a clan's soft delete must not take its images with it, and a hard delete should
-- stop rather than destroy files other rows may still reference.
ALTER TABLE media_objects
    ADD CONSTRAINT fk_media_clan
        FOREIGN KEY (clan_id) REFERENCES clans (id) ON DELETE RESTRICT;
