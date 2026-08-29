-- V11.1 — opt-in profile propagation (FR-N6).
--
-- The phase document calls this file `V11b`, which Flyway cannot parse: a version is digits and
-- separators only. `V11_1` keeps it adjacent to the claim table it belongs with, and leaves the
-- numbers doc 03 §5 reserved for later phases free.
--
-- A side table rather than columns on `persons`, for one reason: `persons` belongs to Lineage and
-- these flags belong to Linking. A boolean pair on the person row would put Linking's write path
-- inside Lineage's aggregate, and the next thing that wanted a flag would put it there too.
--
-- Default off, in the table and in the code above it. Somebody who fills in a display name is
-- editing their account, not the family book, and a product that quietly rewrites a gia phả entry
-- because of it has done something nobody asked for.

CREATE TABLE person_profile_propagation
(
    person_id                UUID PRIMARY KEY REFERENCES persons (id) ON DELETE CASCADE,

    -- Denormalized so every read here is clan-scoped like every other read (doc 02 §7 rule 4).
    clan_id                  UUID        NOT NULL REFERENCES clans (id) ON DELETE CASCADE,

    -- users.avatar_media_id -> persons.avatar_media_id.
    propagate_avatar         BOOLEAN     NOT NULL DEFAULT FALSE,

    -- users.display_name -> persons.alias, the tên thường gọi. Deliberately never full_name: the
    -- book's name is what the book says, and an account display name is not a genealogical fact.
    propagate_display_name   BOOLEAN     NOT NULL DEFAULT FALSE,

    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_propagation_clan ON person_profile_propagation (clan_id);

COMMENT ON TABLE person_profile_propagation IS
    'Per-field opt-in for copying an account profile onto its linked node (FR-N6). A missing row '
        'means every flag is off, which is the default the product ships with.';
