-- V6 — the grant table (doc 03 §3.11). The whole authorization model is rows in here.
--
-- No `role` column anywhere in the schema, and this is the reason (PRD decision D2, doc 04 §1.1):
-- a capability is an explicit row naming a person, a permission and a place. Any number of people
-- may hold the same one, and taking it back is a timestamp rather than a delete, because "who could
-- change this last March?" is a question families ask.

CREATE TABLE permission_grants
(
    id               UUID PRIMARY KEY,
    clan_id          UUID        NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    grantee_user_id  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    permission       VARCHAR(30) NOT NULL,
    scope_type       VARCHAR(10) NOT NULL,

    -- A branch id or (from Phase 4) a person id. Deliberately no foreign key: it points at one of
    -- two tables depending on scope_type, and the alternative — two nullable columns with a check
    -- that exactly one is set — buys referential integrity at the cost of every query growing a
    -- COALESCE. The scope is validated by the application before it is written, and a scope naming
    -- a branch that does not exist covers nothing rather than everything (ScopeCoverage).
    scope_ref_id     UUID,

    granted_by       UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    granted_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ,
    revoked_at       TIMESTAMPTZ,
    revoked_by       UUID REFERENCES users (id) ON DELETE RESTRICT,

    CONSTRAINT ck_grant_permission CHECK (permission IN
                                          ('CLAN_VIEW', 'TREE_EDIT', 'MEMBER_APPROVE',
                                           'NODE_CLAIM_APPROVE', 'ROLE_MANAGE', 'CLAN_ADMIN')),
    CONSTRAINT ck_grant_scope_type CHECK (scope_type IN ('CLAN', 'BRANCH', 'SUBTREE')),

    -- I-G1. A CLAN scope names nothing and the other two must name something. Without this, a
    -- BRANCH grant with a null reference would resolve to "covers no branch", which reads in a
    -- table like a grant somebody holds and behaves like one they do not.
    CONSTRAINT ck_grant_scope_ref CHECK ((scope_type = 'CLAN') = (scope_ref_id IS NULL)),

    -- I-G2. An administrator of half a clan is a contradiction: the settings and the branch tree
    -- they would administer belong to the clan as a whole (doc 04 §2).
    CONSTRAINT ck_grant_admin_is_clan_wide CHECK (permission <> 'CLAN_ADMIN' OR scope_type = 'CLAN'),

    -- MEMBER_APPROVE has no subtree form: being admitted to a clan is about branches, and a person
    -- node is not a place anyone can be admitted into.
    CONSTRAINT ck_grant_member_approve_scope CHECK (permission <> 'MEMBER_APPROVE'
        OR scope_type IN ('CLAN', 'BRANCH')),

    -- A revoked grant may leave the revoker null — that is the nightly expiry sweep, which has no
    -- human behind it — but a live grant cannot name one.
    CONSTRAINT ck_grant_revocation CHECK (revoked_at IS NOT NULL OR revoked_by IS NULL),
    CONSTRAINT ck_grant_expiry_after_grant CHECK (expires_at IS NULL OR expires_at > granted_at)
);

-- I-G5: one live grant per (person, clan, permission, scope). Partial, so the history of a
-- permission granted, revoked and granted again is kept rather than overwritten. COALESCE gives
-- the clan-wide case a value the index can compare, since NULLs never collide with one another.
CREATE UNIQUE INDEX ux_grant_active ON permission_grants
    (grantee_user_id,
     clan_id,
     permission,
     scope_type,
     (COALESCE(scope_ref_id, '00000000-0000-0000-0000-000000000000'::UUID)))
    WHERE revoked_at IS NULL;

-- The hot path (doc 03 §3.11, doc 06 §7): every authorization check in the product starts by
-- loading one user's grants for one clan. Partial on revoked_at because a revoked row is never
-- part of that answer, which keeps the index the size of what is currently in force rather than
-- of everything ever issued.
CREATE INDEX ix_grant_lookup ON permission_grants (grantee_user_id, clan_id)
    WHERE revoked_at IS NULL;

-- The permissions screen (doc 05 §4.6) and last-admin protection (I-M3), which counts the clan's
-- live CLAN_ADMIN holders and must not read the clan's whole grant history to do it.
CREATE INDEX ix_grant_clan_permission ON permission_grants (clan_id, permission)
    WHERE revoked_at IS NULL;

-- The nightly hygiene sweep reads exactly this: grants whose expiry has passed and which nobody
-- has marked revoked yet.
CREATE INDEX ix_grant_expiry ON permission_grants (expires_at)
    WHERE revoked_at IS NULL AND expires_at IS NOT NULL;

COMMENT ON TABLE permission_grants IS
    'Additive only: effective access is the union of a user''s grants, with no negative rules, so '
        'resolution is total and order-independent (doc 04 §1.3). Grants are effective only while '
        'the grantee holds an ACTIVE clan_memberships row (I-M2).';

COMMENT ON COLUMN permission_grants.expires_at IS
    'Filtered at query time as well as swept nightly: a deployment whose sweep has not run must '
        'not hand out an extra hour of access.';
