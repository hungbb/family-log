-- V16 — a revocation reason for the sessions a suspension ends.
--
-- Super admins themselves are not stored. They are named by the SUPER_ADMIN_EMAILS environment
-- variable and recognised on each request (see SuperAdminGuard), so revoking one is a redeploy
-- rather than a row nobody remembers to delete. What a super admin can do that needs the schema is
-- suspend an account, and a suspension ends every session it holds.

ALTER TABLE refresh_tokens DROP CONSTRAINT ck_refresh_revoked_reason;
ALTER TABLE refresh_tokens
    ADD CONSTRAINT ck_refresh_revoked_reason CHECK (revoked_reason IS NULL OR revoked_reason IN
                                                    ('ROTATED', 'LOGOUT', 'LOGOUT_ALL',
                                                     'REUSE_DETECTED', 'PASSWORD_RESET',
                                                     'SUSPENDED'));
