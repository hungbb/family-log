# Phase 0 — Foundation

**Goal:** a repository where both applications build, run, and test locally — with the guardrails that later phases depend on already in place.

**Estimate:** ~1 week · **Depends on:** nothing · **Next:** [Phase 1](phase-1-identity-auth.md)

---

## 1. Scope

**In scope**
- Monorepo layout (`api/`, `web/`, `.docs/`).
- Spring Boot skeleton that starts, connects to a locally installed Postgres, and serves `/actuator/health`.
- Vite + React + TypeScript skeleton that renders an app shell with the theme applied.
- Flyway wired with the first migration.
- Lint/format/pre-commit tooling and the constraint-enforcing ESLint rules.
- ArchUnit rules for the backend layering.

**Out of scope**
- Any business entity, endpoint, or screen beyond a health page.
- Authentication (Phase 1) — the security chain permits everything for now, behind a clearly marked TODO.
- Containerization (Docker) and CI/CD — a deferred workstream, added later.
- Production deployment (Phase 8).

## 2. Repository Layout

```
family-log/
  .docs/                     # these planning documents
  api/                       # Spring Boot application
  web/                       # React application
  .editorconfig  .gitignore  README.md
```

> No `docker/` or `.github/workflows/` in v1 — containerization and CI/CD land later. Postgres is a local install; media is written to a local directory.

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
| F0.3 | `theme/tokens.ts`, `antdTheme.ts`, `styled.d.ts`, `GlobalStyle.ts` | The modern minimalist palette from doc 07 §3 |
| F0.4 | `App.tsx` providers: QueryClient → ConfigProvider → ThemeProvider → I18n → Router | |
| F0.5 | `components/layout/AppShell` with sidebar/topbar placeholders | Pure styled-components over AntD `Layout` |
| F0.6 | `api/client.ts` fetch wrapper skeleton | Base URL, JSON handling, `ApiError` parsing; no auth yet |
| F0.7 | ESLint flat config with the constraint rules (doc 07 §9) | Restricted imports: axios, ky, tailwindcss; no raw `fetch` outside the client; no `.css` imports |
| F0.8 | Prettier, husky, lint-staged | Pre-commit: eslint + prettier + `tsc --noEmit`. No test runner in v1 (no Vitest/Testing Library/MSW/Playwright) |
| F0.9 | i18n scaffold with `vi` default and an `en` stub | |
| F0.10 | Health page hitting `/actuator/health` through the client | Proves the whole wire path |

## 5. Local Setup Tasks

| # | Task | Notes |
|---|------|-------|
| I0.1 | Document local **PostgreSQL 16** setup (native install): create the database and role, connection settings | In the README + `.env.example`; no Docker in v1 |
| I0.2 | `.env.example` for both apps | Documented, secret-free; includes the local media directory path |
| I0.3 | Root `README.md`: how to run both apps in under five minutes | |

> **CI/CD and containerization are deferred.** No GitHub Actions, Docker images, dependency/SAST scanning, or MinIO yet. Keep build/run/test commands simple and documented so wiring a pipeline later is mechanical.

## 6. Migrations

| Version | Contents |
|---------|----------|
| `V1__enable_extensions.sql` | `CREATE EXTENSION IF NOT EXISTS pgcrypto, unaccent, pg_trgm;` plus `f_unaccent(text)` marked `IMMUTABLE` (required for the generated `tsvector` column in Phase 4) |

## 7. Tests

- **Backend**: context loads; `/actuator/health` returns `UP` against a Testcontainers Postgres; Flyway migrates a fresh DB; all ArchUnit rules pass.
- **Frontend (manual + lint)**: the app renders the shell and shows a healthy API badge; ESLint blocks a fixture file importing `axios` and one importing `tailwindcss` (a lint run — there is no test runner in v1).

## 8. Demo Script

Start your locally installed PostgreSQL 16 (see the README), then:

```bash
cd api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

```bash
cd web && npm run dev
```

Open the web app: the shell renders with the modern minimalist theme and a green "API healthy" badge fed by a real request through the fetch client.

## 9. Exit Criteria

- [ ] With a local Postgres running, two commands give a working local environment.
- [ ] Both apps build and lint locally; the backend test suite runs with a single command.
- [ ] Flyway runs from an empty database with no errors.
- [ ] ArchUnit layering rules active and passing.
- [ ] ESLint provably blocks Axios, Tailwind, stray `.css` imports, and raw `fetch`.
- [ ] The theme is applied — the app has a modern, minimalist look, not stock Ant Design.
- [ ] `README.md` gets a new developer running in under five minutes.

## 10. Notes & Risks

- **Get the guardrails in now.** The ESLint constraint rules and ArchUnit layering are cheap on day one and expensive to retrofit; they are the mechanism that keeps the draft's hard constraints true.
- **Don't over-build the skeleton.** No premature abstraction of controllers or services — later phases define those shapes.
- **Media is local in v1.** Uploaded images are written to a local directory behind the `MediaStorage` port; an object-storage (S3-compatible) adapter is a post-v1 swap.
- **CI/CD and containerization are deferred.** Keep build/run/test commands simple and documented so wiring them into a pipeline later is mechanical.
