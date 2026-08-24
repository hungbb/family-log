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

**Frontend** — login form validation; the client retries once after a `401` and succeeds; concurrent 401s trigger exactly one refresh call (MSW request counting); guard redirects preserve the return path.

**E2E** — register → log in → land on the dashboard → reload (session survives) → log out → protected route redirects to login.

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
- [ ] M1 milestone demo runs on staging.

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
