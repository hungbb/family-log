# Phase 5 — Tree Visualizer

**Goal:** the *gia phả* people actually recognize — one seamless, navigable tree that aggregates every branch, with in-place editing where the user has scope (M4).

**Estimate:** ~2.5 weeks · **Depends on:** [Phase 4](phase-4-lineage-core.md) · **Next:** [Phase 6](phase-6-claiming-linking.md)

**Requirements:** FR-T2, FR-T3, FR-T4, goal G6, NFR-1, NFR-7

---

## 1. Scope

**In scope** — the SVG tree canvas with d3-hierarchy layout, couple (union) rendering, generation rails, pan/zoom/collapse/focus, viewport virtualization, per-node `canEdit` affordances, branch badges and highlighting, the master-view aggregation endpoint tuning, ETag caching, and the accessible list-view toggle.

**Out of scope** — print/PDF export (Phase 8), photo rendering on nodes (Phase 7 — initials placeholders for now), real-time collaboration (non-goal N4), custom layout algorithms beyond the two orientations below.

## 2. Backend Tasks

| # | Task | Detail |
|---|------|--------|
| B5.1 | `GET /tree` finalized as the master view (FR-T2) | Single flat payload; branches are metadata on nodes, never a partition |
| B5.2 | Native flat-fetch queries replacing any JPA path | Three indexed scans: persons, parentages, unions |
| B5.3 | Caffeine cache keyed by (clanId, treeVersion) + `ETag` / `If-None-Match` → `304` | |
| B5.4 | `GET /tree/ancestors/{personId}` and `/tree/descendants/{personId}` | Breadcrumbs and focused subtree loads |
| B5.5 | `GET /tree/stats` | Counts by generation, living/deceased, per branch, unlinked nodes |
| B5.6 | Batch `EditableSet` performance pass | O(number of grants) queries, independent of tree size — benchmarked |
| B5.7 | Depth-bounded partial loads for very large clans | `?rootPersonId=&depth=` with a documented client strategy for > 3,000 nodes |
| B5.8 | Benchmark suite against the 5,000-person fixture in CI | Fails the build if the tree endpoint regresses beyond budget |

## 3. Frontend Tasks

| # | Task | Detail |
|---|------|--------|
| F5.1 | `buildGraph()` — normalize the flat payload into Maps; resolve unions into couple-nodes; order siblings by `birthOrder` → birth date → creation | Pure function, heavily unit-tested |
| F5.2 | `useTreeLayout()` — d3-hierarchy layout maths only | Couples occupy one layout slot; children hang from the union midpoint |
| F5.3 | `<TreeCanvas>` SVG with a single transformed `<g>` | The one sanctioned inline style (doc 07 §7.3), with its ESLint disable and rationale |
| F5.4 | Viewport virtualization | Only nodes intersecting the viewport (plus margin) render |
| F5.5 | `<PersonNode>` card | Name (serif), years, gender rail, deceased treatment, branch badge, initials avatar, `canEdit` state |
| F5.6 | `<UnionLink>` and `<ParentEdge>` | Orthogonal connectors, union type indicated (secondary/sequential rendered distinctly) |
| F5.7 | Generation rails labelled *Đời N* | |
| F5.8 | Interactions: wheel/pinch zoom, drag pan, click select, double-click focus, collapse/expand subtree, keyboard navigation | `useTreeViewStore` holds viewport and collapsed-node state |
| F5.9 | Toolbar: zoom controls, fit-to-screen, orientation (vertical/horizontal), "find me", branch filter/highlight, search-and-jump | |
| F5.10 | MiniMap for large trees | |
| F5.11 | In-canvas editing affordances | Hover actions on editable nodes: add child, add spouse, edit, delete — opening the Phase 4 forms in a drawer |
| F5.12 | Out-of-scope nodes muted with an explanatory tooltip | The visual expression of branch isolation: see everything, edit your branch |
| F5.13 | List-view toggle (NFR-7) | The Phase 4 table becomes the permanent accessible alternative, sharing the same data hooks |
| F5.14 | Loading and empty states | Skeleton canvas; a genuinely helpful empty state ("Add the founding ancestor to begin") |
| F5.15 | Performance pass | Memoized nodes, `requestAnimationFrame`-batched transforms, profiling against the 5,000-node fixture |

## 4. Migrations

None. This phase is read-path and UI work; any index tuning discovered during benchmarking goes into `V14__add_search_indexes.sql` in Phase 8.

## 5. Tests

**Unit (frontend)** — `buildGraph` for: a person with two unions and children from each; adopted children; a person with one known parent; a detached fragment; sibling ordering fallbacks. Layout produces no overlapping nodes for these fixtures.

**Component** — `TreeCanvas` renders the expected node count for a fixture; collapse hides descendants; `canEdit === false` renders no hover actions; keyboard arrows move focus between siblings and generations.

**Integration** — `304` returned when `If-None-Match` matches; a lineage mutation bumps `treeVersion` and busts the ETag; `canEdit` flags match the resolver's decisions for a branch-scoped user (cross-checked against per-node `can()` calls in the test).

**Performance (NFR-1)** — 2,000-node tree renders within 1 s from data to painted frame; pan/zoom holds 60 fps; the 5,000-node fixture stays usable with virtualization on (measured, recorded in the phase report).

**Accessibility** — axe scan clean on the tree page; the list view is fully navigable by keyboard and announces generation groupings.

**E2E** — a branch-scoped editor opens the master view, sees the whole clan including other branches, adds a child under their own node, and finds the add action absent on a node in another branch.

## 6. Demo Script

1. Open a clan with four generations across three branches — the whole *gia phả* renders as one tree (G6).
2. Zoom out to the minimap, then use "find me" to jump to a node.
3. Toggle branch highlighting: *Chi Hai* lights up while the rest dims.
4. As a *Chi Hai*-scoped editor, hover a node in *Chi Hai* → add-child action appears; hover a *Chi Trưởng* node → muted, tooltip explains the scope.
5. Add a child in-canvas; the tree re-lays out around the new node without losing the viewport.
6. Toggle to list view and navigate the same data with the keyboard only.

## 7. Exit Criteria

- [ ] FR-T2, T3, T4 complete; the master view aggregates all branches seamlessly.
- [ ] Performance budgets met and recorded (2,000-node render, 60 fps pan/zoom).
- [ ] Branch isolation is visible and correct in the UI, and still enforced server-side.
- [ ] The list view is a complete, accessible alternative — axe clean.
- [ ] M4 milestone demo runs on staging with real (or realistic) clan data.

## 8. Notes & Risks

- **R2 (layout performance) concentrates here.** Virtualize before optimizing anything else; measure with the 5,000-node fixture, not a toy tree. Canvas rendering stays the documented fallback if SVG cannot hold 60 fps, but SVG keeps accessibility and text selection, so try hard first.
- **Couple rendering is the layout subtlety.** Multiple unions per person (common in older *gia phả*) break naive tree layouts — treat the couple as one layout node and give secondary unions a distinct visual treatment.
- **Validate with a real record (R4).** Sit with an actual family book at the start of this phase; layout conventions people expect (right-to-left birth order, đời labels, branch grouping) are cheaper to honour now than to retrofit.
- **The list view is not a consolation prize.** It is the accessible path, the printable path, and the fastest way to audit data — keep it first-class.
