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
| B5.8 | Benchmark harness against the 5,000-person fixture, run locally | Flags a regression when the tree endpoint exceeds budget; wired into CI later |

## 3. Frontend Tasks

| # | Task | Detail |
|---|------|--------|
| F5.1 | `buildGraph()` — normalize the flat payload into Maps; resolve unions into couple-nodes; order siblings by `birthOrder` → birth date → creation | Pure function, heavily unit-tested |
| F5.2 | `useTreeLayout()` — d3-hierarchy layout maths only | Couples occupy one layout slot; children hang from the union midpoint |
| F5.3 | `<TreeCanvas>` SVG with a single transformed `<g>` | The one sanctioned inline style (doc 07 §7.3), with its ESLint disable and rationale |
| F5.4 | Viewport virtualization | Only nodes intersecting the viewport (plus margin) render |
| F5.5 | `<PersonNode>` card | Name (display weight), years, gender-tinted background, gray card when deceased, branch badge, initials avatar, `canEdit` state |
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

**Backend integration** — `304` returned when `If-None-Match` matches; a lineage mutation bumps `treeVersion` and busts the ETag; `canEdit` flags match the resolver's decisions for a branch-scoped user (cross-checked against per-node `can()` calls in the test).

**Performance (NFR-1)** — 2,000-node tree renders within 1 s from data to painted frame; pan/zoom holds 60 fps; the 5,000-node fixture stays usable with virtualization on (measured by hand in the browser profiler, recorded in the phase report).

**Frontend (manual)** — spot-check `buildGraph` output for a person with two unions and children from each, adopted children, one known parent, a detached fragment, and sibling ordering fallbacks; the layout has no overlapping nodes; `TreeCanvas` shows the expected node count; collapse hides descendants; `canEdit === false` renders no hover actions; keyboard arrows move focus between siblings and generations. (`buildGraph` is a pure function, written to stay easy to cover once an automated frontend suite is added.)

**Accessibility (manual)** — a browser axe extension is clean on the tree page; the list view is fully navigable by keyboard and announces generation groupings.

**Manual walkthrough** — a branch-scoped editor opens the master view, sees the whole clan including other branches, adds a child under their own node, and finds the add action absent on a node in another branch.

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
- [ ] The list view is a complete, accessible alternative — clean under a browser axe extension.
- [ ] M4 milestone demo runs locally with real (or realistic) clan data.

## 8. Notes & Risks

- **R2 (layout performance) concentrates here.** Virtualize before optimizing anything else; measure with the 5,000-node fixture, not a toy tree. Canvas rendering stays the documented fallback if SVG cannot hold 60 fps, but SVG keeps accessibility and text selection, so try hard first.
- **Couple rendering is the layout subtlety.** Multiple unions per person (common in older *gia phả*) break naive tree layouts — treat the couple as one layout node and give secondary unions a distinct visual treatment.
- **Validate with a real record (R4).** Sit with an actual family book at the start of this phase; layout conventions people expect (right-to-left birth order, đời labels, branch grouping) are cheaper to honour now than to retrofit.
- **The list view is not a consolation prize.** It is the accessible path, the printable path, and the fastest way to audit data — keep it first-class.

---

## 9. Phase Report

> Appended on completion, per doc 09 §5.

### What shipped

**Backend.** All of B5.1–B5.8.

- **B5.2 — the tree read left JPA.** `TreeSnapshotRepository` is a purpose-built read port and
  `TreeSnapshotJdbcAdapter` is three flat scans behind it. The aggregate read it replaces returned
  whole `Person` records — `notesHtml` and `notesText` included, several megabytes of rich text on
  the fixture — and hydrated every row into a persistence context that kept a dirty-checking snapshot
  a read-only request never uses. Both costs scaled with the clan, on the hottest read in the
  product. The new rows carry the fourteen columns a node card renders. Edges join *both* of their
  endpoints to live persons: soft-deleting somebody leaves their edges alone (I-P7), so an edge can
  outlive the node at one end, and a payload carrying it draws a line into empty space.
- **B5.3 — `TreeSnapshotCache`, on Caffeine, keyed by (clan, tree version).** There is no
  invalidation to get wrong: a mutation bumps the version inside its own transaction (B4.12), so an
  entry is either current or unreachable. The cache holds *rows*, never the finished payload —
  `canEdit` is per-caller and is stamped on after the cache, from the batch resolver. Bounded by
  weight in people rather than by entry count, because one entry is one clan and clans differ by
  three orders of magnitude. `ETag` / `If-None-Match` → `304` was already in place from B4.12 and is
  the layer above this one: an unchanged tree costs one indexed read and never reaches the cache.
- **B5.5 — statistics read the cached snapshot** rather than making a scan of their own. Whoever
  asks for them is looking at the tree, so the rows are already in the cache under the same key.
- **B5.6/B5.8 — the benchmark harness grew three measurements**: an uncached tree read (the version
  is bumped between warmups so the measurement is a real scan and not a map lookup), a cached read,
  and the batch `EditableSet`. The 5,000-person fixture now records marriages as well as wives, so
  the union scan and the canvas's couple rendering are both exercised at size.
- **B5.1, B5.4, B5.7** were delivered in Phase 4 and are unchanged. B5.7's client strategy is
  documented on `TreeQuery` in `web/src/api/endpoints/tree.ts`.

**Frontend.** All of F5.1–F5.15.

- `model/buildGraph.ts` (F5.1) normalizes the flat payload into couple-slots: an *anchor*, the
  spouses beside them, and children hanging from the marriage they were born into. Pure — no React,
  no d3, no DOM.
- `model/layout.ts` (F5.2) is layout maths only, over `d3-hierarchy`'s tidy tree. A couple occupies
  one layout slot and the separation function is told how wide each slot actually is, which is what
  stops a man with three wives overlapping his brother. Both orientations are one set of maths in
  *axis space*.
- `TreeCanvas` (F5.3, F5.4, F5.8, F5.15) is one transformed `<g>`, viewport virtualization with a
  400 px overscan, `requestAnimationFrame`-batched pan commits, memoized cards, wheel/pinch zoom
  about the pointer, and arrow-key navigation between siblings and generations.
- `PersonNode` (F5.5, F5.11, F5.12), `UnionLine`/`ParentEdgePath` (F5.6), generation rails labelled
  from the data rather than from the depth (F5.7), `TreeToolbar` (F5.9), `MiniMap` (F5.10),
  `TreeListView` (F5.13) and the skeleton/empty states (F5.14).
- `useTreeViewStore` holds viewport, collapsed set, orientation, focus and branch highlighting, and
  resets when the clan changes.

**Migrations.** None, as §4 said.

### Measured

| | |
|---|---|
| Backend unit tests | **531 pass** (526 before this phase) |
| Frontend | `eslint --max-warnings=0`, constraint script, `tsc --noEmit`, `vite build` all clean |
| Tree chunk | 312 KB raw / 94 KB gzipped, lazily loaded per route — the initial bundle is unchanged |

**Integration tests were written but not executed in the authoring environment** — it has no Docker
daemon and every `*IT` needs Testcontainers. `LineageCoreIT.cachedTreeIsBustedByAnEdit` and the three
new `LargeTreePerformanceIT` measurements are complete and must be run (`./mvnw verify`) on a machine
with Docker. Until then **the NFR-1 numbers in §7 are unmeasured**, and so are the 60 fps pan and the
2,000-node paint, which §5 asks for by hand in a browser profiler. §7's exit criteria are not met
until somebody does both.

### Deviations from the design docs

1. **No inline style on the canvas.** Doc 07 §7.3 and F5.3 anticipated one sanctioned `style` prop
   with an ESLint disable. It turned out not to be needed: the pan/zoom transform is an SVG
   *attribute* on a `<g>`, which `react/forbid-dom-props` does not touch. The comment in
   `eslint.config.js` was corrected to say so — there is no disable comment for that rule anywhere
   in the codebase, and there should not be one.
2. **The tree read model lives in `lineage.domain` as `TreeSnapshotRepository`'s row records**, not
   as the aggregates. Doc 06 §2.1 keeps ports in `domain`, and these are rows a port returns; they
   deliberately carry no invariants, because re-validating five thousand records to draw a picture is
   work with no reader.

### Notes for the next phase

- **One person is drawn in one place.** A tree is a spanning tree of a graph that is not one. Two
  blood descendants of the same clan who marry each other — not rare in an old book — are drawn as
  one couple under whichever of them the walk reaches first, and the second one's edge to their own
  parents is not drawn. Nobody vanishes and no child is orphaned; the list view still shows both
  parentages. Any layout avoiding this would have to draw somebody twice, which is worse.
- **"Find me" is inert until Phase 6.** It looks for a node whose `linkedUserId` is the signed-in
  account, and almost nothing is linked yet, so the control is hidden rather than shown disabled.
  Linking will light it up with no change here.
- **Avatars are initials.** `avatarMediaId` is carried through the payload and ignored; Phase 7
  resolves it. The initials are taken from the *end* of the name, because Vietnamese names run family
  name first and the last word is the one somebody is actually called by.
- **R4 is still open.** §8 asks for a real family book to be read at the start of this phase; the
  layout conventions honoured here — right-to-left birth order via `birthOrder`, *đời* rails,
  *chính thất* before *thứ thất*, secondary unions drawn distinctly — come from the design docs
  rather than from a book on the table. Worth checking against one before M4 is demoed.
