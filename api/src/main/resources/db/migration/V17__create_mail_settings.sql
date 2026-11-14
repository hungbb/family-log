-- V17 — the outgoing mail transport a super admin configures (ADR 0007).

-- The SMTP transport, as one row. Platform-wide by nature: there is no clan to scope it to.
CREATE TABLE mail_settings
(
    -- A UUID like every other key (ConventionsTest: Hibernate never converts an identifier), fixed
    -- to one well-known value. `singleton` is what actually enforces one row: a second row would
    -- leave every reader to guess which one is live.
    id                  UUID PRIMARY KEY,
    singleton           BOOLEAN      NOT NULL DEFAULT TRUE,
    enabled             BOOLEAN      NOT NULL DEFAULT FALSE,
    host                VARCHAR(255),
    port                INTEGER,
    username            VARCHAR(320),
    -- AES-GCM, keyed by SETTINGS_ENCRYPTION_KEY, as "v1:" + base64(iv || ciphertext || tag). A
    -- database dump therefore does not hand over the mailbox; the key lives only in the
    -- environment. Never returned by the API.
    password_ciphertext TEXT,
    security            VARCHAR(10)  NOT NULL DEFAULT 'STARTTLS',
    from_address        VARCHAR(320),
    from_name           VARCHAR(150),
    version             BIGINT       NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Not a foreign key, for the same reason audit rows are not: the record of who changed the
    -- transport must survive the account.
    updated_by          UUID,

    CONSTRAINT ck_mail_settings_singleton CHECK (singleton),
    CONSTRAINT ux_mail_settings_singleton UNIQUE (singleton),
    CONSTRAINT ck_mail_settings_security CHECK (security IN ('NONE', 'STARTTLS', 'SSL')),
    CONSTRAINT ck_mail_settings_port CHECK (port IS NULL OR port BETWEEN 1 AND 65535),
    -- An enabled transport with nowhere to connect, or no sender, fails on every message.
    CONSTRAINT ck_mail_settings_enabled_complete CHECK (
        NOT enabled OR (host IS NOT NULL AND port IS NOT NULL AND from_address IS NOT NULL))
);

-- Present from the start, disabled, so reads never have to treat "no row" as a state.
INSERT INTO mail_settings (id) VALUES ('00000000-0000-0000-0000-00000000a11e');

COMMENT ON TABLE mail_settings IS
    'The SMTP transport for outgoing mail. Exactly one row; edited by super admins at runtime.';
