# Gia Ký

A platform for Vietnamese families to build, preserve and collaborate on their *gia phả* — the
clan lineage record. Multi-clan, multi-branch, with fine-grained permissions that let one relative
manage their own branch without touching anyone else's.

- `api/` — Spring Boot 3.5 · Java 21 · PostgreSQL 16 · Flyway
- `web/` — React 19 · TypeScript · Vite · Ant Design 5 · styled-components
- `.docs/` — the design source of truth. Start at [`.docs/00-README.md`](.docs/00-README.md).

> **Status: Phase 2 (clans & branch structure) — milestone M2.** On top of Phase 1's accounts, you
> can found a clan, share its join code, ask to join one with a code, approve or refuse those
> requests, build the tree of *chi* and *nhánh* the family divides into, and switch between the
> clans you belong to. There is no person and no family tree yet — those are Phase 4.
> See [`.docs/08-implementation-roadmap.md`](.docs/08-implementation-roadmap.md).
>
> Until Phase 3 ships the permission model, **the person who created a clan is the only one who can
> administer it.** That rule lives in exactly one class, `InterimClanAccessCheck`, and is marked for
> removal.

---

## Running it, in about five minutes

### 0. What you need

| Tool | Version | Check |
|------|---------|-------|
| JDK | 21 | `java -version` |
| Node.js | ≥ 22.12, 64-bit | `node -v` and `node -p "process.arch"` |
| PostgreSQL | 16 | `psql --version` |

Maven is not required — `api/mvnw` downloads it on first use.

> **Node must be 64-bit.** Vite's bundler ships a native binary; installing dependencies under a
> 32-bit Node produces a `node_modules` that cannot build. If `process.arch` prints `ia32`, switch
> and reinstall.

### 1. Create the database

Once, from a superuser shell (`psql -U postgres`):

```sql
CREATE ROLE giaky WITH LOGIN PASSWORD 'giaky';
CREATE DATABASE giaky OWNER giaky;
\c giaky
GRANT ALL ON SCHEMA public TO giaky;
```

The first migration installs the `pgcrypto`, `unaccent` and `pg_trgm` extensions, which needs
rights the `giaky` role does not have by default. Either grant them once as superuser:

```sql
-- as postgres, connected to the giaky database
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

…or, on a personal machine, give the role superuser and let Flyway do it: `ALTER ROLE giaky
SUPERUSER;`. The migration is written `IF NOT EXISTS`, so either order works.

### 2. Start the API

```bash
cd api
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Flyway migrates on startup. Then:

- <http://localhost:8080/actuator/health> — should report `UP`, with `components.db.status` also `UP`
- <http://localhost:8080/swagger-ui.html> — the OpenAPI document

The `dev` profile defaults to `jdbc:postgresql://localhost:5432/giaky` with user and password
`giaky`. If yours differs, copy `api/.env.example` and set the variables in your environment.

### 3. Start the web app

```bash
cd web
npm install
npm run dev
```

Open <http://localhost:5173>. You land on the sign-in screen.

### 4. Walk through an account

There is no mail server until Phase 8, so under the `dev` profile the API logs the link a real
message would have carried, and a `dev`-only endpoint serves the raw token. Neither exists in any
other profile, and a test asserts it.

1. **Register** at <http://localhost:5173/register>. The account is created in
   `PENDING_VERIFICATION` and signed in immediately; a banner asks for the address to be confirmed.
2. **Open the verification link from the API log.** It is ready to click:

   ```
   [dev delivery] EMAIL_VERIFY link for you@example.com: http://localhost:5173/verify-email?token=… (expires …)
   ```

   The banner goes away. If you would rather have the bare token — a script, say — the endpoint is:

   ```bash
   curl "http://localhost:8080/api/v1/dev/verification-tokens?email=you@example.com"
   ```

3. **Reload the page.** The session survives: the client exchanges its stored refresh token for a
   new pair before rendering.
4. **Edit your profile** at <http://localhost:5173/me> — change the display name and the language,
   and watch the interface switch. The page also lists every browser signed in to the account.
5. **Sign out everywhere** from that page, then try the other browser: its next request fails and it
   is redirected to the sign-in screen.

A password reset works the same way: submit <http://localhost:5173/forgot-password> and follow the
`PASSWORD_RESET` link from the log. The endpoint form is
`…/dev/verification-tokens?email=you@example.com&purpose=PASSWORD_RESET`.

The links point at `WEB_BASE_URL` (`http://localhost:5173` by default) — see `api/.env.example` if
your web client runs somewhere else.

If the health badge at <http://localhost:5173/health> is red, the page shows the error code and
correlation id. The usual cause is that the API is not running; the second is a
`CORS_ALLOWED_ORIGINS` that does not include `http://localhost:5173`.

---

## Everyday commands

### `api/`

```bash
./mvnw test                                      # unit + architecture tests — no Docker needed
./mvnw verify                                    # the above plus *IT integration tests — needs Docker
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Integration tests run against a real PostgreSQL through Testcontainers, so `verify` needs a Docker
daemon. `test` deliberately does not — the architecture rules and unit tests stay runnable
everywhere.

### `web/`

```bash
npm run dev               # Vite dev server on :5173
npm run verify            # lint + constraint fixtures + tsc + build — run this before pushing
npm run lint              # eslint --max-warnings=0
npm run lint:constraints  # proves ESLint still blocks Axios, Tailwind, .css imports, raw fetch
npm run typecheck         # tsc --noEmit
npm run format            # prettier --write .
npm run build             # production bundle into dist/
```

A husky pre-commit hook runs ESLint, Prettier and `tsc --noEmit` on staged frontend files. It is
installed by `npm install` inside `web/`.

---

## Layout

```
family-log/
  .docs/            design documents and ADRs — the source of truth
  api/              Spring Boot application
    src/main/java/app/giaky/
      common/       cross-cutting: error, security, web, types, audit
      config/       Spring @Configuration
      identity/ clanorg/ lineage/ authorization/ linking/ media/ notification/
                    one bounded context each, layered domain → application → infrastructure → api
    src/main/resources/db/migration/    Flyway migrations
  web/
    src/
      api/          the fetch client and one module per backend context
      components/   shared, app-agnostic UI
      features/     one folder per bounded context, mirroring the backend
      stores/       Zustand stores; the session lives here, because the shell reads it
      hooks/ lib/   shared hooks and pure helpers
      theme/        tokens, the AntD theme, the global stylesheet
      i18n/         vi (default) and en
    eslint-fixtures/  files that must fail lint; see `npm run lint:constraints`
```

Every package under `api/src/main/java/app/giaky/` carries a `package-info.java` stating the rules
that apply inside it. Those rules are enforced, not merely documented — see below.

---

## The guardrails

The point of Phase 0 is that the constraints in [`.docs/draft.md`](.docs/draft.md) §3.2 and the
layering in [`.docs/06-backend-architecture.md`](.docs/06-backend-architecture.md) §2.1 are
mechanical, not a matter of review discipline. Both are cheap now and expensive to retrofit.

**Authentication** (Phase 1, `.docs/phases/phase-1-identity-auth.md`):

- HS256 access tokens, 15 minutes, carrying `sub / iat / exp / jti / email / tokenVersion` and
  **no permission or clan claims** — permissions change far more often than a token lives.
- Opaque 256-bit refresh tokens, stored as a SHA-256 hash, rotated on every use. Presenting an
  already-rotated token revokes the entire chain, so a stolen token is loud rather than silent.
- `users.token_version` lets a password reset and "log out everywhere" invalidate outstanding
  *access* tokens immediately, rather than leaving a fifteen-minute window.
- Every failure to sign in answers the same way, in about the same time, whether the address exists
  or not.
- Rate limits: login 10/min/IP and 5/min/account; register and forgot-password 5/min/IP.
- The refresh token lives in `localStorage` — cross-site deployment, so an `HttpOnly` cookie is not
  available. The trade is recorded in [ADR 0003](.docs/adr/0003-refresh-token-in-local-storage.md).

**Clans and branches** (Phase 2, `.docs/phases/phase-2-clan-branch.md`):

- A clan is the tenancy boundary. Every clan-scoped read takes a `ClanId`, and a branch id from
  another clan answers `404` — not `403`, which would confirm that the id belongs to something.
- A non-member gets `404` for every clan endpoint, and a member who lacks a permission gets `403`.
  A private clan does not confirm its own existence to somebody outside it.
- Join codes are `SURNAME-XXXX` in Crockford base32 — no `I`, `L`, `O` or `U`, because a code's
  whole job is to survive being read aloud and typed back in. They are generated once, never
  changed, and never reissued, even after a clan is deleted.
- Branches form a tree with a materialized path, capped at ten deep. Re-parenting rewrites the path
  of every branch beneath, in one statement inside one transaction; a property test re-derives every
  path from the parent links after hundreds of random moves.
- A join request that nobody answers is closed after 90 days by an hourly sweep, guarded by a
  PostgreSQL advisory lock so two instances do not both run it.

**Backend — ArchUnit** (`api/src/test/java/app/giaky/architecture/`):

- `domain` depends on nothing but the JDK and `common.types` — no Spring, no JPA
- `application` never depends on `api`; `api` never touches repositories or JPA entities
- no context reaches into another context's `domain` or `infrastructure`
- controllers never inject repositories
- every repository read takes a `ClanId` or carries `@TenantExempt` with a justification
- plus the conventions: constructor injection only, `@Transactional` only on application services,
  no `System.out`, `Optional` never a parameter

`identity` and `clanorg` have real classes and are really checked; the four remaining contexts are
still empty and pass trivially. Each starts biting the moment its layer gains a class.

**Frontend — ESLint** (`web/eslint.config.js`):

- no Axios, `ky`, or any other HTTP client — everything goes through `src/api/client.ts`
- no Tailwind or any utility-CSS framework
- no `.css` imports; the one global stylesheet is `theme/GlobalStyle.ts`
- no raw `fetch` outside the client
- no `style` prop on DOM elements
- `components/`, `hooks/`, `lib/`, `stores/` and `theme/` may not import from `features/`

`web/eslint-fixtures/` holds files that break each of these on purpose, and
`npm run lint:constraints` asserts that every one is still caught. A guardrail nobody tests is a
guardrail that quietly stops working.

---

## Known gaps in this phase

- **No CI, no Docker, no frontend test runner.** Deferred deliberately — see
  [ADR 0001](.docs/adr/0001-defer-containerisation-and-ci.md). Until CI exists, `./mvnw verify` and
  `npm run verify` are a convention, and the pre-commit hook is the only mechanical gate.
- **No email delivery.** Verification and reset tokens are logged and served by a `dev`-only
  endpoint. SMTP is Phase 8; in any profile that is neither `dev` nor `test`, a token is issued and
  discarded with a `WARN`, so nobody can complete verification there. That is deliberate and loud.
- **The initial JS bundle is ~296 KB gzipped**, over the 250 KB budget in
  [doc 07 §10](.docs/07-frontend-architecture.md). It is almost entirely Ant Design. Route-level
  splitting is in place from Phase 1 and the auth screens ship in their own chunks; the shared
  AntD core is what exceeds the budget. Recheck once the tree and clan screens exist.
- **Frontend API types are hand-written** against doc 05 rather than generated by
  `openapi-typescript` (doc 07 §4.4). The generator's value is the diff that proves the client has
  not drifted, and nothing runs that diff until CI lands with ADR 0001's workstream.
- **ESLint is pinned to 9.x.** `eslint-plugin-react` and `eslint-plugin-jsx-a11y` have not declared
  ESLint 10 support; installing it needs `--legacy-peer-deps`, which is a worse trade than staying
  on the maintenance line. Revisit when those plugins ship.
- **The palette diverges from doc 07 §3.1** — modern minimalist rather than lacquer-red heritage.
  See [ADR 0002](.docs/adr/0002-modern-minimalist-palette.md).

---

## Conventions

Read [`.docs/09-engineering-conventions.md`](.docs/09-engineering-conventions.md) before your first
pull request. The short version: trunk-based branching off `main`, Conventional Commits, one
migration per PR and never edited after merge, two reviewers for anything touching
`authorization/`, and every bug gets a failing test before its fix.

Deviations from `.docs/` are recorded as ADRs in [`.docs/adr/`](.docs/adr/) — not in a PR comment.
