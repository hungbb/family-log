-- V7 — the person node (doc 03 §3.7). The table goal G1 is about.
--
-- A person here is a row in a family book, not an account. `linked_user_id` is nullable and stays
-- null for almost every ancestor a clan records, because the people a gia phả is mostly about are
-- dead and were never going to sign in. Everything the tree does — placement, relationships,
-- generation, search — works with that column empty; Phase 6 fills it in for the living.
--
-- Dates are flattened rather than stored as a composite type or JSON (doc 03 §3.7). Most ancestors
-- have a year and nothing else, some have only "khoảng 1890", and a few carry a lunar date that no
-- Gregorian column can hold. Plain columns per date make every one of those cases a WHERE clause an
-- index can serve, which a JSON blob would not.

CREATE TABLE persons
(
    id                   UUID PRIMARY KEY,

    -- The tenancy key, on every query without exception (doc 02 §7 rule 4).
    clan_id              UUID         NOT NULL REFERENCES clans (id) ON DELETE CASCADE,

    -- Organizational placement, and therefore the target a BRANCH-scoped grant is matched against
    -- (doc 04 §3). NULL means an unplaced node, which no branch grant can reach — which is why
    -- creating one is restricted to TREE_EDIT@CLAN holders (phase 4 §8).
    branch_id            UUID REFERENCES branches (id) ON DELETE RESTRICT,

    full_name            VARCHAR(200) NOT NULL,
    family_name          VARCHAR(100),
    given_name           VARCHAR(100),
    alias                VARCHAR(100),
    posthumous_name      VARCHAR(100),

    -- Diacritic-folded full-text search (doc 03 §6). Generated rather than maintained by a trigger
    -- or by the application: a stored generated column cannot drift from the columns it derives
    -- from, and "nguyen van an" finding "Nguyễn Văn An" is a promise every search box here makes.
    -- f_unaccent() is the IMMUTABLE wrapper V1 created for exactly this.
    name_search          TSVECTOR GENERATED ALWAYS AS (
                             to_tsvector('simple',
                                         f_unaccent(coalesce(full_name, '') || ' ' ||
                                                    coalesce(alias, '') || ' ' ||
                                                    coalesce(posthumous_name, '') || ' ' ||
                                                    coalesce(family_name, '') || ' ' ||
                                                    coalesce(given_name, '')))
                             ) STORED,

    gender               VARCHAR(10)  NOT NULL DEFAULT 'UNKNOWN',
    life_status          VARCHAR(10)  NOT NULL DEFAULT 'UNKNOWN',

    -- Birth, at whatever precision the record actually has.
    birth_year           SMALLINT,
    birth_month          SMALLINT,
    birth_day            SMALLINT,
    birth_precision      VARCHAR(10)  NOT NULL DEFAULT 'UNKNOWN',
    birth_circa          BOOLEAN      NOT NULL DEFAULT FALSE,
    birth_range_end_year SMALLINT,
    birth_lunar_text     VARCHAR(100),

    death_year           SMALLINT,
    death_month          SMALLINT,
    death_day            SMALLINT,
    death_precision      VARCHAR(10)  NOT NULL DEFAULT 'UNKNOWN',
    death_circa          BOOLEAN      NOT NULL DEFAULT FALSE,
    death_range_end_year SMALLINT,
    death_lunar_text     VARCHAR(100),

    burial_place         VARCHAR(255),

    -- đời. Recomputed from parentage edges and deliberately nullable: an imported or fragmentary
    -- record may have no path to the founding ancestor, and 0 would be a lie the UI would render
    -- as a fact (phase 4 §8).
    generation           SMALLINT,
    birth_order          SMALLINT,

    avatar_media_id      UUID REFERENCES media_objects (id) ON DELETE RESTRICT,

    -- Sanitized on write, never on read (doc 02 §3). notes_text is the plain projection that search
    -- and excerpts read, so neither has to strip tags at query time.
    notes_html           TEXT,
    notes_text           TEXT,

    linked_user_id       UUID REFERENCES users (id) ON DELETE RESTRICT,
    is_founding_ancestor BOOLEAN      NOT NULL DEFAULT FALSE,

    version              BIGINT       NOT NULL DEFAULT 0,
    created_by           UUID REFERENCES users (id) ON DELETE RESTRICT,
    updated_by           UUID REFERENCES users (id) ON DELETE RESTRICT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMPTZ,

    -- I-P1.
    CONSTRAINT ck_persons_full_name CHECK (length(btrim(full_name)) > 0),
    CONSTRAINT ck_persons_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'UNKNOWN')),
    CONSTRAINT ck_persons_life_status CHECK (life_status IN ('ALIVE', 'DECEASED', 'UNKNOWN')),

    CONSTRAINT ck_persons_birth_precision CHECK (birth_precision IN
                                                 ('EXACT', 'MONTH', 'YEAR', 'RANGE', 'UNKNOWN')),
    CONSTRAINT ck_persons_death_precision CHECK (death_precision IN
                                                 ('EXACT', 'MONTH', 'YEAR', 'RANGE', 'UNKNOWN')),
    CONSTRAINT ck_persons_birth_month CHECK (birth_month IS NULL OR birth_month BETWEEN 1 AND 12),
    CONSTRAINT ck_persons_death_month CHECK (death_month IS NULL OR death_month BETWEEN 1 AND 12),
    CONSTRAINT ck_persons_birth_day CHECK (birth_day IS NULL OR birth_day BETWEEN 1 AND 31),
    CONSTRAINT ck_persons_death_day CHECK (death_day IS NULL OR death_day BETWEEN 1 AND 31),

    -- A day without a month, or a month without a year, is a date nobody can render or compare.
    CONSTRAINT ck_persons_birth_shape CHECK ((birth_month IS NULL OR birth_year IS NOT NULL)
        AND (birth_day IS NULL OR birth_month IS NOT NULL)),
    CONSTRAINT ck_persons_death_shape CHECK ((death_month IS NULL OR death_year IS NOT NULL)
        AND (death_day IS NULL OR death_month IS NOT NULL)),
    CONSTRAINT ck_persons_birth_range CHECK (birth_range_end_year IS NULL
        OR (birth_year IS NOT NULL AND birth_range_end_year >= birth_year)),
    CONSTRAINT ck_persons_death_range CHECK (death_range_end_year IS NULL
        OR (death_year IS NOT NULL AND death_range_end_year >= death_year)),

    -- I-P3. Somebody recorded as living cannot also have a date of death; the two facts contradict
    -- one another, and the pair is exactly what a careless import produces.
    CONSTRAINT ck_persons_alive_has_no_death CHECK (life_status <> 'ALIVE' OR death_year IS NULL),

    CONSTRAINT ck_persons_generation CHECK (generation IS NULL OR generation >= 1),
    CONSTRAINT ck_persons_birth_order CHECK (birth_order IS NULL OR birth_order >= 1),
    CONSTRAINT ck_persons_version CHECK (version >= 0)
);

CREATE INDEX ix_persons_clan ON persons (clan_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_persons_branch ON persons (clan_id, branch_id) WHERE deleted_at IS NULL;

-- I-P4: one node per user per clan. Partial on both columns, so many unlinked nodes coexist and a
-- soft-deleted node releases the user to be linked somewhere else.
CREATE UNIQUE INDEX ux_persons_linked_user ON persons (clan_id, linked_user_id)
    WHERE linked_user_id IS NOT NULL AND deleted_at IS NULL;

-- I-P6: one founding ancestor per clan. đời 1 is a single person by definition.
CREATE UNIQUE INDEX ux_persons_founding ON persons (clan_id)
    WHERE is_founding_ancestor AND deleted_at IS NULL;

CREATE INDEX ix_persons_name_search ON persons USING GIN (name_search);

-- Prefix and typo tolerance, which the tsvector cannot give: "ngu" while somebody is still typing,
-- and "nguyen vsn" from a phone keyboard (doc 03 §6 step 3).
CREATE INDEX ix_persons_name_trgm ON persons USING GIN (f_unaccent(lower(full_name)) gin_trgm_ops);

CREATE INDEX ix_persons_generation ON persons (clan_id, generation) WHERE deleted_at IS NULL;

-- The "recently deleted" list an administrator restores from (FR-P10, doc 05 §4.7).
CREATE INDEX ix_persons_deleted ON persons (clan_id, deleted_at) WHERE deleted_at IS NOT NULL;

COMMENT ON TABLE persons IS
    'A node in the family tree, decoupled from any user account (G1). linked_user_id is null for '
        'almost every row, and nothing in Lineage requires it.';
COMMENT ON COLUMN persons.generation IS
    'đời, 1 = founding ancestor. Advisory: recomputed from parentage edges, null while unknown. '
        'Clients render null as unknown rather than defaulting to 0.';
COMMENT ON COLUMN persons.branch_id IS
    'Organizational placement (I-P8, same clan). Null = unplaced, reachable only by a clan-wide or '
        'subtree grant.';


-- The per-clan tree version behind the tree-read ETag (B4.12).
--
-- Its own table rather than a column on `clans`, and the reason is optimistic locking: `clans`
-- carries a @Version that clan edits check, so bumping it on every person insert would answer a
-- concurrent clan rename with 409 VERSION_CONFLICT for a change that had nothing to do with it. A
-- row per clan also keeps the lineage write path off the clan row entirely, so two editors adding
-- cousins never contend with the settings screen.
CREATE TABLE clan_tree_versions
(
    clan_id      UUID PRIMARY KEY REFERENCES clans (id) ON DELETE CASCADE,
    tree_version BIGINT      NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_tree_version_non_negative CHECK (tree_version >= 0)
);

COMMENT ON TABLE clan_tree_versions IS
    'Bumped by every lineage mutation and rendered as the ETag on tree reads, so an unchanged tree '
        'answers 304 (doc 05 §4.9).';
