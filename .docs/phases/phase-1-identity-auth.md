# Phase 1 — Identity & Authentication

**Goal:** real user accounts with secure login, token lifecycle, and a profile — the walking skeleton (M1).

**Estimate:** ~1.5 weeks · **Depends on:** [Phase 0](phase-0-foundation.md) · **Next:** [Phase 2](phase-2-clan-branch.md)

**Requirements:** FR-A1 … FR-A6, NFR-3 (auth portion)

---

## 1. Scope

**In scope** — registration, login, JWT access tokens, rotating refresh tokens, logout / logout-all, email verification and password reset (token generation and validation), profile read/update, rate limiting on auth endpoints, the frontend auth flows and session handling.

**Out of scope** — actual email delivery (tokens are logged in `dev` and surfaced through a test endpoint; SMTP lands in Phase 8), social login, MFA, avatar upload (Phase 7 — the profile stores a `mediaId` that stays null for now).

## 2. Backend Tasks

| # | Task | Detail |
|---|------|--------|
| B1.1 | `identity/domain`: `User`, `EmailAddress`, `PasswordHash`, `UserStatus` | Framework-free; `EmailAddress` normalizes (trim + lowercase) and validates in its constructor |
| B1.2 | Password policy service | ≥ 10 chars, common-password deny list loaded from a resource file |
| B1.3 | `infrastructure`: `UserJpaEntity`, `UserRepository`, MapStruct mapper, `BCryptPasswordHasher` (cost 12) | |
| B1.4 | `RefreshTokenService` | 256-bit opaque token, stored as SHA-256 hash, 30-day TTL, **rotation with reuse detection**: presenting a rotated token revokes the entire chain |
| B1.5 | `JwtService` + `JwtKeyProvider` | HS256, 15-min access token, claims `sub/iat/exp/jti/email/tokenVersion`; **no permission claims** (doc 05 §5) |
| B1.6 | `JwtAuthenticationFilter` + `AuthenticatedUser` principal + `@CurrentUser` argument resolver | |
| B1.7 | `SecurityConfig` for real | Stateless, `/auth/**` and `/actuator/health` public, everything else authenticated; removes the Phase 0 placeholder |
| B1.8 | `AuthService`: register, login, refresh, logout, logout-all | Login failures return one generic error — never reveals whether an email exists |
| B1.9 | `VerificationTokenService` | Purposes `EMAIL_VERIFY` (24 h) / `PASSWORD_RESET` (1 h); single-use; reset revokes all sessions |
| B1.10 | `AccountService`: get/update profile | |
| B1.11 | `AuthController`, `MeController` per [doc 05 §4.1–4.2](../05-api-specification.md) | |
| B1.12 | Rate limiting (bucket4j, in-memory) | Login 10/min/IP + 5/min/account; register & forgot-password 5/min/IP |
| B1.13 | Audit entries for `USER_REGISTERED`, `USER_LOGIN`, `LOGIN_FAILED`, `PASSWORD_RESET` | Table created here so the audit thread starts in Phase 1 |

## 3. Frontend Tasks

| # | Task | Detail |
|---|------|--------|
| F1.1 | `features/auth`: login, register, forgot-password, reset-password pages | AntD `Form` + Zod schemas, i18n strings, styled-components layout |
| F1.2 | `useSessionStore` (Zustand) | Access token in memory; user object; hydration flag |
| F1.3 | Auth wiring in `api/client.ts` | Bearer injection + single-flight refresh with a one-time retry (doc 07 §4.2) |
| F1.4 | `api/endpoints/auth.ts`, `me.ts` | Typed against the generated OpenAPI types |
| F1.5 | `RequireAuth` guard with return-path redirect | |
| F1.6 | `/me` profile page: display name, locale switcher, active sessions, "log out everywhere" | |
| F1.7 | AppShell shows the signed-in user, avatar initials, sign-out | |
| F1.8 | Session bootstrap on app load | Attempts refresh; renders a splash until resolved to avoid a login flash |
| F1.9 | Error surfacing | Field errors from `422` map onto AntD form fields; `code` drives the toast message |
| F1.10 | i18n keys for the whole auth surface (`vi` + `en`) | |

## 4. Migrations

| Version | Contents |
|---------|----------|
| `V2__create_users_and_tokens.sql` | `users` (unique lower(email)), `refresh_tokens`, `verification_tokens` + indexes (doc 03 §3.1–3.2) |
| `V3__create_media_objects.sql` | `media_objects`, referenced by `users.avatar_media_id` (nullable, unused until Phase 7) |
| `V12a__create_audit_entries.sql` | `audit_entries` — pulled forward so auditing starts now; the application role gets `INSERT` only |

## 5. Tests

**Unit** — `EmailAddress` normalization/validation; password policy; `JwtService` issue/verify/expiry/tamper; refresh-token rotation and reuse detection.

**Integration (Testcontainers)** — register → verify → login → refresh → access a protected endpoint → logout; duplicate email returns `409`; login with a wrong password returns the same generic error as an unknown email; an expired access token returns `401 TOKEN_EXPIRED`; a reused refresh token revokes the chain; password reset invalidates every session; rate limiting triggers `429`.

**Security cases** — JWT with an altered payload rejected; `alg: none` rejected; a token for a deactivated user rejected; `password_hash` never appears in any response body (asserted by serializing every DTO in a test).

**Frontend (manual)** — login form validation; the client retries once after a `401` and succeeds; concurrent 401s trigger exactly one refresh call; guard redirects preserve the return path.

**Manual walkthrough** — register → log in → land on the dashboard → reload (session survives) → log out → protected route redirects to login.

## 6. Demo Script

1. Register a new account; the verification token appears in the dev log.
2. Verify, then log in — the dashboard shows the user's name.
3. Reload: the session persists via refresh rotation.
4. Change the display name and locale; the UI switches to English and back to Vietnamese.
5. "Log out everywhere" from a second browser and watch the first session fail its next request and redirect to login.

## 7. Exit Criteria

- [ ] FR-A1…A6 implemented end to end.
- [ ] Access tokens carry no permission claims.
- [ ] Refresh rotation with reuse detection is proven by test.
- [ ] Rate limits enforced on all three sensitive endpoints.
- [ ] No endpoint leaks whether an email address exists.
- [ ] Coverage ≥ 80 % on `identity.application` and `identity.domain`.
- [ ] M1 milestone demo runs locally from seed data.

## 8. Decisions to Confirm in This Phase

| Decision | Options | Default |
|----------|---------|---------|
| Refresh-token transport | `HttpOnly` cookie vs `localStorage` | Cookie when API and web share a site; otherwise `localStorage` with the XSS risk documented in an ADR |
| Email verification enforcement | Block login vs allow with a banner | Allow login, restrict clan creation until verified |
| Access-token TTL | 15 min vs 60 min | 15 min (refresh is transparent) |

## 9. Notes & Risks

- **`tokenVersion`** on `users` lets password reset and "log out everywhere" invalidate outstanding *access* tokens immediately, not just refresh tokens. Worth the one extra column.
- **Don't let auth leak into other contexts.** Downstream code sees only a `UserId`; no other context may load `User` (doc 02 §1.1).
- The `dev`-only endpoint that reveals verification tokens must be `@Profile("dev")` and excluded from the production build — verified by a test asserting it is absent under the `prod` profile.

---

## 10. Phase Report

> Appended on completion, per [doc 09 §5](../09-engineering-conventions.md). What shipped, what
> deviated from the plan above, and the numbers that were measured rather than estimated.

### What shipped

All of §2 and §3. The backend has the full `identity` context — domain, application, infrastructure
and api — behind a real Spring Security chain; the frontend has the five auth screens, the profile
page, session bootstrap, and a client that refreshes transparently.

Three decisions from §8 were taken:

| Decision | Taken | Why |
|----------|-------|-----|
| Refresh-token transport | `localStorage` | The API and the web client are not same-site, so an `HttpOnly` cookie is unavailable. Recorded, with the accepted risk stated plainly, in [ADR 0003](../adr/0003-refresh-token-in-local-storage.md). |
| Email verification | Login allowed, banner shown | The default in §8. Restricting clan creation is Phase 2's to enforce, since clans do not exist yet. |
| Access-token TTL | 15 min (`dev`: 24 h) | The default in §8, with doc 06 §5's development convenience. |

### Deviations from the plan

| Plan | What shipped | Why |
|------|--------------|-----|
| `V12a__create_audit_entries.sql` | `V3_1__create_audit_entries.sql` | Flyway cannot parse `12a` — a version is digits and separators only. `3.1` keeps the numbers doc 03 §5 reserves for Phases 2–4 free and still applies in order. |
| `refresh_tokens` per doc 03 §3.2 | Plus `chain_id` and `revoked_reason` | Reuse detection (task B1.4) needs to identify a rotation family; without a chain id, "revoke the entire chain" has nothing to revoke. `revoked_reason` makes `ROTATED` distinguishable from `REUSE_DETECTED` in the data. |
| `verification_tokens` "same shape" as refresh tokens | `consumed_at` instead of `revoked_at` | These are single-use rather than revocable, and the column name is the difference. |
| `audit_entries`: `INSERT` only for the application role | A `BEFORE UPDATE OR DELETE` trigger | The role split does not exist in this deployment yet. The trigger enforces the same rule inside the database, costs nothing on the insert path, and is asserted by `IdentitySchemaIT`. Phase 8 replaces it with the grant. |
| `GET /me` returns `clans[]` | Field absent | There are no clans until Phase 2, and an always-empty array of an undesigned element type is a contract that would then have to be broken. Adding it is an additive change, which doc 05 §6 allows inside `v1`. |
| `PATCH /me` updates `avatarMediaId` | `displayName` and `locale` only | The only way to obtain a media id is the upload endpoint in Phase 7. Accepting a reference into a table nothing can populate would be theatre. |
| Frontend types generated from OpenAPI (doc 07 §4.4) | Hand-written against doc 05 | The generator's value is the diff that proves the client has not drifted, and nothing runs that diff until CI lands ([ADR 0001](../adr/0001-defer-containerisation-and-ci.md)). Deferred with it. |
| `ApiException` sealed over five subclasses | Six | `RATE_LIMITED` is in doc 05 §2's catalogue but describes the caller's recent history rather than anything about the request, which none of the original five could express. |
| `MEMBERSHIP_EXISTS` for a duplicate registration | New code `EMAIL_ALREADY_REGISTERED` | The existing 409 codes are all about clan membership. Doc 05 §2 updated. |
| `GET /me/sessions` | Added | Task F1.6 asks the profile page for active sessions and doc 05 §4.2 had no endpoint for them. Added there. |

Two additions to the frontend structure, both recorded in doc 07 §2: a `stores/` directory (the app
shell reads the session, and `components/` may not import from `features/`), and `stores/` plus
`hooks/` added to the ESLint shared-layer boundary.

### Measured numbers

| Metric | Result |
|--------|--------|
| Backend unit + architecture tests | 175 pass (`./mvnw test`) |
| Line coverage, `identity.domain` | 97.8 % (132/135) |
| Line coverage, `identity.application` | 98.2 % (275/280) |
| Combined, against the ≥ 80 % gate | **98.1 %** — from unit tests alone, before any integration test runs |
| Initial JS bundle | ~296 KB gzipped (runtime 0.4 + index 158.5 + client 99.8 + query 5.3 + useAuth 32.0) |

Coverage was measured with `./mvnw test jacoco:report`; the gate itself is bound to `verify`, where
the integration tests run, and is configured in `api/pom.xml`.

The bundle is still over doc 07 §10's 250 KB budget, as it was in Phase 0, and for the same reason:
Ant Design's shared core. Route-level splitting is now real — the auth screens, the profile page and
the health page each ship separately — so the figure is the floor rather than everything at once.
Recheck when the tree and clan screens land.

### Not verified here

The integration tests (`AuthFlowIT`, `AuthSecurityIT`, `RateLimitIT`, `AccountIT`,
`IdentitySchemaIT` — 44 cases across five classes) are written and compile, but were **not executed**: this machine has
no Docker daemon, and Testcontainers needs one. They must pass before this is merged. Run:

```bash
cd api && ./mvnw verify
```

The same applies to the §6 demo script, which needs a running PostgreSQL.

### Exit criteria

- [x] FR-A1…A6 implemented end to end.
- [x] Access tokens carry no permission claims — asserted against the encoded payload, not the
      parsed claims, in `JwtServiceTest.carriesNoAuthorizationClaims`.
- [x] Refresh rotation with reuse detection proven by test —
      `RefreshTokenServiceTest.reuseRevokesTheWholeChain`, plus `AuthSecurityIT` end to end.
- [x] Rate limits enforced on all three sensitive endpoints, plus the per-account login limit.
- [x] No endpoint leaks whether an email address exists — except registration, which doc 05 §4.1
      specifies as `409`. `AuthFlowIT.loginFailuresAreIndistinguishable` compares the two failure
      bodies byte for byte.
- [x] Coverage ≥ 80 % on `identity.application` and `identity.domain`.
- [ ] M1 demo runs locally from seed data — pending a machine with PostgreSQL and Docker.

### Notes for Phase 2

- **`JpaAuthenticatedUserAdapter` reads the account on every authenticated request.** That is the
  price of `tokenVersion` being authoritative. It is one indexed primary-key lookup; if a profile
  ever says it matters, a short-lived cache goes there and nowhere else.
- **The rate limiter is in-memory.** Two instances would each grant the full quota. Correct for a
  single-instance v1; a distributed backend is a Bucket4j adapter swap behind `RateLimiter`.
- **`archunit.properties` still sets `failOnEmptyShould=false`.** The identity packages have real
  classes now, but the other six contexts do not. Revisit when they do — turning it on early would
  fail rules for having nothing to check.
- **`/actuator/prometheus` currently requires authentication.** Doc 05 §4.11 wants it restricted to
  an internal network, which is an infrastructure concern; authenticated is the closest this layer
  can get on its own.
