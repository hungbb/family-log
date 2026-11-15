# ADR 0007 — Platform super admins, named by configuration

- **Status:** Accepted
- **Date:** 2026-11-14
- **Deciders:** project owner
- **Affects:** 04-authorization-model.md §4 step 2, 01-product-requirements.md persona P5, phase-8 R8.1 (email delivery), 05-api-specification.md §4.12

## Context

Doc 04 models every capability as a clan-scoped grant and leaves platform operators "out of band and
not modelled as members in v1". Running the product now needs two things that no clan grant can
express:

- **Managing accounts across the platform.** Examples: a relative whose verification mail never
  arrived, an abusive account, someone locked out of their password.
- **Platform settings changed at runtime.** The first is the outgoing mail transport. Until now
  nothing could send mail outside `dev`, so in a deployment nobody could verify an address or reset
  a password (Phase 8, R8.1).

## Decision

A **super admin** is an account whose address appears in `SUPER_ADMIN_EMAILS` **and** is verified
**and** can authenticate. The check runs on every call, in one place (`SuperAdminGuard`), and no
role is stored in the database. Super admins manage all accounts (list, suspend, reactivate, mark
verified, resend verification, send a reset link) and the SMTP settings. They hold **nothing
inside a clan**: `PermissionResolver` does not know they exist.

The SMTP settings live in a single-row `mail_settings` table. The password is AES-256-GCM encrypted
with `SETTINGS_ENCRYPTION_KEY`, which has no default outside `dev`/`test`, and it is never returned.
Mail is sent after the requesting transaction commits, on a virtual thread, and never fails the
caller.

## Alternatives Considered

| Option | Pros | Cons | Why not |
|--------|------|------|---------|
| `users.is_super_admin` column, managed in the UI | Promote/demote without a redeploy | An endpoint that can mint platform admins; needs last-admin protection; the flag outlives the intent | A small operator team changes rarely; a redeploy is an acceptable price for having no escalation path |
| A platform-scoped grant in `PermissionResolver` | One decision point for everything | Mixes "may act in this clan" with "may operate the platform"; a bug in one becomes access in the other | The isolation guarantee (G5) is worth more than one fewer class |
| SMTP password only in the environment | No secret in the database | The admin screen could not change the one setting that most often changes | The project owner chose encrypted-at-rest |
| Treat a configured address as verified | No bootstrap step | Whoever registers the address first becomes the operator | That is a takeover of the whole platform |

## Consequences

**Positive** — Verification and password reset work in deployments. Operators can unblock
relatives without SQL. Every admin action is audited with the operator as actor. The B3.15 ArchUnit
rule now also holds `UserAdministrationService` and `platform.application` to a guard
(`SuperAdminGuard.require`).

**Negative** — Bootstrapping a fresh installation needs one manual step. The first super admin
cannot verify by mail before mail is configured, so their address is verified once by SQL (see
`deploy/prod/README.md`). Messages are bilingual (vi + en) because the delivery port does not carry
the recipient's locale. Rotating `SETTINGS_ENCRYPTION_KEY` makes the stored SMTP password unreadable
until it is re-entered.

**Follow-up** — Delivered in two changes: super admins and user management first, then the mail
settings and SMTP delivery. Until the second lands, deployments still send no mail. Doc 04 §4 step 2 now has a counterpart for platform operators (this ADR). A
deployment needs `SETTINGS_ENCRYPTION_KEY` (required) and `SUPER_ADMIN_EMAILS` (optional). Further
platform settings join `/admin/settings`. Carrying the locale on `VerificationTokenDelivery` would
allow single-language mail.
