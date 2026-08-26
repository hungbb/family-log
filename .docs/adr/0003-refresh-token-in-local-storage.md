# ADR 0003 — Keep the refresh token in `localStorage`, not in a cookie

- **Status:** Accepted
- **Date:** 2026-08-26
- **Deciders:** hungbb
- **Affects:** [05-api-specification.md](../05-api-specification.md) §5 ("Token storage (client)");
  [07-frontend-architecture.md](../07-frontend-architecture.md) §4.2, §6;
  [phases/phase-1-identity-auth.md](../phases/phase-1-identity-auth.md) §8

## Context

Phase 1 §8 leaves the refresh-token transport open, with the default written as: *"Cookie when API and
web share a site; otherwise `localStorage` with the XSS risk documented in an ADR."* This is that
ADR.

The API and the web client **do not** share a site, in development or in the deployment shape v1
plans for:

- development is `http://localhost:5173` (Vite) against `http://localhost:8080` (Spring Boot) —
  different ports, and for cookie purposes a different origin;
- doc 06 §5 and doc 05 §5 describe the API as a separately addressed service behind a reverse proxy,
  with an explicit CORS allow-list per environment. Nothing in the plan puts the two on one host.

A `Secure; HttpOnly; SameSite=Strict` cookie is therefore not available. `SameSite=None` would be —
it is what cross-site cookies require — but it costs the CSRF protection that was the point of
choosing a cookie in the first place, forces `Access-Control-Allow-Credentials: true` on the API
(which doc 05 §5 explicitly declines: *"credentials via the `Authorization` header only"*), and is
increasingly restricted by browsers phasing out third-party cookies. The result would be strictly
worse than either honest option.

The remaining alternative — holding both tokens in memory only — is genuinely more secure and
produces a product where every page reload signs the user out.

## Decision

The refresh token is stored in `localStorage` under `giaky.refreshToken`. The access token lives in
memory only, in the Zustand session store, and is never persisted.

## Alternatives Considered

| Option | Pros | Cons | Why not |
|--------|------|------|---------|
| `HttpOnly` cookie, `SameSite=Strict` | Unreadable by any script; the strongest option against XSS | Needs API and web on one site, which this deployment shape does not have | Not available to us |
| `HttpOnly` cookie, `SameSite=None; Secure` | Works cross-site; still unreadable by script | Reintroduces CSRF (so CSRF tokens come back), requires `allowCredentials`, and browsers are restricting third-party cookies | Trades a real, mitigated risk for two new ones |
| Access **and** refresh token in memory only | No persisted credential at all | Every reload, and every new tab, requires signing in again | Rejected on the product; people would keep long-lived sessions open elsewhere instead, which is worse |
| `sessionStorage` | Cleared when the tab closes | Same XSS exposure as `localStorage`, and still loses the session on a reload in some flows | The security difference is marginal; the usability cost is not |

## Consequences

**Positive** — a session survives a reload and a restart, which is what the product needs. No cookie
means no CSRF surface: a cross-site form can make a browser attach cookies, but it cannot make it
attach an `Authorization` header, which is why `SecurityConfig` can leave CSRF off honestly rather
than as an oversight. CORS stays `allowCredentials: false`.

**Negative — stated plainly.** A successful XSS on the web origin can read the refresh token and use
it to mint access tokens for as long as the chain lives. `HttpOnly` would have prevented exactly
that. This is the risk the decision accepts, and it is accepted because the alternatives available
to *this* deployment shape are worse, not because the risk is small.

What limits the damage:

- **Rotation with reuse detection** (task B1.4). A stolen token is single-use like any other. The
  moment either the thief or the real client presents an already-rotated token, the whole chain is
  revoked and both are signed out — so theft is loud rather than silent, and bounded by the interval
  between the client's own refreshes rather than by the 30-day TTL.
- **`tokenVersion`** on `users`. "Log out everywhere" and a password reset invalidate outstanding
  *access* tokens immediately, not just refresh tokens, so a person who suspects compromise has a
  control that works within one request.
- **The access token is never persisted**, so the more frequently used credential is not sitting in
  storage.
- **Sessions are visible.** `GET /me/sessions` lists them with user agent, address and issue time,
  so an unrecognised session can be spotted and ended.

**Follow-up**

- The real mitigation for the accepted risk is not having an XSS. The measures that prevent one are
  Phase 8's: a strict `Content-Security-Policy` on the served assets, and the dependency and SAST
  scanning deferred by [ADR 0001](0001-defer-containerisation-and-ci.md). Both should cite this ADR.
- If the deployment ever puts the API and the web client on one site — `app.giaky.vn` and
  `app.giaky.vn/api` — this decision should be revisited immediately. The change is confined to
  `web/src/stores/useSessionStore.ts` and the CORS configuration, and would supersede this ADR.
- Doc 05 §5 and doc 07 §4.2 describe the choice as open. They stay as written; this ADR is the
  record of which branch was taken and why.
