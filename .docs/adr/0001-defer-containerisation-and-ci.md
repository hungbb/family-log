# ADR 0001 — Defer containerisation, CI/CD and the frontend test runner out of Phase 0

- **Status:** Accepted
- **Date:** 2026-08-25
- **Deciders:** hungbb
- **Affects:** [phases/phase-0-foundation.md](../phases/phase-0-foundation.md) §1, §2, §5, §7, §8; [06-backend-architecture.md](../06-backend-architecture.md) §8; [09-engineering-conventions.md](../09-engineering-conventions.md) §1, §4.3

## Context

`phase-0-foundation.md` as written puts five things in scope that the Phase 0 issue
([#2](https://github.com/hungbb/family-log/issues/2)) removes:

- `docker/docker-compose.yml` with PostgreSQL 16 and MinIO;
- `.github/workflows/api.yml` and `web.yml`;
- Dependabot and a SAST baseline;
- Vitest, Testing Library and MSW;
- Playwright.

The issue narrows the phase to "a repository where both applications build, run and test locally,
with the guardrails that later phases depend on". PostgreSQL is a native local install, media is a
local directory, and the exit criterion becomes "two commands give a working local environment"
rather than "`docker compose up` + two commands, CI green on both applications".

The reasoning holds up. Containerisation and a pipeline are real work that produce nothing a
developer can use on day one, and they are mechanical to add later *provided* the build, run and
test commands stay simple and documented — which is a constraint on this phase, not a reason to do
the work now. MinIO in particular exists only so the Phase 7 S3 adapter has a local target; there is
no adapter yet.

The frontend test runner is a closer call, and the honest reading is that Phase 0 has almost nothing
to test: one page, one fetch wrapper, no business logic. A test suite whose only subject is a health
badge mostly tests the framework.

## Decision

Phase 0 ships without Docker, without GitHub Actions, without dependency/SAST scanning, and without
a frontend test runner. PostgreSQL 16 is installed natively and documented in the README; uploaded
media goes to a local directory behind the `MediaStorage` port.

Backend testing is unaffected: JUnit, Testcontainers and ArchUnit are all present and running.

## Alternatives Considered

| Option | Pros | Cons | Why not |
|--------|------|------|---------|
| Follow the phase doc as written | Matches the plan; CI green from commit one | Roughly doubles Phase 0; MinIO and the E2E harness serve code that does not exist | The value arrives in Phase 7 and 8, the cost arrives now |
| Docker Compose but no CI | One-command database, no pipeline work | Docker Desktop is a licensing and performance question on the team's machines; a native Postgres is one install | Not enough benefit over `initdb` to justify the dependency in v1 |
| Keep Vitest, drop Playwright | Some frontend safety net | The units worth testing (`buildGraph`, date and name formatting, permission gates) all arrive in Phases 4–5 | Adding the runner alongside its first real subject costs the same and skips the empty-suite phase |

## Consequences

**Positive** — Phase 0 is a day's work rather than a week's, and every command a developer needs is
a plain `./mvnw` or `npm run`. The guardrails that are genuinely expensive to retrofit — the ArchUnit
layering rules and the ESLint constraint rules — are all in place, which is what the phase was
actually for.

**Negative** — nothing verifies a pull request automatically. Until CI exists, `./mvnw verify` and
`npm run verify` are a convention rather than a gate, and the husky pre-commit hook is the only
mechanical check. There is no frontend regression safety net at all: the constraint fixtures prove
ESLint still blocks Axios and Tailwind, and nothing else is asserted.

**Follow-up**

- Testcontainers integration tests (`*IT`) need Docker and are bound to Maven's `verify` phase, so
  `./mvnw test` still runs on a machine without a daemon. Whoever adds CI must run `verify`, not
  `test`, or the integration tests silently never execute.
- The deferred workstream, when it lands: `docker/docker-compose.yml` (Postgres + MinIO),
  `.github/workflows/{api,web}.yml`, Dependabot, SAST, then Vitest + Testing Library + MSW, then
  Playwright.
- `phase-0-foundation.md` §1, §2, §5, §7 and §8 and `09-engineering-conventions.md` §1 still describe
  `docker/` and `.github/workflows/` in the repository layout. They are the plan of record for the
  deferred work; this ADR is the reason the directories are absent today.
