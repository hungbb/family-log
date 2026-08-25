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
