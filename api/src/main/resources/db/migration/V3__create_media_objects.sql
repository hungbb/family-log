-- V3 — media_objects, and the users.avatar_media_id foreign key that needs it.
--
-- Nothing writes to this table until Phase 7 ships uploads. It exists now because `users` (V2) and
-- `clans` (V4) both carry a media reference, and adding the column later would mean a second
-- migration against a populated table for no benefit. Doc 03 §5 puts it in Phase 1 for exactly
-- this reason.

CREATE TABLE media_objects
(
    id            UUID PRIMARY KEY,
    storage_key   VARCHAR(512) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    byte_size     BIGINT       NOT NULL,
    width         INTEGER,
    height        INTEGER,
    -- Derived renditions, keyed by name: {"thumb": {"key": "...", "width": 128, "height": 128}}.
    variants      JSONB        NOT NULL DEFAULT '{}'::JSONB,
    owner_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    -- No FK: `clans` does not exist until V4, and a media object may be account-scoped rather than
    -- clan-scoped. Phase 2 adds the constraint alongside the table it points at.
    clan_id       UUID,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_media_byte_size CHECK (byte_size > 0),
    CONSTRAINT ck_media_dimensions CHECK ((width IS NULL) = (height IS NULL))
);

-- The storage key is the file's identity on disk; two rows pointing at one file would make
-- deletion unsafe.
CREATE UNIQUE INDEX ux_media_storage_key ON media_objects (storage_key);
CREATE INDEX ix_media_owner ON media_objects (owner_user_id);

-- RESTRICT, not CASCADE: deleting the image should never delete the account that references it.
ALTER TABLE users
    ADD CONSTRAINT fk_users_avatar_media
        FOREIGN KEY (avatar_media_id) REFERENCES media_objects (id) ON DELETE RESTRICT;

COMMENT ON TABLE media_objects IS
    'The single indirection point between database rows and files on the media volume. Populated from Phase 7.';
