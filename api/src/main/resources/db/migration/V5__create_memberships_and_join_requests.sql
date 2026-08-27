-- V5 — who belongs to a clan, and who has asked to (doc 03 §3.5–3.6).
--
-- Deliberately no `role` column. Phase 3's grant model is the design (PRD decision D2), and a
-- temporary enum here would leak into the API, into the UI, and into everyone's mental model long
-- before it could be taken out again. Until Phase 3 lands, "who may administer a clan" is answered
-- by `clans.created_by` alone, in one class — see `InterimClanAccessCheck`.

CREATE TABLE clan_memberships
(
    id             UUID PRIMARY KEY,
    clan_id        UUID        NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    -- Which branch this member belongs to. Optional: a member approved into the clan as a whole
    -- has no home branch until somebody places them.
    home_branch_id UUID REFERENCES branches (id) ON DELETE RESTRICT,

    status         VARCHAR(20) NOT NULL,
    joined_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at        TIMESTAMPTZ,
    invited_by     UUID REFERENCES users (id) ON DELETE RESTRICT,
    approved_by    UUID REFERENCES users (id) ON DELETE RESTRICT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_membership_status CHECK (status IN ('ACTIVE', 'LEFT', 'REMOVED')),
    -- An ended membership records when it ended, and a live one cannot. Without this, "active" and
    -- "has no leaving date" drift apart and every query has to guess which one it meant.
    CONSTRAINT ck_membership_left_at CHECK ((status = 'ACTIVE') = (left_at IS NULL))
);

-- I-M1: at most one ACTIVE membership per (clan, user). Partial, so the history of someone who
-- left and rejoined is kept rather than overwritten.
CREATE UNIQUE INDEX ux_membership_active ON clan_memberships (clan_id, user_id)
    WHERE status = 'ACTIVE';

-- The clan switcher (FR-C8): "which clans is this person in?", asked on every page load.
CREATE INDEX ix_membership_user ON clan_memberships (user_id) WHERE status = 'ACTIVE';

-- The member list filtered by branch (doc 05 §4.5).
CREATE INDEX ix_membership_clan_branch ON clan_memberships (clan_id, home_branch_id)
    WHERE status = 'ACTIVE';

COMMENT ON TABLE clan_memberships IS
    'Membership is the prerequisite for every grant in a clan (I-M2). Revoking it is what Phase 3 '
        'hangs grant deactivation on.';


CREATE TABLE clan_join_requests
(
    id                UUID PRIMARY KEY,
    clan_id           UUID        NOT NULL REFERENCES clans (id) ON DELETE CASCADE,

    -- Null means "the clan as a whole". A request carrying a branch came from a shared branch code,
    -- which is how a relative joins straight into the right chi.
    target_branch_id  UUID REFERENCES branches (id) ON DELETE RESTRICT,

    requester_user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    message           TEXT,
    status            VARCHAR(20) NOT NULL,
    decided_by        UUID REFERENCES users (id) ON DELETE RESTRICT,
    decided_at        TIMESTAMPTZ,
    decision_reason   TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- created_at + 90 days. Set by the application rather than as a column default, because the
    -- clock is injected (doc 09 §4.6) and a test that cannot age a request cannot test expiry.
    expires_at        TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_join_status CHECK (status IN
                                     ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED')),
    -- I-J4 is a one-way door: everything that leaves PENDING is stamped with when it left. That
    -- includes the sweep, which stamps the time but no actor.
    CONSTRAINT ck_join_decision_paired CHECK ((status = 'PENDING') = (decided_at IS NULL)),
    CONSTRAINT ck_join_expiry_after_creation CHECK (expires_at > created_at)
);

-- I-J1: one pending request per (requester, clan, target branch). COALESCE gives the "whole clan"
-- case a value the index can compare, since NULLs never collide with one another.
CREATE UNIQUE INDEX ux_join_pending ON clan_join_requests
    (requester_user_id,
     clan_id,
     (COALESCE(target_branch_id, '00000000-0000-0000-0000-000000000000'::UUID)))
    WHERE status = 'PENDING';

-- The approver's inbox (doc 05 §4.5), newest first.
CREATE INDEX ix_join_clan_status ON clan_join_requests (clan_id, status, created_at DESC);

-- "My requests" on /me (FR-J5).
CREATE INDEX ix_join_requester ON clan_join_requests (requester_user_id, created_at DESC);

-- The nightly sweep reads exactly this: pending requests past their date, and nothing else.
CREATE INDEX ix_join_expiry ON clan_join_requests (expires_at) WHERE status = 'PENDING';

COMMENT ON COLUMN clan_join_requests.expires_at IS
    'A request nobody answers is closed after 90 days, so an approver inbox stays a list of live '
        'decisions rather than an archive.';
