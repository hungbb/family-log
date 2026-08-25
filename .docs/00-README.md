# Gia Ký — Planning Documentation

**Gia Ký** is a web platform for Vietnamese families and clans (*gia tộc*) to build, manage, and preserve their lineage records (*gia phả*) collaboratively — with branch-scoped permissions and a workflow that maps living user accounts onto historical tree nodes.

This folder is the design source of truth. Code follows these documents; deviations are recorded as ADRs.

---

## Read in this order

| # | Document | What it answers |
|---|----------|-----------------|
| — | [draft.md](draft.md) | The original idea (input, kept unchanged for reference) |
| 01 | [Product Requirements](01-product-requirements.md) | What we are building, for whom, and what "done" means |
| 02 | [Domain Model](02-domain-model.md) | Bounded contexts, aggregates, invariants, domain events |
| 03 | [Data Model](03-data-model.md) | PostgreSQL schema, indexes, closure table, Flyway plan |
| 04 | [Authorization Model](04-authorization-model.md) | **The core differentiator** — scoped permissions and branch isolation |
| 05 | [API Specification](05-api-specification.md) | REST contract, error model, security details |
| 06 | [Backend Architecture](06-backend-architecture.md) | Spring Boot + DDD layering, packages, key components |
| 07 | [Frontend Architecture](07-frontend-architecture.md) | React + AntD + styled-components, fetch client, tree visualizer |
| 08 | [Implementation Roadmap](08-implementation-roadmap.md) | Nine phases, dependencies, milestones, risks |
| 09 | [Engineering Conventions](09-engineering-conventions.md) | Workflow, code rules, testing strategy, coverage gates |

**Phase plans:** [phases/](phases/) — one document per phase with tasks, migrations, tests, demo script, and exit criteria.

## The idea in one paragraph

A clan's family tree is built from `Person` nodes that need **no user account** — historical ancestors are just data. Clans nest into branches (*chi / nhánh*), and editing rights are granted as **scoped permissions** to any number of people: clan-wide, branch-scoped, or subtree-scoped. A branch editor can maintain their own subtree and nothing else, yet everyone still sees **one seamless tree** — branches are a permission overlay, not a data partition. Living members join with a clan code and **claim** their node in the tree, subject to approval by an authorized editor.

## Phase map

| Phase | Focus | Est. | Milestone |
|-------|-------|------|-----------|
| [0 — Foundation](phases/phase-0-foundation.md) | Repo, build, skeletons, guardrails | 1 wk | |
| [1 — Identity & Auth](phases/phase-1-identity-auth.md) | Accounts, JWT, sessions, profile | 1.5 wk | **M1** walking skeleton |
| [2 — Clan & Branch](phases/phase-2-clan-branch.md) | Clans, branch tree, membership, join requests | 2 wk | **M2** clans exist |
| [3 — Authorization](phases/phase-3-authorization.md) | Grants, scoped resolution, enforcement | 2 wk | **M3** safe delegation |
| [4 — Lineage Core](phases/phase-4-lineage-core.md) | Persons, relationships, ancestry closure | 2.5 wk | |
| [5 — Tree Visualizer](phases/phase-5-tree-visualizer.md) | Master view, layout, editing UX | 2.5 wk | **M4** the tree lives |
| [6 — Claiming & Linking](phases/phase-6-claiming-linking.md) | Node claims, approvals, assignment | 1.5 wk | |
| [7 — Media & Content](phases/phase-7-media-content.md) | Photos, biographies, notifications, i18n | 1.5 wk | **M5** feature complete |
| [8 — Hardening & Release](phases/phase-8-hardening-release.md) | Security, performance, ops, launch | 2 wk | **M6** release candidate |

**Total ≈ 16.5 weeks** for 1–2 full-stack engineers.

## Non-negotiable constraints (from the draft)

**Backend** — Java 21, Spring Boot 3.x, PostgreSQL, Flyway, DDD layering, REST + OpenAPI, JWT.

**Frontend** — React (latest) + TypeScript, Ant Design, styled-components, ESLint + Prettier, native `fetch`.
- ❌ **NEVER Tailwind CSS**
- ❌ **NO Axios**
- ✅ **All custom styles inside styled-components**

These are enforced mechanically by ESLint rules (doc 07 §9) and ArchUnit rules (doc 06 §2.1), not by convention alone.

## Decisions still open

Five product questions are unresolved with documented defaults — see [PRD §9](01-product-requirements.md#9-open-questions). None of them block Phase 0–3.

## Working with these documents

- Update the relevant document **in the PR that changes the design**, not afterwards.
- Record any deviation as an ADR in [adr/](adr/) using the template there.
- Each phase doc gets a short completion report appended when the phase ships.
