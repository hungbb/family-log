# Phase 0 — Foundation

**Goal:** a repository where both applications build, run, test, and deploy — with the guardrails that later phases depend on already in place.

**Estimate:** ~1 week · **Depends on:** nothing · **Next:** [Phase 1](phase-1-identity-auth.md)

---

## 1. Scope

**In scope**
- Monorepo layout (`api/`, `web/`, `.docs/`, `docker/`).
- Spring Boot skeleton that starts, connects to Postgres, and serves `/actuator/health`.
- Vite + React + TypeScript skeleton that renders an app shell with the theme applied.
- Docker Compose for Postgres 16 and MinIO.
- Flyway wired with the first migration.
- CI pipeline: build, test, lint for both apps.
- Lint/format/pre-commit tooling and the constraint-enforcing ESLint rules.
- ArchUnit rules for the backend layering.

**Out of scope**
- Any business entity, endpoint, or screen beyond a health page.
- Authentication (Phase 1) — the security chain permits everything for now, behind a clearly marked TODO.
- Production deployment (Phase 8); staging deploy is enough here.

## 2. Repository Layout

```
family-log/
  .docs/                     # these planning documents
  api/                       # Spring Boot application
  web/                       # React application
  docker/
    docker-compose.yml       # postgres, minio
    postgres/init.sql
  .github/workflows/
    api.yml  web.yml
  .editorconfig  .gitignore  README.md
```

## 3. Backend Tasks

| # | Task | Notes |
|---|------|-------|
| B0.1 | `api` Maven project, Java 21, Spring Boot 3.3+ | Dependencies: web, validation, data-jpa, security, flyway, postgresql, actuator, springdoc, mapstruct, micrometer-prometheus |
| B0.2 | Package skeleton per [doc 06 §2](../06-backend-architecture.md) | Empty `domain/application/infrastructure/api` packages per context, each with a `package-info.java` stating its rules |
| B0.3 | `application.yml` + `dev`/`test`/`prod` profiles | Secrets from env; fail fast when missing |
| B0.4 | Flyway `V1__enable_extensions.sql` | `pgcrypto`, `unaccent`, `pg_trgm`, plus the immutable `f_unaccent()` wrapper |
| B0.5 | `common/error` — exception hierarchy + `GlobalExceptionHandler` → RFC 7807 | Doc 05 §2 shape, with `code` and `correlationId` |
| B0.6 | `common/web` — `CorrelationIdFilter`, page envelope, `ETag` support | Correlation id into MDC |
| B0.7 | Logback config: pretty in `dev`, JSON in `prod` | Includes correlation id |
| B0.8 | `SecurityConfig` placeholder: stateless, CSRF off, all permitted | Marked `// TODO(Phase 1)` and asserted by a test that fails once Phase 1 lands |
| B0.9 | springdoc OpenAPI config + `/swagger-ui.html` | |
| B0.10 | Testcontainers base test class `AbstractIntegrationTest` | Reused Postgres container, transactional rollback per test |
| B0.11 | ArchUnit test suite | The six rules in doc 06 §2.1 |
| B0.12 | `Clock` and `IdGenerator` beans | Fixed clock and deterministic ids in `test` profile |

## 4. Frontend Tasks

| # | Task | Notes |
|---|------|-------|
| F0.1 | Vite + React 19 + TypeScript strict scaffold | `strict`, `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes` |
| F0.2 | Install AntD 5, styled-components 6, React Router 7, TanStack Query 5, Zustand, i18next, dayjs, zod | **No Axios. No Tailwind.** |
| F0.3 | `theme/tokens.ts`, `antdTheme.ts`, `styled.d.ts`, `GlobalStyle.ts` | The heritage palette from doc 07 §3 |
| F0.4 | `App.tsx` providers: QueryClient → ConfigProvider → ThemeProvider → I18n → Router | |
| F0.5 | `components/layout/AppShell` with sidebar/topbar placeholders | Pure styled-components over AntD `Layout` |
| F0.6 | `api/client.ts` fetch wrapper skeleton | Base URL, JSON handling, `ApiError` parsing; no auth yet |
| F0.7 | ESLint flat config with the constraint rules (doc 07 §9) | Restricted imports: axios, ky, tailwindcss; no raw `fetch` outside the client; no `.css` imports |
| F0.8 | Prettier, husky, lint-staged | Pre-commit: eslint + prettier + `tsc --noEmit` |
| F0.9 | Vitest + Testing Library + MSW setup, one smoke test | |
| F0.10 | Playwright config + one smoke E2E (`app renders`) | |
| F0.11 | i18n scaffold with `vi` default and an `en` stub | |
| F0.12 | Health page hitting `/actuator/health` through the client | Proves the whole wire path |

## 5. Infrastructure Tasks

| # | Task | Notes |
|---|------|-------|
| I0.1 | `docker/docker-compose.yml` — Postgres 16 + MinIO with volumes | |
| I0.2 | `.env.example` for both apps | Documented, secret-free |
| I0.3 | GitHub Actions `api.yml`: Maven build, unit + Testcontainers tests, ArchUnit, OpenAPI export artifact | Cached `~/.m2` |
| I0.4 | GitHub Actions `web.yml`: install, lint (`--max-warnings=0`), `tsc`, Vitest, build | Cached npm |
| I0.5 | Dependency scanning (Dependabot) + SAST baseline | |
| I0.6 | Root `README.md`: how to run both apps in under five minutes | |

## 6. Migrations

| Version | Contents |
|---------|----------|
| `V1__enable_extensions.sql` | `CREATE EXTENSION IF NOT EXISTS pgcrypto, unaccent, pg_trgm;` plus `f_unaccent(text)` marked `IMMUTABLE` (required for the generated `tsvector` column in Phase 4) |

## 7. Tests

- **Backend**: context loads; `/actuator/health` returns `UP` against a Testcontainers Postgres; Flyway migrates a fresh DB; all ArchUnit rules pass.
- **Frontend**: app renders the shell; the fetch wrapper maps a Problem Details response to `ApiError` (MSW); the ESLint config rejects a fixture file importing `axios` and one importing `tailwindcss`.
- **E2E**: Playwright loads the app and sees the health indicator.

## 8. Demo Script

```bash
docker compose -f docker/docker-compose.yml up -d
```

```bash
cd api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

```bash
cd web && npm run dev
```

Open the web app: the shell renders with the heritage theme and a green "API healthy" badge fed by a real request through the fetch client.

## 9. Exit Criteria

- [ ] `docker compose up` + two commands gives a working local environment.
- [ ] CI green on both applications, running on every PR.
- [ ] Flyway runs from an empty database with no errors.
- [ ] ArchUnit layering rules active and passing.
- [ ] ESLint provably blocks Axios, Tailwind, stray `.css` imports, and raw `fetch`.
- [ ] The theme is applied — the app does not look like stock Ant Design.
- [ ] `README.md` gets a new developer running in under five minutes.

## 10. Notes & Risks

- **Get the guardrails in now.** The ESLint constraint rules and ArchUnit layering are cheap on day one and expensive to retrofit; they are the mechanism that keeps the draft's hard constraints true.
- **Don't over-build the skeleton.** No premature abstraction of controllers or services — later phases define those shapes.
- **MinIO is included early** purely so the S3 adapter written in Phase 7 has a local target from the start.
