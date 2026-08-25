# Gia Ký

A platform for Vietnamese families to build, preserve and collaborate on their *gia phả* — the
clan lineage record. Multi-clan, multi-branch, with fine-grained permissions that let one relative
manage their own branch without touching anyone else's.

- `api/` — Spring Boot 3.5 · Java 21 · PostgreSQL 16 · Flyway
- `web/` — React 19 · TypeScript · Vite · Ant Design 5 · styled-components
- `.docs/` — the design source of truth. Start at [`.docs/00-README.md`](.docs/00-README.md).

> **Status: Phase 0 (foundation).** Both applications build, run and test. There is no
> authentication, no clan, no person and no tree yet — one health page proves the wire path from
> the browser through to PostgreSQL. See [`.docs/08-implementation-roadmap.md`](.docs/08-implementation-roadmap.md).

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

Open <http://localhost:5173>. The shell renders with the theme applied and a green **API healthy**
badge, fed by a real cross-origin request through the fetch client.

If the badge is red, the page shows the error code and correlation id. The usual cause is that the
API is not running; the second is a `CORS_ALLOWED_ORIGINS` that does not include
`http://localhost:5173`.

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

**Backend — ArchUnit** (`api/src/test/java/app/giaky/architecture/`):

- `domain` depends on nothing but the JDK and `common.types` — no Spring, no JPA
- `application` never depends on `api`; `api` never touches repositories or JPA entities
- no context reaches into another context's `domain` or `infrastructure`
- controllers never inject repositories
- every repository read takes a `ClanId` or carries `@TenantExempt` with a justification
- plus the conventions: constructor injection only, `@Transactional` only on application services,
  no `System.out`, `Optional` never a parameter

Most of these currently check empty packages and pass trivially. Each starts biting the moment its
layer gains a class.

**Frontend — ESLint** (`web/eslint.config.js`):

- no Axios, `ky`, or any other HTTP client — everything goes through `src/api/client.ts`
- no Tailwind or any utility-CSS framework
- no `.css` imports; the one global stylesheet is `theme/GlobalStyle.ts`
- no raw `fetch` outside the client
- no `style` prop on DOM elements
- `components/`, `lib/` and `theme/` may not import from `features/`

`web/eslint-fixtures/` holds files that break each of these on purpose, and
`npm run lint:constraints` asserts that every one is still caught. A guardrail nobody tests is a
guardrail that quietly stops working.

---

## Known gaps in this phase

- **No CI, no Docker, no frontend test runner.** Deferred deliberately — see
  [ADR 0001](.docs/adr/0001-defer-containerisation-and-ci.md). Until CI exists, `./mvnw verify` and
  `npm run verify` are a convention, and the pre-commit hook is the only mechanical gate.
- **Security permits everything.** `SecurityConfig` is a marked placeholder; `SecurityConfigTest`
  is written to fail when Phase 1 lands, which is the reminder to replace it.
- **The initial JS bundle is ~295 KB gzipped**, over the 250 KB budget in
  [doc 07 §10](.docs/07-frontend-architecture.md). It is almost entirely Ant Design, and the budget
  assumes route-level code splitting — of which Phase 0 has one route to split. Recheck once the
  tree and the clan screens exist.
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
