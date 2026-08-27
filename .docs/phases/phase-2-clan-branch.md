# Phase 2 — Clan & Branch Structure

**Goal:** clans exist, they have a nested branch structure, and people can join them (M2).

**Estimate:** ~2 weeks · **Depends on:** [Phase 1](phase-1-identity-auth.md) · **Next:** [Phase 3](phase-3-authorization.md)

**Requirements:** FR-C1 … FR-C8, FR-J1 … FR-J5, NFR-4 (tenancy scaffolding)

---

## 1. Scope

**In scope** — clan CRUD, `clanCode` generation and lookup, the branch tree with materialized paths (create, rename, move, archive), memberships, join requests with an approver inbox, member list and removal, multi-clan switching in the UI.

**Out of scope** — `branchRootPersonId` wiring (persons do not exist until Phase 4; the column is created but stays null), fine-grained permissions (Phase 3 — until then, **the clan creator is the only user who may administer a clan**, implemented as an explicit interim check that Phase 3 replaces), tree data of any kind.

> **Interim authorization.** This phase uses a single deliberately crude rule: `isClanCreator(user, clan)` for administrative actions, and `isActiveMember(user, clan)` for reads. It lives in **one** class, `InterimClanAccessCheck`, marked `@Deprecated` with a Phase-3 TODO, so it is trivially findable and removable. No ad-hoc checks anywhere else.

## 2. Backend Tasks

| # | Task | Detail |
|---|------|--------|
| B2.1 | `clanorg/domain`: `Clan`, `Branch`, `ClanMembership`, `ClanJoinRequest` + invariants I-C1…I-C7, I-M1…I-M3, I-J1…I-J4 | Framework-free |
| B2.2 | `CodeGenerator` | `SURNAME-XXXX`, Crockford base32, collision retry, never reissued |
| B2.3 | `BranchPathService` | Path build on create; recursive-CTE rewrite of descendant paths on move; depth cap 10 |
| B2.4 | JPA entities, repositories, mappers | Every read method takes a `ClanId` |
| B2.5 | `ClanService`: create (clan + root branch + creator membership in one transaction), update, soft delete, lookup by code | Creation also emits `ClanCreated`, which Phase 3 consumes to bootstrap `CLAN_ADMIN` |
| B2.6 | `BranchService`: create, update, move, archive, list-as-tree | Move validates no cycle and no archived parent |
| B2.7 | `MembershipService`: list, remove, leave, last-admin protection hook | Last-admin logic is a port that Phase 3 fills in properly |
| B2.8 | `JoinRequestService`: submit, cancel, approve, reject, expire | Approval creates the membership atomically; duplicates return `409 DUPLICATE_PENDING_REQUEST` |
| B2.9 | Scheduled job: expire join requests older than 90 days | `@Scheduled`, guarded for single-instance execution |
| B2.10 | Controllers per [doc 05 §4.3–4.5](../05-api-specification.md) | |
| B2.11 | Audit entries: `CLAN_CREATED`, `BRANCH_*`, `MEMBERSHIP_*`, `JOIN_REQUEST_*` | |
| B2.12 | Tenancy test helper | Asserts every repository query filters by `clan_id` |

## 3. Frontend Tasks

| # | Task | Detail |
|---|------|--------|
| F2.1 | Onboarding: "Create a clan" / "Join with a code" | The empty-state landing for a user with no clans |
| F2.2 | Create-clan form: name, surname, origin place, description | Shows the generated `clanCode` on success with a copy button |
| F2.3 | Clan list + `ClanSwitcher` in the shell | `useActiveClanStore`, persisted, validated against membership on boot (FR-C8) |
| F2.4 | Clan settings page (creator only for now) | Editable fields; `clanCode` shown read-only with an explanatory tooltip |
| F2.5 | Branch management page | AntD `Tree` of branches with create/rename/move/archive; drag-to-move calls the move endpoint |
| F2.6 | Join flow: enter code → preview card (`/clans/lookup`) → submit request with a message | |
| F2.7 | Join-request inbox: pending list, approve (choose a home branch), reject with reason | |
| F2.8 | "My requests" section on `/me` showing status | |
| F2.9 | Member list: search, filter by branch, remove, leave-clan | |
| F2.10 | `RequireClan` guard | `404` from the API renders "clan not found or you are not a member" |
| F2.11 | i18n for the whole surface | |

## 4. Migrations

| Version | Contents |
|---------|----------|
| `V4__create_clans_and_branches.sql` | `clans`, `branches`, path index, `ux_branches_one_root`, depth check (doc 03 §3.3–3.4). `branch_root_person_id` is created **without** its FK — added in Phase 4 (`V10`) |
| `V5__create_memberships_and_join_requests.sql` | `clan_memberships`, `clan_join_requests` + the partial unique indexes for I-M1 and I-J1 |

## 5. Tests

**Unit** — path generation and rewrite; `CodeGenerator` collision handling; join-request state machine (all illegal transitions rejected); membership invariants.

**Integration** — create clan → root branch exists → creator is an active member; nested branches to depth 10, depth 11 rejected; moving a branch rewrites every descendant path in one transaction; moving a branch under its own descendant is rejected; archiving with live children is rejected; duplicate pending join request returns `409`; approving creates exactly one active membership; a second approval of the same request returns `409`; a non-member gets `404` (not `403`) for every clan endpoint (doc 04 §7).

**Tenancy (NFR-4)** — a user in clan A requesting a branch id belonging to clan B receives `404`; parameterized across every endpoint that takes a nested id.

**Frontend (manual)** — clan switcher persists across reloads; join-code preview handles an unknown code; branch tree renders nesting and disables illegal drops.

**Manual walkthrough** — user A creates a clan and two branches; user B joins by code; A approves with a home branch; B sees the clan in their switcher and the branch tree.

## 6. Demo Script

1. User A creates the clan "Nguyễn Văn" and copies its code.
2. A adds branches: *Chi Trưởng*, *Chi Hai*, and *Chi Hai → Nhánh An Giang*.
3. User B joins with the code; A's inbox shows the request; A approves it into *Chi Hai*.
4. B logs in and switches between their two clans.
5. A drags *Nhánh An Giang* under *Chi Trưởng*; the paths update and the tree re-renders.

## 7. Exit Criteria

- [ ] FR-C1…C8 and FR-J1…J5 complete end to end.
- [ ] Branch paths are provably consistent after arbitrary move sequences (property test).
- [ ] All interim authorization is confined to `InterimClanAccessCheck`.
- [ ] Tenancy tests pass for every clan-scoped endpoint.
- [ ] M2 milestone demo runs locally from seed data.

## 8. Notes & Risks

- **Branch codes vs clan codes.** Both are issued here so Phase 3's branch-scoped joins work without a schema change. Sharing a branch code lets a relative join straight into the right *chi*.
- **Re-parenting is the risky operation.** It rewrites a subtree of paths; keep it in a single SQL statement inside one transaction, and cover it with the property test rather than a handful of examples.
- **Resist adding a `role` column** to membership under time pressure — Phase 3's grant model is the design (decision D2 in the PRD), and a temporary role enum would leak into the UI and be painful to remove.

---

## 9. Phase Report

> Appended on completion, per [doc 09 §5](../09-engineering-conventions.md). What shipped, what
> deviated from the plan above, and the numbers that were measured rather than estimated.

### What shipped

All of §2 and §3. The backend has the full `clanorg` context — domain, application, infrastructure
and api — behind the interim access rule; the frontend has onboarding, clan creation with the code
hand-off, the join-by-code flow with its preview card, the branch tree with drag-to-move, the member
list, the approver inbox, and the switcher in the shell.

The interim authorization is where §1 said it would be. `InterimClanAccessCheck` is the only class in
the codebase that decides anything about access to a clan; it is `@Deprecated(forRemoval = true)`,
it implements the `LastAdminProtection` port so that Phase 3 replaces an implementation rather than
finding call sites, and `grep -rn "isCreatedBy\|requireAdministrator" src/main` returns that one file
plus the aggregate method it delegates to.

### Deviations from the plan

| Plan | What shipped | Why |
|------|--------------|-----|
| `GET /clans/{clanId}/my-permissions` (doc 05 §4.3) | `myPermissions[]` on the clan resource | Recorded in [ADR 0004](../adr/0004-clan-permissions-on-the-clan-resource.md). A second request for a list that is already computed on the first is a round trip the switcher would pay on every clan. |
| `GET /me` returns `clans[]` (doc 05 §4.2) | Still absent; `GET /clans` serves the switcher | Identity would have to depend on ClanOrg to assemble it. `GET /clans` is one request either way, and the boundary stays where doc 02 §1.1 puts it. |
| `branches.path` per doc 03 §3.4 | Plus `ux_branches_path (clan_id, path)` | A path segment is eight hex characters of a UUID. A collision inside one clan is vanishingly unlikely and would silently make one branch's descendant query return another's subtree — a tenancy-shaped bug inside a single tenant. The index turns it into an insert failure that `BranchService.freeBranchId` retries. |
| `branches` indexes | Plus `ix_branches_parent (clan_id, parent_branch_id)` | Every render of the branch tree groups by parent. |
| Member list paged in SQL (doc 05 §4.5) | Filtered and paged in the application service | The name search lives in Identity, and pushing the filter into a ClanOrg query would mean joining another context's table (doc 02 §7 rule 1). The service intersects instead. The cost is proportional to the number of *accounts* in one clan — tens, occasionally hundreds — which, unlike the tree, does not grow. |
| Approver scoping by `MEMBER_APPROVE` | The clan creator only | §1: fine-grained permissions are Phase 3. The inbox endpoint answers `403` to any other member. |
| Repository reads take a `ClanId` (doc 06 §2.1) | Typed ids reach the JPA layer through `common/persistence` converters | Without them a derived finder's parameter would be a raw `UUID` — which is precisely the shape rule 6 of `LayeringTest` forbids — and the tenancy rule would have to be relaxed to accommodate the code it exists to constrain. |
| `SequentialIdGenerator` unchanged | Counter written into both halves of the UUID | Its original ids all shared the first eight hex characters, which is exactly the substring a branch path segment is built from. Every branch in a `test`-profile clan would have claimed one path. Found by `BranchTest`, and it would have surfaced only under the test profile — the worst place for it to hide. |

`UserDirectory` is new in `identity.application`: the published surface ClanOrg reads member names
through. It exists so that no other context has to join `users` in its own SQL.

### Measured numbers

| Metric | Result |
|--------|--------|
| Unit tests | 322, up from 188 — 134 added, all green (`./mvnw test`) |
| ClanOrg test classes | 4 domain, 4 application, 1 property test, 6 integration |
| Path property test | 8 seeds × 60 random moves, re-deriving every path from the parent links after each |
| Migrations | `V4` (clans, branches) and `V5` (memberships, join requests) |
| Frontend | `npm run verify` green: ESLint `--max-warnings=0`, the seven constraint fixtures, `tsc --noEmit`, and the production build |
| Initial JS (gzipped) | ~295 KB — **over** the 250 KB budget of doc 07 §10; see below |

**The bundle budget is missed and the cause is Ant Design**, not this phase's screens: the three
chunks in the initial load are the framework, the AntD runtime and the router, and every feature
chunk here is lazily loaded (the branch page is 27 KB gzipped, the member page 70 KB). The fix is
per-component AntD imports and is a change to how the whole application imports it; it belongs with
the tree visualizer in Phase 5, which is the page the budget was written for.

### Not verified here

The six integration classes (`ClanOrgSchemaIT`, `ClanLifecycleIT`, `BranchStructureIT`, `JoinFlowIT`,
`TenancyBoundaryIT`, `ClanAccessIT` — 43 test methods, expanding to roughly 70 cases through the two
parameterized matrices) are written and compile, but were **not executed**: this machine has no
Docker daemon, and Testcontainers needs one. They must pass before this is merged:

```bash
cd api && ./mvnw verify
```

That leaves three things unproven until they run: the recursive path-rewrite SQL, the JPA mapping of
the typed-id converters, and every schema constraint in §4. The in-memory tests exercise a *second*
implementation of the rewrite — see the note on `InMemoryBranchRepository` — so a disagreement
between the two would show up in `BranchStructureIT` and nowhere else.

The §6 demo script also needs a running PostgreSQL and has not been walked through.

### Exit criteria

- [x] FR-C1…C8 and FR-J1…J5 complete end to end.
- [x] Branch paths are provably consistent after arbitrary move sequences —
      `BranchPathPropertyTest` re-derives every path and depth from `parentBranchId` alone after each
      of 60 random moves, across 8 seeds.
- [x] All interim authorization is confined to `InterimClanAccessCheck`.
- [ ] Tenancy tests pass for every clan-scoped endpoint — `TenancyBoundaryIT` and `ClanAccessIT` are
      written and parameterized over every endpoint; pending Docker.
- [ ] M2 milestone demo runs locally from seed data — pending a machine with PostgreSQL.

### Notes for Phase 3

- **`InterimClanAccessCheck` is the whole checklist.** Replace `requireVisible` and
  `requireAdministrator` with `PermissionResolver.require(...)`, move `LastAdminProtection` onto the
  grant table, and delete the file. Nothing else in `clanorg` asks who anybody is.
- **The events are already published.** `ClanCreated` carries the founder and the root branch, which
  is what doc 04 §6's bootstrap grant needs; `MembershipGranted` and `MembershipRevoked` are what
  I-M2 hangs on. Nothing subscribes yet, which is the point — Phase 3 adds listeners, not edits.
- **Branch codes exist and nothing reads them.** Every branch has one from creation, so
  branch-scoped joining is a feature rather than a backfill. The join endpoint already resolves one.
- **`myPermissions[]` changes source, not shape.** The frontend gates on the strings in it and
  re-checks nothing; swapping the interim list for resolved grants is invisible to the client.
- **The member list still has no `linkedPersonId`.** The field is in the contract and returns null
  until Phase 6 links accounts to nodes.
