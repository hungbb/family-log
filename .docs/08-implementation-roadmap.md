# 08 — Implementation Roadmap

> Nine phases from empty repository to release candidate. Each phase is independently demoable and leaves `main` deployable.

---

## 1. Phase Overview

| # | Phase | Focus | Est. | Depends on | Detail |
|---|-------|-------|------|-----------|--------|
| 0 | **Foundation** | Repo layout, build, Docker, CI, skeletons | 1 wk | — | [phase-0](phases/phase-0-foundation.md) |
| 1 | **Identity & Auth** | Register, login, JWT, sessions, profile | 1.5 wk | 0 | [phase-1](phases/phase-1-identity-auth.md) |
| 2 | **Clan & Branch** | Clans, branch tree, membership, join requests | 2 wk | 1 | [phase-2](phases/phase-2-clan-branch.md) |
| 3 | **Authorization** | Grants, scoped resolution, enforcement, grant UI | 2 wk | 2 | [phase-3](phases/phase-3-authorization.md) |
| 4 | **Lineage Core** | Persons, parentage, unions, ancestry closure | 2.5 wk | 3 | [phase-4](phases/phase-4-lineage-core.md) |
| 5 | **Tree Visualizer** | Master view, layout, pan/zoom, editing UX | 2.5 wk | 4 | [phase-5](phases/phase-5-tree-visualizer.md) |
| 6 | **Claiming & Linking** | Node claims, approvals, direct assignment | 1.5 wk | 5 | [phase-6](phases/phase-6-claiming-linking.md) |
| 7 | **Media & Content** | Uploads, rich notes, notifications, i18n | 1.5 wk | 6 | [phase-7](phases/phase-7-media-content.md) |
| 8 | **Hardening & Release** | Security, performance, audit UI, ops, launch | 2 wk | 7 | [phase-8](phases/phase-8-hardening-release.md) |

**Total: ~16.5 weeks** for a team of 1–2 full-stack engineers. Phases 4–5 are the critical path and hold the most estimation risk.

## 2. Dependency Graph

```
Phase 0  Foundation
    │
Phase 1  Identity & Auth
    │
Phase 2  Clan & Branch ──────────────┐
    │                                │
Phase 3  Authorization               │  (branch paths feed scope resolution)
    │                                │
Phase 4  Lineage Core ◄──────────────┘
    │        │
    │        └── SUBTREE scopes activate here (Phase 3 ships CLAN + BRANCH only)
    │
Phase 5  Tree Visualizer
    │
Phase 6  Claiming & Linking
    │
Phase 7  Media & Content
    │
Phase 8  Hardening & Release
```

**Deliberate ordering note.** Authorization (3) lands *before* the tree (4) so that no lineage endpoint is ever written without an enforcement point — retrofitting authorization onto existing CRUD is how isolation bugs are born. `SUBTREE` scope needs `person_ancestry`, which arrives with Phase 4; Phase 3 therefore ships `CLAN` and `BRANCH` scopes and a resolver that already understands `SUBTREE` but rejects grants of that type until Phase 4 enables it behind a feature flag.

## 3. Requirement Coverage Map

| Phase | Requirements delivered |
|-------|----------------------|
| 0 | NFR-6 (baseline), engineering conventions |
| 1 | FR-A1…A6, NFR-3 (auth portion) |
| 2 | FR-C1…C8, FR-J1…J5, NFR-4 (tenancy scaffolding) |
| 3 | FR-Z1…Z8, FR-X1 (audit for authz), G4, G5 |
| 4 | FR-P1…P11, FR-T1, FR-T5, FR-T6, G1 |
| 5 | FR-T2, FR-T3, FR-T4, G6, NFR-1, NFR-7 |
| 6 | FR-N1…N6, G7 |
| 7 | FR-M1…M3, FR-X2, NFR-8 |
| 8 | FR-X3, NFR-2, NFR-3 (full), NFR-5, NFR-9, NFR-10 |

Every functional requirement from [01-product-requirements.md](01-product-requirements.md) appears exactly once as a primary deliverable.

## 4. Per-Phase Definition of Done

A phase is done only when **all** of the following hold:

1. **Functionality** — every listed requirement is implemented end to end (API + UI), not just the API.
2. **Tests** — unit + integration for new backend code, component tests for new UI, and the phase's named E2E scenario passes.
3. **Coverage gates** — ≥ 80 % line coverage on `application` and `domain` packages; **100 % branch coverage on `PermissionResolver`**.
4. **Migrations** — Flyway migrations applied cleanly on a fresh database *and* on the previous phase's database.
5. **Docs** — OpenAPI regenerated, frontend API types regenerated with no diff, and any design deviation recorded in `.docs/adr/`.
6. **Quality gates** — ESLint/Prettier/`tsc --noEmit` clean, ArchUnit rules green, no new critical/high issues from dependency and SAST scans.
7. **Demo** — the phase's demo script (in its phase doc) runs against a fresh environment from seed data.

## 5. Cross-Cutting Work Threads

These run continuously rather than living in a single phase:

| Thread | Practice |
|--------|----------|
| **Security** | Each phase adds its own abuse cases to the test suite; a full OWASP pass happens in Phase 8. |
| **Audit logging** | Every new mutation endpoint writes an audit entry in the same PR that introduces it. |
| **i18n** | Strings go into locale files from Phase 1 onward; only the translation *completeness* pass waits for Phase 7. |
| **Performance** | The 5,000-person fixture exists from Phase 4; tree endpoints are benchmarked whenever they change. |
| **ADRs** | Any deviation from these documents is recorded as a short ADR in `.docs/adr/NNNN-title.md`. |

## 6. Milestones

| Milestone | After | Demonstrates |
|-----------|-------|--------------|
| **M1 — Walking skeleton** | Phase 1 | Register, log in, see an empty dashboard; CI deploys to staging. |
| **M2 — Clans exist** | Phase 2 | Create a clan, define branches, invite by code, approve a join. |
| **M3 — Safe delegation** | Phase 3 | Grant a branch-scoped permission and watch out-of-scope actions get refused. |
| **M4 — The tree lives** | Phase 5 | A real 4-generation *gia phả* rendered, edited by two users with different scopes. |
| **M5 — Feature complete** | Phase 7 | Full acceptance scenarios 1–6 from the PRD pass. |
| **M6 — Release candidate** | Phase 8 | Security review signed off, performance budgets met, runbook and backups tested. |

## 7. Risk Register

| # | Risk | Impact | Likelihood | Mitigation |
|---|------|--------|-----------|------------|
| R1 | Scoped-permission edge cases leak across branches | **Critical** — the product's core promise | Medium | Single resolver, 100 % branch coverage, property tests, the isolation matrix in doc 04 §8 |
| R2 | Tree layout performance collapses on large clans | High | Medium | Flat API + virtualized SVG + 5,000-node fixture benchmarked from Phase 4 |
| R3 | Closure-table drift after complex edits | High | Medium | Same-transaction maintenance, `rebuild_person_ancestry()`, nightly consistency check |
| R4 | Modelling gaps in Vietnamese lineage conventions (multiple wives, adoption, con nuôi, thứ tự sinh) | High | Medium | Union types + parentage `nature` from day one; validate with a real clan record in Phase 5 |
| R5 | Branch re-parenting corrupts paths | Medium | Low | Single recursive SQL update, depth cap, integration tests on deep trees |
| R6 | Scope creep from "just one more field" on Person | Medium | High | PRD field list is the contract; additions need an ADR |
| R7 | Diacritic-insensitive search proves inadequate | Medium | Low | `unaccent` + `pg_trgm` prototyped in Phase 4 with real Vietnamese names |
| R8 | AntD default look makes the product feel generic | Low | Medium | Token system and heritage visual direction fixed in Phase 0/5 (doc 07 §3.3) |

## 8. Working Agreements

- **Branching**: short-lived feature branches off `main`, squash merge, `main` always deployable.
- **PR size**: prefer < 400 changed lines; authorization changes always get a second reviewer.
- **Migrations**: one migration per PR, never edited after merge.
- **Contract-first**: API shape agreed (doc 05 updated) before implementation, so frontend and backend can proceed in parallel within a phase.
- **Vertical slices**: within a phase, ship one feature end to end before starting the next, rather than "all APIs then all screens".
