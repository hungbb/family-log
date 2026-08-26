-- V2 — the identity tables: accounts, refresh-token sessions, and single-use verification tokens.
--
-- Shapes follow .docs/03-data-model.md §3.1–3.2. Two columns are additions to that document and
-- are explained where they are declared: `users.token_version` and `refresh_tokens.chain_id`.

CREATE TABLE users
(
    id                UUID PRIMARY KEY,
    email             VARCHAR(320) NOT NULL,
    password_hash     VARCHAR(100) NOT NULL,
    display_name      VARCHAR(150) NOT NULL,
    avatar_media_id   UUID,
    locale            VARCHAR(10)  NOT NULL DEFAULT 'vi-VN',
    status            VARCHAR(30)  NOT NULL,

    -- Bumped by "log out everywhere" and by a password reset. Access tokens carry the value they
    -- were minted with, so a bump invalidates every outstanding *access* token immediately rather
    -- than leaving a 15-minute window in which a stolen one still works. One extra column buys the
    -- only revocation a stateless bearer token can otherwise not have (phase doc §9).
    token_version     BIGINT       NOT NULL DEFAULT 0,

    email_verified_at TIMESTAMPTZ,
    last_login_at     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_users_status CHECK (status IN
                                      ('ACTIVE', 'PENDING_VERIFICATION', 'SUSPENDED', 'DEACTIVATED')),
    CONSTRAINT ck_users_locale CHECK (locale IN ('vi-VN', 'en-US')),
    -- I-U1: normalization is the domain's job (EmailAddress trims and lowercases). This is the
    -- backstop that catches a write which bypassed it.
    CONSTRAINT ck_users_email_normalized CHECK (email = lower(btrim(email))),
    CONSTRAINT ck_users_email_shape CHECK (position('@' IN email) > 1),
    CONSTRAINT ck_users_display_name CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT ck_users_token_version CHECK (token_version >= 0)
);

-- I-U1. Expressed on lower(email) rather than on email so it holds even if the CHECK above is
-- ever relaxed; doc 03 §3.1 specifies exactly this index.
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));

COMMENT ON COLUMN users.token_version IS
    'Incremented to invalidate every outstanding access token for this user at once.';
COMMENT ON COLUMN users.avatar_media_id IS
    'FK to media_objects, added in V3. Null until Phase 7 ships uploads.';


-- Refresh-token sessions. The raw token is a 256-bit random value that exists only in the
-- response body and in the client; the server keeps a SHA-256 of it, so a database read cannot
-- be replayed as a session.
CREATE TABLE refresh_tokens
(
    id             UUID PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    -- The rotation family. Every refresh mints a successor in the same chain, so detecting the
    -- reuse of an already-rotated token lets us revoke the whole line in one statement rather
    -- than walking a `replaced_by` pointer. Not in doc 03 §3.2; required by phase task B1.4.
    chain_id       UUID         NOT NULL,

    token_hash     VARCHAR(100) NOT NULL,
    issued_at      TIMESTAMPTZ  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    revoked_at     TIMESTAMPTZ,
    revoked_reason VARCHAR(20),
    user_agent     VARCHAR(255),
    ip_address     VARCHAR(45),

    CONSTRAINT ck_refresh_revocation_paired CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL)),
    CONSTRAINT ck_refresh_revoked_reason CHECK (revoked_reason IS NULL OR revoked_reason IN
                                                ('ROTATED', 'LOGOUT', 'LOGOUT_ALL', 'REUSE_DETECTED',
                                                 'PASSWORD_RESET')),
    CONSTRAINT ck_refresh_expiry_after_issue CHECK (expires_at > issued_at)
);

CREATE UNIQUE INDEX ux_refresh_token_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_user_active ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
-- The reuse-detection path: revoke every live token in the presented token's family.
CREATE INDEX ix_refresh_chain ON refresh_tokens (chain_id) WHERE revoked_at IS NULL;

COMMENT ON COLUMN refresh_tokens.revoked_reason IS
    'Why this token stopped being usable. ROTATED is the normal end of life; REUSE_DETECTED means '
        'the whole chain was killed because a rotated token was presented again.';


-- Email verification and password reset. Same shape as refresh tokens plus a purpose, and
-- `consumed_at` instead of `revoked_at` because these are single-use rather than revocable.
CREATE TABLE verification_tokens
(
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose     VARCHAR(20)  NOT NULL,
    token_hash  VARCHAR(100) NOT NULL,
    issued_at   TIMESTAMPTZ  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    consumed_at TIMESTAMPTZ,

    CONSTRAINT ck_verification_purpose CHECK (purpose IN ('EMAIL_VERIFY', 'PASSWORD_RESET')),
    CONSTRAINT ck_verification_expiry_after_issue CHECK (expires_at > issued_at)
);

CREATE UNIQUE INDEX ux_verification_token_hash ON verification_tokens (token_hash);
CREATE INDEX ix_verification_user_purpose
    ON verification_tokens (user_id, purpose) WHERE consumed_at IS NULL;
