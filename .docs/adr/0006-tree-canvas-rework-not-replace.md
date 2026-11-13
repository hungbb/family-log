# ADR 0006 — Rework the tree canvas's own SVG renderer rather than adopt a graph library

- **Status:** Proposed
- **Date:** 2026-11-13
- **Deciders:** hungbb
- **Affects:** [07-frontend-architecture.md](../07-frontend-architecture.md) §7; [0002-modern-minimalist-palette.md](0002-modern-minimalist-palette.md) (adds the canvas's light/dark palette); issue [#61](https://github.com/hungbb/family-log/issues/61)

## Context

[#61](https://github.com/hungbb/family-log/issues/61) asks for the family-tree map to be redesigned —
theming in light and dark, connectors that stay visible at every zoom, restrained motion, print with
re-arranging to fit the paper, and a documented visual vocabulary — and asks first for a decision:
rework the hand-written SVG canvas, or adopt a free, open-source renderer.

Two requirements dominate that decision, as the issue itself predicts:

1. **Print** wants vector output. Whatever draws the tree has to be able to draw it again onto a page
   of a given physical size, tile by tile, with text that stays text.
2. **The domain is ours either way.** No general-purpose graph library models a marriage, a *thứ
   thất*, remarriage, adoptive vs. biological descent, or the couple-as-one-slot layout. That layer
   (`buildGraph.ts`, `layout.ts`) stays hand-written whatever draws the pixels.

## Decision

**Rework the existing renderer.** Keep `d3-hierarchy` for the tidy layout and the hand-written SVG for
drawing; split the drawing out of `TreeCanvas` into a `TreeScene` that the screen and the print sheet
share. Add no runtime dependency.

What changes:

- **Palette.** `tokens.tree.light` / `tokens.tree.dark`, published by the canvas frame as `--tree-*`
  custom properties. The print sheet and exports publish the light set on their own roots, so printing
  is light whatever the screen shows. A toolbar control switches the scheme; the choice is remembered.
- **Connectors** use `vector-effect: non-scaling-stroke` with a zoom-following but floored width
  (`screenStroke`), and darker/lighter edge colours that clear 3:1 against the surface.
- **Motion** (`model/motion.ts`): ~260 ms layout glide on collapse/expand, ~300 ms camera tween on
  jump/fit, highlight dims eased; never on pan or wheel; off under `prefers-reduced-motion` and when
  more than 600 slots are on screen.
- **Print and export** (`model/print.ts`, `PrintDialog`, `PrintSheet`): the tree is laid out in both
  orientations and the planner picks tree orientation × paper orientation for the fewest pages (or the
  largest single page), then tiles with overlap at a legible scale. Pages are SVG sized in millimetres,
  so the browser prints vectors and "Save as PDF" is the PDF export. SVG and PNG downloads reuse the
  same drawing with computed styles inlined.
- **Visual vocabulary** (`model/visual.ts`, `Legend`, `Glyphs`): one definition read by canvas,
  minimap, legend, list view and print, with a non-colour channel for every category.

## Alternatives Considered

Licences below are the `license` field on the npm registry as of 2026-11-13; nothing was adopted, so
no LICENSE file was audited.

| Option | Licence | Why not |
|--------|---------|---------|
| `@xyflow/react` (React Flow) | MIT | DOM nodes: weaker print story and heavier on a 5,000-person clan; no notion of unions, so the domain layer stays ours anyway; replaces the virtualization and keyboard model we already have |
| Topola | Apache-2.0 | Purpose-built and SVG, but its chart vocabulary and layout are its own; the couple-slot layout, stacking (#60), slots and our card would have to be re-expressed or forked |
| `family-chart` | ISC on the registry (the issue lists MIT core + paid tier) | Core coverage of multi-wife / adoption semantics unclear; adopting it to then override its layout and styling buys little |
| Cytoscape.js | MIT | Canvas rendering: no selectable text, raster print |
| `elkjs` / `dagre` | EPL-2.0 OR GPL-3.0 / MIT | Layout engines only; `d3-hierarchy` plus the slot model has not been the limiting factor |
| `dTree`, `relatives-tree` | MIT | Unmaintained or very small; reference at most |

## Consequences

**Positive** — no new dependency or licence; the a11y, virtualization, keyboard and deep-link behaviour
is untouched; print is genuinely vector; the palette and vocabulary live in one place each.

**Negative** — all of the design is ours to maintain. Connectors fade rather than morph during the
layout glide (morphing an elbow path would need a path-interpolation library). A PDF file is produced
through the browser's print dialog rather than a direct download. The app chrome stays light-only;
only the canvas has a dark scheme.

**Follow-up** — if printed trees get large enough that browser print struggles, a direct PDF export
(e.g. `svg2pdf.js` + `jsPDF`, both MIT) can sit on the same `TreeTile` drawing.
