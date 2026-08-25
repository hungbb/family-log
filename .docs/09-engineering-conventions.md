# 09 — Engineering Conventions & Testing Strategy

> How we write, review, test, and ship. Applies to every phase.

---

## 1. Repository & Workflow

**Layout**
```
family-log/
  .docs/            planning docs (this folder) + .docs/adr/
  api/              Spring Boot application
  web/              React application
  docker/           local infrastructure
  .github/workflows/
```

**Branching** — trunk-based. Short-lived branches off `main`: `feat/<phase>-<slug>`, `fix/<slug>`, `chore/<slug>`. Squash merge. `main` is always deployable.

**Commits** — Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`), with a scope where useful: `feat(authz): add subtree scope resolution`.

**Pull requests**
- Under ~400 changed lines where possible; split larger work.
- Description states *what* changed and *why*, and names the requirement id (e.g. FR-Z4).
- One migration per PR, never edited after merge.
- **Two reviewers required for anything touching `authorization/`**; one otherwise.
- CI green is a merge precondition, not a suggestion.

**Definition of done** — the seven-point list in [08-implementation-roadmap.md](08-implementation-roadmap.md) §4.

## 2. Backend Conventions

### 2.1 Naming

| Kind | Convention | Example |
|------|-----------|---------|
| Application service | `<Aggregate>Service` | `PersonService` |
| Domain service | Describes behaviour | `LineageGraphService`, `PermissionResolver` |
| Controller | `<Resource>Controller` | `ClaimController` |
| Request DTO | `<Verb><Noun>Request` | `CreatePersonRequest` |
| Response DTO | `<Noun>Response` / `<Noun>Summary` / `<Noun>Detail` | `PersonDetail` |
| JPA entity | `<Noun>JpaEntity` | `PersonJpaEntity` |
| Mapper | `<Noun>Mapper` (MapStruct) | `PersonMapper` |
| Port | `<Capability>Port` | `LineageAncestryPort` |
| Test | `<Class>Test` (unit) / `<Feature>IT` (integration) | `PermissionResolverTest`, `BranchIsolationIT` |

### 2.2 Code rules

- **Records for DTOs and value objects.** Immutable by default; mutable state only inside aggregates that need it.
- **Constructor injection only.** No field injection, no `@Autowired` on fields.
- **No `Optional` parameters**; `Optional` is a return type only.
- **Null discipline** — nullable returns are `Optional`; nullable fields are documented. Package-level `@NullMarked` where practical.
- **No business logic in controllers.** Bind, validate, delegate, map.
- **`@Transactional` only on application services**, never on controllers or repositories.
- **Exceptions carry a `code`** matching the API error catalogue (doc 05 §2).
- **Every clan-scoped repository method takes a `ClanId`.** Exceptions need `@TenantExempt` and a justification comment.
- **No `System.out`**; SLF4J with structured key-value context.
- **Comments explain why, not what.** Invariant enforcement points reference their invariant id (`// I-P4`).

### 2.3 SQL & migrations

- Native SQL for tree, closure, and authorization reads; JPA for aggregate CRUD.
- Every migration is tested against a fresh database **and** against the previous release's database in CI.
- Destructive changes ship in two releases: add-and-backfill, then drop.
- No `Flyway.clean` outside `dev`; disabled by configuration in `prod`.

## 3. Frontend Conventions

### 3.1 Naming

| Kind | Convention | Example |
|------|-----------|---------|
| Component file | `PascalCase.tsx` | `PersonNode.tsx` |
| Hook | `use<Thing>.ts` | `useTreeLayout.ts` |
| Endpoint module | `camelCase.ts` under `api/endpoints/` | `permissions.ts` |
| Styled component | `Styled`-free noun, exported from `styles.ts` | `NodeCard`, `RailLabel` |
| Store | `use<Domain>Store.ts` | `useActiveClanStore.ts` |
| Test | `<file>.test.tsx` colocated | `PersonNode.test.tsx` |

### 3.2 Code rules

- **Function components + hooks only.**
- **Props are typed interfaces**, never `any`; no `React.FC` (implicit children).
- **Server state in TanStack Query, client state in Zustand.** Never duplicate server data into a store.
- **All HTTP through `api/endpoints/*`**, which call the wrapper in `api/client.ts`. Components never call `request()` or `fetch` directly.
- **All styling in styled-components** (doc 07 §3.2). The single inline-style exception is `TreeCanvas`.
- **No hard-coded user-facing strings** — i18n keys only.
- **No hard-coded colours or spacing** — theme tokens only.
- **Feature isolation** — cross-feature imports go through a feature's `index.ts`.
- **Error boundaries** at the route level; a failed tree render never blanks the whole app.

### 3.3 Enforced by tooling

ESLint carries the constraint rules from doc 07 §9 (no Axios, no Tailwind, no stray `.css`, no raw `fetch`, no `style` prop, i18n literals). CI runs `eslint --max-warnings=0`, `prettier --check`, and `tsc --noEmit`. A pre-commit hook runs all three on staged files.

## 4. Testing Strategy

### 4.1 Shape of the suite

```
        ╱ E2E (Playwright) ╲          ~15 specs — the PRD acceptance scenarios
      ╱  Integration (IT)   ╲         ~120 tests — real Postgres via Testcontainers
    ╱   Component (RTL)      ╲        ~80 tests — UI behaviour with MSW
  ╱     Unit (JUnit/Vitest)   ╲       ~400 tests — domain logic, pure functions
```

### 4.2 Backend layers

| Layer | Tool | Scope | Rule |
|-------|------|-------|------|
| Domain unit | JUnit 5 + AssertJ | Aggregates, value objects, `PermissionResolver` | No Spring, no DB, no mocks of things we own beyond ports |
| Application | JUnit + Mockito | Orchestration, transaction boundaries | Ports stubbed |
| Integration (`*IT`) | Testcontainers Postgres | Controller → DB, real HTTP via MockMvc/RestAssured | Fresh schema per class, rollback per test |
| Architecture | ArchUnit | Layering, tenancy, `require(...)` coverage | Runs in the normal test task |
| Performance | JMH / custom harness | Tree read, closure maintenance | Budgets asserted in CI against the 5,000-node fixture |

### 4.3 Frontend layers

| Layer | Tool | Scope |
|-------|------|-------|
| Unit | Vitest | Pure functions: `buildGraph`, date formatting, name formatting |
| Component | Testing Library + MSW | Forms, guards, permission gates, tree interactions |
| E2E | Playwright | Full user journeys against a running stack with seeded data |
| Accessibility | axe + Playwright | Tree page, list view, all forms |
| Visual (optional) | Playwright screenshots | Tree layout regressions on fixture trees |

### 4.4 Coverage gates

| Target | Gate |
|--------|------|
| `*.domain`, `*.application` (backend) | ≥ 80 % line coverage |
| `PermissionResolver` | **100 % branch coverage** |
| `AncestryProjection` | ≥ 95 % line coverage + the property test |
| Frontend `features/*/model`, `lib/` | ≥ 75 % |
| Everything else | No gate; reviewed by judgement |

Coverage is a floor, not a goal. A PR that raises coverage while testing nothing meaningful gets rejected.

### 4.5 Mandatory test classes

These exist from the phase that introduces them and are never deleted:

1. **`BranchIsolationIT`** — the isolation matrix (doc 04 §8). The single most important test in the codebase.
2. **`TenancyBoundaryIT`** — cross-clan id substitution on every nested endpoint, parameterized.
3. **`PermissionEscalationIT`** — every escalation path from doc 04 §5.4.
4. **`AncestryProjectionPropertyTest`** — randomized edit sequences vs a reference closure.
5. **`ApiContractTest`** — the exported OpenAPI spec matches the committed frontend types.
6. **`SensitiveFieldExposureTest`** — no DTO serializes a password hash, token, or non-member-visible field.

### 4.6 Test data

- **Fixtures over factories with defaults** — a test's setup should make its preconditions obvious.
- One shared `ClanFixture` builds the canonical demo clan (đời-13 ancestor, branches C and D, one admin, two branch editors, two plain members) used across authorization tests.
- `LargeTreeGenerator` produces the 5,000-person clan for performance work.
- Fixed `Clock` and deterministic `IdGenerator` in the `test` profile — no flaky time or ordering.

### 4.7 Bug policy

Every bug gets a failing test at the layer where it escaped, **before** the fix. Authorization bugs additionally get a row in the isolation matrix.

## 5. Documentation Practice

- `.docs/` is the design source of truth and is updated in the PR that changes the design — not afterwards.
- Deviations from these documents are recorded as ADRs in `.docs/adr/NNNN-short-title.md` using the template in that folder.
- OpenAPI is generated from code, exported in CI, and diffed; breaking changes fail the build.
- Each phase ends with a short report appended to its phase doc: what shipped, what deviated, measured numbers.
- `README.md` at the repo root stays current on how to run everything — verified by a new-developer walkthrough at each milestone.

## 6. Definition of "Deployable"

`main` is deployable when: CI is green; migrations apply cleanly forward from the previous release; no feature is half-enabled without a flag; and the health endpoint reports `UP` against a real database. Anything that cannot meet this bar goes behind a feature flag rather than a long-lived branch.
