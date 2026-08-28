-- V8 — the edges (doc 03 §3.8–3.9). Two tables, and between them the whole shape of a family.
--
-- `parentages` is the structural one: it is what the closure projection is built from, what
-- generation is computed from, and what a cycle would corrupt. `unions` is the social one — it
-- records marriages, including the multiple wives a Vietnamese gia phả routinely lists, and carries
-- no structural weight beyond grouping children under the marriage they came from.

CREATE TABLE parentages
(
    id                UUID PRIMARY KEY,

    -- Denormalized from the two endpoints so every read filters on one indexed column rather than
    -- joining `persons` twice to find out whose clan an edge belongs to.
    clan_id           UUID        NOT NULL REFERENCES clans (id) ON DELETE CASCADE,

    parent_person_id  UUID        NOT NULL REFERENCES persons (id) ON DELETE RESTRICT,
    child_person_id   UUID        NOT NULL REFERENCES persons (id) ON DELETE RESTRICT,

    parent_role       VARCHAR(10) NOT NULL,
    nature            VARCHAR(12) NOT NULL DEFAULT 'BIOLOGICAL',

    -- Set when the child was attached through a marriage rather than to one parent alone. Null is
    -- normal: most recorded edges in an old book name a father and nothing else.
    union_id          UUID,

    created_by        UUID REFERENCES users (id) ON DELETE RESTRICT,
    updated_by        UUID REFERENCES users (id) ON DELETE RESTRICT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ,

    -- I-R1. The one cycle a single row can express, refused by the database rather than only by
    -- the application, because it is the one a bad import produces in bulk.
    CONSTRAINT ck_parentage_not_self CHECK (parent_person_id <> child_person_id),
    CONSTRAINT ck_parentage_role CHECK (parent_role IN ('FATHER', 'MOTHER')),
    CONSTRAINT ck_parentage_nature CHECK (nature IN ('BIOLOGICAL', 'ADOPTED', 'STEP', 'FOSTER'))
);

-- I-R3: one father and one mother. `con nuôi` (an adopted child) is a `nature`, not a third parent
-- edge — a child adopted by an uncle still has the father the book records, and the product must
-- not force a choice between the two facts.
CREATE UNIQUE INDEX ux_parentage_child_role ON parentages (child_person_id, parent_role)
    WHERE deleted_at IS NULL;

-- "Who are this person's children?" — every tree assembly, every generation recompute.
CREATE INDEX ix_parentage_parent ON parentages (parent_person_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_parentage_child ON parentages (child_person_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_parentage_clan ON parentages (clan_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_parentage_union ON parentages (union_id) WHERE union_id IS NOT NULL AND deleted_at IS NULL;

COMMENT ON TABLE parentages IS
    'Parent → child edges. A DAG, not a tree: a child has up to two parents. Cycle prevention '
        '(I-R4) is enforced in the application against person_ancestry before insert, because it is '
        'a reachability question no CHECK constraint can ask.';


CREATE TABLE unions
(
    id                   UUID PRIMARY KEY,
    clan_id              UUID        NOT NULL REFERENCES clans (id) ON DELETE CASCADE,

    -- Stored with partner_a_id < partner_b_id (doc 03 §3.9). The pair is unordered — a marriage has
    -- no first partner — and canonicalizing it on write is what turns "at most one union per
    -- unordered pair" (I-N2) into a plain unique index instead of two rows the application has to
    -- remember to check in both directions.
    partner_a_id         UUID        NOT NULL REFERENCES persons (id) ON DELETE RESTRICT,
    partner_b_id         UUID        NOT NULL REFERENCES persons (id) ON DELETE RESTRICT,

    union_type           VARCHAR(12) NOT NULL DEFAULT 'PRIMARY',
    status               VARCHAR(12) NOT NULL DEFAULT 'UNKNOWN',

    start_year           SMALLINT,
    start_month          SMALLINT,
    start_day            SMALLINT,
    start_precision      VARCHAR(10) NOT NULL DEFAULT 'UNKNOWN',
    start_circa          BOOLEAN     NOT NULL DEFAULT FALSE,
    start_range_end_year SMALLINT,
    start_lunar_text     VARCHAR(100),

    end_year             SMALLINT,
    end_month            SMALLINT,
    end_day              SMALLINT,
    end_precision        VARCHAR(10) NOT NULL DEFAULT 'UNKNOWN',
    end_circa            BOOLEAN     NOT NULL DEFAULT FALSE,
    end_range_end_year   SMALLINT,
    end_lunar_text       VARCHAR(100),

    notes                TEXT,

    created_by           UUID REFERENCES users (id) ON DELETE RESTRICT,
    updated_by           UUID REFERENCES users (id) ON DELETE RESTRICT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMPTZ,

    -- I-N1.
    CONSTRAINT ck_union_distinct_partners CHECK (partner_a_id <> partner_b_id),
    -- The canonical ordering the unique index below depends on.
    CONSTRAINT ck_union_canonical_order CHECK (partner_a_id < partner_b_id),
    -- SECONDARY is the vợ lẽ of an older book; SEQUENTIAL is a remarriage after the first ended.
    -- Both appear in real gia phả and neither is a variant of the other.
    CONSTRAINT ck_union_type CHECK (union_type IN ('PRIMARY', 'SECONDARY', 'SEQUENTIAL')),
    CONSTRAINT ck_union_status CHECK (status IN ('ACTIVE', 'ENDED', 'WIDOWED', 'UNKNOWN')),
    CONSTRAINT ck_union_start_precision CHECK (start_precision IN
                                               ('EXACT', 'MONTH', 'YEAR', 'RANGE', 'UNKNOWN')),
    CONSTRAINT ck_union_end_precision CHECK (end_precision IN
                                             ('EXACT', 'MONTH', 'YEAR', 'RANGE', 'UNKNOWN')),
    CONSTRAINT ck_union_start_month CHECK (start_month IS NULL OR start_month BETWEEN 1 AND 12),
    CONSTRAINT ck_union_end_month CHECK (end_month IS NULL OR end_month BETWEEN 1 AND 12),
    CONSTRAINT ck_union_start_day CHECK (start_day IS NULL OR start_day BETWEEN 1 AND 31),
    CONSTRAINT ck_union_end_day CHECK (end_day IS NULL OR end_day BETWEEN 1 AND 31),
    CONSTRAINT ck_union_start_shape CHECK ((start_month IS NULL OR start_year IS NOT NULL)
        AND (start_day IS NULL OR start_month IS NOT NULL)),
    CONSTRAINT ck_union_end_shape CHECK ((end_month IS NULL OR end_year IS NOT NULL)
        AND (end_day IS NULL OR end_month IS NOT NULL))
);

-- I-N2, made trivial by the canonical ordering above.
CREATE UNIQUE INDEX ux_union_pair ON unions (partner_a_id, partner_b_id) WHERE deleted_at IS NULL;

CREATE INDEX ix_unions_clan ON unions (clan_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_unions_partner_a ON unions (partner_a_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_unions_partner_b ON unions (partner_b_id) WHERE deleted_at IS NULL;

-- The parentage side of the union link, added after `unions` exists.
ALTER TABLE parentages
    ADD CONSTRAINT fk_parentage_union
        FOREIGN KEY (union_id) REFERENCES unions (id) ON DELETE RESTRICT;

COMMENT ON TABLE unions IS
    'Marriages and partnerships. Multiple wives are normal in the books this product digitizes: a '
        'PRIMARY union and any number of SECONDARY ones may be live at once for the same person '
        '(I-N3 restricts only PRIMARY).';
