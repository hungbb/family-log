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

---

## 9. Phase Report

> Appended on completion, per doc 09 §5.

### What shipped

**Backend.** All of B4.1–B4.15. `common.types.PartialDate` with three-valued comparison; the
`Person`, `Parentage` and `Union` aggregates with I-P1…I-P8, I-R1…I-R5 and I-N1…I-N4 enforced in a
framework-free domain; `LineageGraphService` (cycle detection, generation cascade) and
`AncestryProjection` over the `person_ancestry` closure; `PersonService` with placement-driven
create, `PUT`/`PATCH`, soft delete, restore and move-branch; `RelationshipService` with the
multi-target checks of doc 04 §5.2; `TreeQueryService` for the flat master view, subtree reads,
ancestor chains and stats; diacritic-insensitive search over `tsvector` + trigram; a per-clan
`treeVersion` behind the tree `ETag`; OWASP-sanitized notes with a plain-text projection; audit
entries with before/after JSONB on every mutation.

**`SUBTREE` scope is live** (B4.8). Phase 3's `DeferredLineageAncestryAdapter` is deleted and
`ClosureLineageAncestryAdapter` reads the closure through `LineageDirectory`. The Phase-3 `422
SUBTREE_SCOPE_UNAVAILABLE` is gone from the happy path; a subtree scope naming a person the clan does
not have now answers `404 PERSON_NOT_FOUND`, the same rule branch scope always had.
`PermissionResolver` and `ScopeCoverage` were **not modified** — which is what Phase 3's port design
was buying.

**Frontend.** All of F4.1–F4.10: `PartialDateInput` (precision first, then only the fields that
follow from it, plus a lunar text field); the create form with a placement picker and `PersonPicker`;
the edit form with a version-conflict dialog that offers a reload rather than a retry; the person
detail drawer; relationship editors; the list view of the tree; the search page with filters;
`canEdit`-driven read-only rendering that names the branch the caller *can* edit; soft-delete
confirmation naming affected children and a restore list for administrators; en/vi strings including
the kinship vocabulary (cha, mẹ, con, vợ, chồng, đời).

**Migrations.** `V7` (persons, generated `name_search`, `clan_tree_versions`), `V8` (parentages,
unions), `V9` (closure table, `rebuild_person_ancestry`, `recompute_person_ancestry_from`), `V10`
(the `branches.branch_root_person_id` foreign key deferred from Phase 2).

### Measured

| | |
|---|---|
| Backend unit tests | **526 pass** (439 before this phase) |
| Frontend | `eslint --max-warnings=0`, constraint script, `tsc --noEmit`, `vite build` all clean |
| Tree chunk | 275 KB raw / 84 KB gzipped, lazily loaded per route |

**Integration tests were written but not executed in the authoring environment** — it has no Docker
daemon, and every `*IT` needs Testcontainers. `LineageSchemaIT`, `LineageCoreIT`,
`AncestryProjectionPropertyIT`, `SubtreeIsolationIT` and `LargeTreePerformanceIT` are complete and
must be run (`./mvnw verify`) on a machine with Docker before this phase is called done. Until then
the NFR-1 numbers in §7's exit criteria are **unmeasured**, and so is the closure property test that
§8 names as the release gate.

### Deviations from the design docs

1. **`clan_tree_versions` is its own table**, not a column on `clans`. Bumping `clans.version` on
   every person insert would answer a concurrent settings edit with `409 VERSION_CONFLICT`. Recorded
   in doc 03 §3.7.1.
2. **`recompute_person_ancestry_from(clan_id, person_id)`** joins the one function doc 03 §3.10 named.
   Doc 03 described removal as "a recursive CTE"; this is that, as a named function so the SQL lives
   in one place. Recorded in doc 03 §3.10.
3. **`AncestryProjectionPropertyTest` is an `IT`.** The subject is the maintenance SQL. Doc 09 §4.5
   updated with the reason.
4. **`PersonSummary.avatarUrl` is `avatarMediaId`.** §1 of this phase puts avatar upload in Phase 7
   and says `avatarMediaId` is accepted but unset; doc 05 §3.3 updated to match.
5. **`GET /clans/{clanId}/persons/deleted`** added to doc 05 §4.7 — F4.9 needs a list to restore from
   and the spec had only the restore endpoint.
6. **`Paged` moved** from `clanorg.application` to `common.types`, so Lineage can page without
   depending on ClanOrg. No behaviour change.
7. **`LineageAncestryPort` grew two methods** — `personExists` and `displayNames`. The first is what
   resolves a subtree scope before it is stored; the second puts a person's name on a subtree grant
   in the permissions screen instead of a UUID. Both go through the one port Authorization already
   had rather than a second one.

### Notes for the next phase

- **Placement is the only way into a branch.** `BRANCH_ROOT` places one person per chi and is refused
  for a second; everything else hangs off somebody. That is the model working as intended, but it
  means seeding an empty chi is a two-step (`UNPLACED`, then move-branch) that needs clan-wide
  `TREE_EDIT`. Worth revisiting with a real *gia phả* in hand — it is the same conversation as R4.
- **Generation takes the shortest path to the founding ancestor** when two parents disagree, matching
  how the closure measures depth. Documented on `LineageGraphService.recomputeGenerationsFrom`.
- **`PartialDate.CIRCA_TOLERANCE_YEARS` is 5.** It decides which records I-P2 refuses; named rather
  than inlined so the argument about it is an argument about one constant.
