-- V13 — in-app notifications (doc 03 §3.13, B6.9).
--
-- One of the three tables doc 06 §2.1 exempts from the domain/entity split: there is no invariant
-- here beyond "read_at is either null or a time", and a payload the reader renders. Email delivery
-- is Phase 8; this table is what the bell in the top bar polls.

CREATE TABLE notifications
(
    id                UUID PRIMARY KEY,
    recipient_user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    -- Null for anything that is not about one clan. Everything Phase 6 writes has one.
    clan_id           UUID REFERENCES clans (id) ON DELETE CASCADE,

    type              VARCHAR(40) NOT NULL,

    -- What the client needs to render the line and link to the thing it is about: names, ids, an
    -- outcome. JSONB rather than columns because the shape differs per type and a notification is
    -- read once and never queried by its contents.
    payload           JSONB       NOT NULL DEFAULT '{}'::JSONB,

    read_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The unread badge, polled every 60 s by every signed-in client (F6.10). Partial, so the index
-- holds only what the badge counts rather than every notification ever delivered.
CREATE INDEX ix_notif_unread ON notifications (recipient_user_id, created_at DESC)
    WHERE read_at IS NULL;

-- The full list, which the notification panel pages through.
CREATE INDEX ix_notif_recipient ON notifications (recipient_user_id, created_at DESC);

COMMENT ON TABLE notifications IS
    'In-app notifications, polled rather than pushed (F6.10). Phase 8 adds email delivery over the '
        'same rows.';
