-- V11 — node claiming (doc 03 §3.12, FR-N1…FR-N3).
--
-- A claim is somebody saying "this row in the book is me". It is deliberately a request rather than
-- a self-service link: a gia phả has two Nguyễn Văn Ans of the same đời more often than not, and the
-- only person who can tell them apart is a relative who knew both.
--
-- Note on numbering: V12 is reserved by doc 03 §5 for the audit table, which Phase 1 shipped early
-- as V3_1. The number stays unused rather than being recycled, so the mapping in doc 03 §5 still
-- reads true.

CREATE TABLE node_claim_requests
(
    id                UUID PRIMARY KEY,
    clan_id           UUID        NOT NULL REFERENCES clans (id) ON DELETE CASCADE,

    -- The node being claimed. RESTRICT rather than CASCADE: a person is soft-deleted, never
    -- removed, so a claim losing its subject would mean the row went somewhere it should not have.
    person_id         UUID        NOT NULL REFERENCES persons (id) ON DELETE RESTRICT,

    requester_user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    -- What the requester offers as proof: "con của ông An, sinh 1985". Free text on purpose — the
    -- evidence a family actually has is a sentence, not a form.
    evidence          TEXT,

    status            VARCHAR(20) NOT NULL,
    decided_by        UUID REFERENCES users (id) ON DELETE RESTRICT,
    decided_at        TIMESTAMPTZ,
    decision_reason   TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_claim_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    -- I-K5, the same one-way door as ck_join_decision_paired: everything that leaves PENDING is
    -- stamped with when it left, and only a live claim has no decision time.
    CONSTRAINT ck_claim_decision_paired CHECK ((status = 'PENDING') = (decided_at IS NULL))
);

-- I-K3: at most one PENDING claim per (requester, person). Partial, so somebody whose claim was
-- rejected in error can be told to submit another rather than having the old one reopened.
CREATE UNIQUE INDEX ux_claim_pending ON node_claim_requests (requester_user_id, person_id)
    WHERE status = 'PENDING';

-- The approver inbox (doc 05 §4.10), newest first.
CREATE INDEX ix_claim_clan_status ON node_claim_requests (clan_id, status, created_at DESC);

-- "My claims" on /me (F6.4).
CREATE INDEX ix_claim_requester ON node_claim_requests (requester_user_id, created_at DESC);

-- The re-check on approval reads the live claims for one node.
CREATE INDEX ix_claim_person ON node_claim_requests (person_id, status);

COMMENT ON TABLE node_claim_requests IS
    'A member asking to be linked to a person node (FR-N1). Approval sets persons.linked_user_id '
        'and nothing else — being in the tree is not permission to edit it (doc 04 §6).';
COMMENT ON COLUMN node_claim_requests.evidence IS
    'Free text the approver reads to tell two same-named relatives apart. Never parsed.';
