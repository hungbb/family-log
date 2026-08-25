# Phase 4 — Lineage Core

**Goal:** the family tree as data — person nodes decoupled from user accounts, parent-child and union relationships, and the ancestry projection that makes subtree permissions possible.

**Estimate:** ~2.5 weeks · **Depends on:** [Phase 3](phase-3-authorization.md) · **Next:** [Phase 5](phase-5-tree-visualizer.md)

**Requirements:** FR-P1 … FR-P11, FR-T1, FR-T5, FR-T6, goal G1

---

## 1. Scope

**In scope** — the `Person` aggregate with partial dates; parentage and union relationships with full invariant enforcement; cycle detection; generation computation; the `person_ancestry` closure projection; `SUBTREE` scope activation; soft delete and restore; person search (diacritic-insensitive); the person detail and edit UI; a basic list/table view of the tree.

**Out of scope** — the graphical tree visualizer (Phase 5 — this phase ships a table/list view so the data is usable and testable); node claiming (Phase 6); avatar upload (Phase 7 — `avatarMediaId` accepted but unset); rich-text notes editor (Phase 7 — plain textarea with server-side sanitization for now).

## 2. Backend Tasks

| # | Task | Detail |
|---|------|--------|
| B4.1 | `common/types.PartialDate` value object | Precision, `circa`, range, lunar text; three-valued comparison (`BEFORE`/`AFTER`/`INDETERMINATE`) |
| B4.2 | `lineage/domain`: `Person`, `PersonName`, `Union`, `Parentage` with invariants I-P1…I-P8, I-R1…I-R5, I-N1…I-N4 | Framework-free, unit-tested without a database |
| B4.3 | JPA entities + mappers + repositories (all clan-scoped) | Partial-date columns flattened per doc 03 §3.7 |
| B4.4 | `LineageGraphService` | Cycle detection before edge insert; generation recompute cascading to descendants; ancestor/descendant queries |
| B4.5 | **`AncestryProjection`** | Maintains `person_ancestry` in the same transaction as parentage changes: cross-product insert on add, recursive recompute on remove, `rebuild_person_ancestry(clanId)` for repair |
| B4.6 | `PersonService`: create (with `placement`), update, patch, soft delete, restore, move branch | `placement` decides both `branchId` inheritance and the authorization target |
| B4.7 | `RelationshipService`: add/remove parentage, create/update/delete union, attach child to union | Multi-target permission checks per doc 04 §5.2 |
| B4.8 | Activate `SUBTREE` scope | Implement `LineageAncestryPort` against the closure table; remove the Phase-3 `422`; grants of subtree scope now work |
| B4.9 | `TreeQueryService` v1 | Flat fetch (persons + parentages + unions + branches) for a clan or subtree, depth-bounded |
| B4.10 | `EditableSet` integration | `canEdit` stamped on every returned person (doc 04 §4.1) |
| B4.11 | Person search | `unaccent` + `tsvector` + trigram; filters for generation, life status, branch, linked/unlinked |
| B4.12 | Per-clan `treeVersion` counter → HTTP `ETag` | Bumped by any lineage mutation |
| B4.13 | HTML sanitization for `notes` on write | Allow-list policy (OWASP Java HTML Sanitizer); plain-text projection stored alongside |
| B4.14 | Audit entries for every person/relationship mutation with before/after JSONB | |
| B4.15 | `LargeTreeGenerator` test fixture (5,000 persons, 12 generations, 8 branches) | Feeds the performance thread from here on |

## 3. Frontend Tasks

| # | Task | Detail |
|---|------|--------|
| F4.1 | `PartialDateInput` component | Year-only / month+year / exact / circa toggle, plus an optional lunar text field. Handles the reality that most ancestors have year-only dates |
| F4.2 | Person create form with `placement` picker | "Child of…", "Spouse of…", "Branch root", "Unplaced"; `PersonPicker` searches within scope |
| F4.3 | Person edit form with optimistic concurrency | `409 VERSION_CONFLICT` opens a "changed elsewhere — reload" dialog rather than overwriting |
| F4.4 | Person detail drawer (FR-T6) | Attributes, parents, unions, children, siblings, linked user placeholder |
| F4.5 | Relationship editors | Add/remove parent, create union with type and status, attach existing person as child |
| F4.6 | **List view** of the clan tree | AntD `Table` grouped by generation, expandable, sortable — the interim view *and* the permanent accessible alternative (NFR-7) |
| F4.7 | Person search page with diacritic-insensitive input and filters | |
| F4.8 | `canEdit`-driven affordances | Out-of-scope nodes render read-only with a tooltip naming the branch the user can edit |
| F4.9 | Soft-delete UX | Confirmation naming affected children; restore from a "recently deleted" list for admins |
| F4.10 | i18n including Vietnamese kinship labels (cha, mẹ, con, vợ, chồng, đời) | |

## 4. Migrations

| Version | Contents |
|---------|----------|
| `V7__create_persons.sql` | `persons` with partial-date columns, generated `name_search` tsvector, all indexes and checks (doc 03 §3.7) |
| `V8__create_parentages_and_unions.sql` | `parentages`, `unions` with canonical partner ordering and partial unique indexes |
| `V9__create_person_ancestry.sql` | Closure table, indexes, and the `rebuild_person_ancestry(uuid)` function |
| `V10__link_branches_to_root_person.sql` | Adds the FK `branches.branch_root_person_id → persons.id` deferred from Phase 2 |

## 5. Tests

**Unit** — `PartialDate` comparison including indeterminate cases; every person, parentage, and union invariant; generation recomputation across multi-level cascades.

**Integration** — create a 4-generation tree from seed data; a cycle attempt returns `422 CYCLE_DETECTED`; a second father edge returns `409`; a duplicate union returns `409`; soft delete hides the node from tree reads and search; restore returns it; `409 VERSION_CONFLICT` on a stale update.

**Closure correctness** — after randomized sequences of add/remove parentage, `person_ancestry` matches a freshly computed reference closure (property test). Explicit cases: remove a middle edge, re-parent a node with a large subtree, and a diamond (a child whose parents share an ancestor).

**Authorization (SUBTREE now live)** — a `TREE_EDIT@SUBTREE(Uncle C)` holder may edit C and every descendant, and receives `403` for C's parent, C's siblings, and C's spouse's parents. Multi-target rules: linking a node inside scope to a parent outside scope returns `403`.

**Performance (NFR-1)** — full tree read of the 5,000-person fixture under the budget; closure maintenance on a deep-subtree re-parent measured and recorded.

**Frontend (manual)** — partial-date input round-trips every precision; version conflict dialog appears; read-only rendering for `canEdit === false`.

**Manual walkthrough** — build a small lineage through the UI, add a second wife with a `SECONDARY` union and children, verify the list view and search find them.

## 6. Demo Script

1. Admin adds the founding ancestor (đời 1) and marks him as such.
2. Adds a wife (`PRIMARY` union) and three children with birth order.
3. Adds a `SECONDARY` union and a child from it — both families appear correctly under the same father.
4. Grants a member `TREE_EDIT@SUBTREE` on the đời-3 person; that member adds descendants successfully, and is refused when editing his own father.
5. Searches "nguyen van" without diacritics and finds "Nguyễn Văn An".
6. Soft-deletes a duplicated node and restores it from the deleted list.

## 7. Exit Criteria

- [ ] FR-P1…P11, FR-T1, FR-T5, FR-T6 complete end to end.
- [ ] Nodes can be created with no user account (G1) — proven by the whole demo running without any linking.
- [ ] `SUBTREE` scope active with its own isolation tests passing.
- [ ] Closure-table property test green over randomized edit sequences.
- [ ] Tree read of 5,000 persons within the performance budget.
- [ ] Coverage ≥ 80 % on `lineage.domain` and `lineage.application`.

## 8. Notes & Risks

- **R3 (closure drift) is the phase's main risk.** Keep maintenance in the mutation transaction, never in an async listener, and treat the property test as a release gate rather than a nice-to-have.
- **R4 (lineage modelling).** Validate the model against a real *gia phả* before Phase 5 — especially multiple wives, adopted children (con nuôi), and sons-in-law recorded in the book. Model gaps found later are expensive.
- **Generation numbers are advisory.** They are recomputed from edges but may be unknown for imported or fragmentary records; the UI must tolerate `null` rather than defaulting to 0.
- **Placement decides authorization.** A person created "unplaced" has no branch and therefore no scope — restrict creation of unplaced nodes to `TREE_EDIT@CLAN` holders, or the branch-isolation guarantee has a hole.
