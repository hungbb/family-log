# Phase 3 — Authorization Engine

**Goal:** the product's differentiator — multiple users holding scoped permissions, with enforcement that provably isolates branches (M3).

**Estimate:** ~2 weeks · **Depends on:** [Phase 2](phase-2-clan-branch.md) · **Next:** [Phase 4](phase-4-lineage-core.md)

**Requirements:** FR-Z1 … FR-Z8, FR-X1, goals G4 and G5

> Read [04-authorization-model.md](../04-authorization-model.md) in full before starting. This phase implements that document.

---

## 1. Scope

**In scope** — the `PermissionGrant` aggregate; `PermissionResolver` with `CLAN` and `BRANCH` scopes; enforcement points replacing all interim checks; the grant-management API and UI; escalation prevention; last-admin protection; audit of every authorization decision on mutation paths; `my-permissions` for UI gating.

**Out of scope** — `SUBTREE` scope *activation* (the resolver understands it, but granting it returns `422 SUBTREE_SCOPE_UNAVAILABLE` until Phase 4 creates `person_ancestry`); read-level filtering (doc 04 §5.5 — `CLAN_VIEW` is clan-wide for reads in v1); named role bundles.

## 2. Backend Tasks

| # | Task | Detail |
|---|------|--------|
| B3.1 | `authorization/domain`: `Permission` enum, `PermissionScope`, `PermissionGrant`, `AccessDecision`, `Target` sealed hierarchy | `Target` = `ClanTarget \| BranchTarget \| PersonTarget \| RelationshipTarget` |
| B3.2 | Ports: `MembershipPort`, `GrantPort`, `BranchHierarchyPort`, `LineageAncestryPort` | `LineageAncestryPort` gets a stub implementation that throws until Phase 4 |
| B3.3 | **`PermissionResolver`** — `decide`, `require`, `editableSet` | Pure domain, no Spring, no DB. The single decision point |
| B3.4 | Implication expansion | `CLAN_ADMIN@CLAN` → all; any permission → `CLAN_VIEW` at the same scope |
| B3.5 | Scope-covering relation (`covers(S1, S2)`) | Doc 04 §5.4; used by both matching and escalation checks |
| B3.6 | Request-scoped grant cache | Keyed by (userId, clanId); one query per request regardless of how many checks run |
| B3.7 | `GrantService`: grant, revoke, list, `grantable` | Enforces the four escalation conditions (doc 04 §5.4) and last-admin protection |
| B3.8 | `@RequiresPermission` annotation + AOP aspect | Coarse pre-check from path variables; the service check remains authoritative |
| B3.9 | Replace `InterimClanAccessCheck` everywhere; delete the class | Clan/branch/membership/join endpoints now use real permissions |
| B3.10 | `ClanCreated` listener → bootstrap `CLAN_ADMIN@CLAN` for the creator | Flagged as a bootstrap grant in the audit log |
| B3.11 | Join approval grants `CLAN_VIEW` at the requested scope | |
| B3.12 | Membership revocation deactivates that user's grants in the clan | Same transaction |
| B3.13 | Audit: `PERMISSION_GRANTED`, `PERMISSION_REVOKED`, and `authorizing_grant_id` on every mutation entry | FR-Z8 |
| B3.14 | Nightly job marking expired grants revoked | Hygiene only — queries already filter on `expires_at` |
| B3.15 | ArchUnit rule: every mutating application-service method calls `require(...)` or is on a justified allow-list | The structural guarantee against forgotten checks |

## 3. Frontend Tasks

| # | Task | Detail |
|---|------|--------|
| F3.1 | `usePermissions(clanId)` hook backed by `/clans/{id}/my-permissions` | 5-min stale time; refetched on clan switch and after grant changes |
| F3.2 | `<PermissionGate permission="…" scopeRef="…">` component | Hides or disables affordances; documented as convenience, not security |
| F3.3 | Permissions page: grant table (user × permission × scope), filters, revoke | |
| F3.4 | Grant dialog built from `/permissions/grantable` | The user can only pick what they may actually grant — escalation is impossible to express in the UI, and still rejected server-side |
| F3.5 | Member detail drawer showing that member's grants, with a plain-language summary ("Can edit *Chi Hai* and everything under it") | |
| F3.6 | Scope picker component | Clan-wide vs a branch from the branch tree; the subtree option is present but disabled with "available once the tree exists" |
| F3.7 | 403 handling | `PERMISSION_DENIED` / `OUT_OF_SCOPE` render an explanatory message naming what the user *does* hold, not a bare error toast |
| F3.8 | i18n for every permission name and description | Vietnamese wording matters here — these are the words families will argue about |

## 4. Migrations

| Version | Contents |
|---------|----------|
| `V6__create_permission_grants.sql` | `permission_grants` with the `CHECK` constraints for I-G1/I-G2 and the two indexes from doc 03 §3.11 |
| `V12b__alter_audit_add_grant.sql` | `audit_entries.authorizing_grant_id` |

## 5. Tests

This phase carries the strictest test bar in the project: **100 % branch coverage on `PermissionResolver`**.

**Unit (no DB, stub ports)** — the full (permission × scopeType × targetType) matrix, allow and deny; implication expansion; expired / revoked / membership-less grants ineffective; overlapping grants union correctly; unclassifiable target → deny.

**Integration — the isolation matrix** (doc 04 §8), using a fixture clan with an đời-13 ancestor and branches C and D. Every row is an explicit test.

**Escalation tests** — a `ROLE_MANAGE@Branch C` holder attempting to grant: `CLAN_ADMIN` → 403; `TREE_EDIT@CLAN` → 403; `TREE_EDIT@Branch D` → 403; `TREE_EDIT@Branch C` → 200; `MEMBER_APPROVE@Branch C` while not holding it themselves → 403.

**Last-admin protection** — revoking the only `CLAN_ADMIN`, removing that member, and self-demotion all return `403 LAST_ADMIN_PROTECTED`.

**Property test** — for randomly generated branch trees and grant sets, a user may act on a node **iff** some grant's scope contains it, checked against an independent reference implementation.

**Structural** — the ArchUnit rule fails when a new mutating service method omits `require(...)` (verified with a deliberately broken fixture).

**Frontend (manual)** — the grant dialog offers no option the caller cannot grant; `PermissionGate` hides controls; a 403 renders the explanatory message.

## 6. Demo Script

1. Admin opens the Permissions page and grants user C `TREE_EDIT` scoped to *Chi Hai*, and `ROLE_MANAGE` on the same branch.
2. C logs in: the member drawer summarizes "Can edit *Chi Hai* and everything under it".
3. C grants `MEMBER_APPROVE@Chi Hai` to user E — allowed. C then tries a clan-wide grant — the option is absent in the UI, and a direct API call returns `403 PERMISSION_ESCALATION_BLOCKED`.
4. Admin revokes their own `CLAN_ADMIN` — refused, last admin protected.
5. The audit page (API, UI in Phase 8) shows each decision with its authorizing grant.

## 7. Exit Criteria

- [ ] FR-Z1…Z8 implemented, `SUBTREE` deferred behind a documented `422`.
- [ ] `PermissionResolver` at 100 % branch coverage.
- [ ] The isolation matrix passes in full at the API layer.
- [ ] `InterimClanAccessCheck` deleted; no ad-hoc checks remain (grep-verified in review).
- [ ] Escalation and last-admin protections proven by test.
- [ ] Every mutation writes an audit entry carrying its authorizing grant.
- [ ] M3 milestone demo runs locally from seed data.

## 8. Notes & Risks

- **R1 from the roadmap lives here.** A leak in scope resolution breaks the product's core promise. Prefer duplicated tests over clever abstractions in this phase.
- **Resist convenience helpers.** Any `if (isAdmin(...))` written outside the resolver becomes a second, divergent policy. The ArchUnit rule exists to make that impossible to merge quietly.
- **Explain decisions in the UI.** Families argue about who may change what; a `403` that names the grant the user *does* hold prevents most support conversations.
- **`SUBTREE` deferral is intentional** — shipping it against a missing closure table would mean a half-tested code path in the highest-risk component. Phase 4 turns it on with its own tests.
