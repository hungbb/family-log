-- V3.1 — the audit trail.
--
-- Doc 03 §5 numbers this migration V12 and places it in Phase 3; the Phase 1 plan pulls it forward
-- so the audit thread starts with the first mutation the product has (registration, login,
-- password reset). It ships as V3.1 rather than V12 so the numbers reserved by doc 03 §5 for
-- Phases 2–4 stay free and still apply in order. (The phase document calls this file `V12a`,
-- which Flyway cannot parse — a version is digits and separators only.)
--
-- Append-only. Doc 03 §3.13 achieves that by withholding UPDATE and DELETE from the application
-- role, which needs a role split this deployment does not have yet; until it does, the trigger
-- below enforces the same rule inside the database and costs nothing on the INSERT path.

CREATE TABLE audit_entries
(
    id                   UUID PRIMARY KEY,
    -- Null for actions that are not clan-scoped, which is all of them in Phase 1.
    clan_id              UUID,
    -- Deliberately not a foreign key: an audit row must outlive anything it refers to, and an
    -- ON DELETE action would be a write against an append-only table.
    actor_user_id        UUID,
    action               VARCHAR(60) NOT NULL,
    target_type          VARCHAR(40) NOT NULL,
    target_id            UUID,
    before_json          JSONB,
    after_json           JSONB,
    -- Which grant authorised the action (FR-Z8). Null until Phase 3 introduces grants.
    authorizing_grant_id UUID,
    ip_address           VARCHAR(45),
    correlation_id       VARCHAR(64),
    occurred_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_clan_time ON audit_entries (clan_id, occurred_at DESC);
CREATE INDEX ix_audit_target ON audit_entries (target_type, target_id);
-- "What did this account do?" — the question asked during an abuse investigation.
CREATE INDEX ix_audit_actor_time ON audit_entries (actor_user_id, occurred_at DESC);

CREATE OR REPLACE FUNCTION audit_entries_are_append_only()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    RAISE EXCEPTION 'audit_entries is append-only: % is not permitted', tg_op
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER trg_audit_entries_append_only
    BEFORE UPDATE OR DELETE
    ON audit_entries
    FOR EACH STATEMENT
EXECUTE FUNCTION audit_entries_are_append_only();

COMMENT ON TABLE audit_entries IS
    'Append-only action log. Partitioned by month from Phase 8; the append-only trigger is replaced '
        'by an INSERT-only grant once the deployment role split lands.';
