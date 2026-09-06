import { type TFunction } from 'i18next';
import {
  type RefObject,
  useCallback,
  useEffect,
  useId,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from 'react';
import { useTranslation } from 'react-i18next';

import { type PersonSummary } from '@/api/endpoints/persons.ts';

import { type TreeGraph } from '../../model/buildGraph';
import {
  boxOf,
  intersects,
  type LaidOutSlot,
  type TreeBounds,
  type TreeLayout,
} from '../../model/layout';
import { ZOOM, useTreeViewStore } from '../../stores/useTreeViewStore';

import { MiniMap, MINIMAP_THRESHOLD } from './MiniMap';
import { PersonNode, type NodeAction } from './PersonNode';
import {
  CanvasFrame,
  CanvasSurface,
  CollapsedCount,
  CollapseToggle,
  ParentEdgePath,
  RailLabel,
  RailLine,
  UnionLine,
  VisuallyHidden,
} from './styles';

/**
 * The tree, drawn (F5.3, F5.4, F5.6, F5.7, F5.8, F5.15).
 *
 * ## One transformed group
 *
 * Everything lives inside a single `<g transform="translate(…) scale(…)">`. Panning and zooming
 * change two numbers on that one element; nothing below it moves, re-measures or re-renders. The
 * alternative — repositioning nodes — makes every frame proportional to the tree.
 *
 * The transform is an SVG **attribute**, not the `style` prop. Doc 07 §7.3 anticipated an inline
 * style here and ESLint forbids `style` on DOM elements; using the attribute means the exception is
 * not needed at all, which is a better answer than a disable comment.
 *
 * ## Why SVG and not canvas
 *
 * Phase 5 §8 keeps canvas as the fallback. SVG holds because the text is real text — selectable,
 * translatable, and reachable by a screen reader — and because virtualization removes the reason to
 * switch: what costs is the number of elements in the document, and only what fits the viewport is
 * ever in it.
 *
 * ## The frame budget (F5.15)
 *
 * Three things keep a pan at 60 fps on the five-thousand-node fixture. Every card is memoized, so a
 * viewport change re-renders none of them. The viewport is committed inside a
 * `requestAnimationFrame`, so a burst of pointer events produces one render rather than forty. And
 * only slots intersecting the viewport — plus a margin, so nothing pops in at the edge — are in the
 * document at all.
 */

export interface TreeCanvasHandle {
  /** Scale and centre so the whole tree is on screen. */
  fit: () => void;
  /** Centre on a person, whichever slot they are drawn in, and select them. */
  jumpTo: (personId: string) => void;
}

interface TreeCanvasProps {
  graph: TreeGraph;
  layout: TreeLayout;
  controlRef: RefObject<TreeCanvasHandle | null>;
  /** The node the signed-in account is linked to, if any — drawn with an accent ring (F6.7). */
  myPersonId?: string;
  onOpenPerson: (personId: string) => void;
  onNodeAction: (action: NodeAction, person: PersonSummary) => void;
}

/** Rendered beyond the viewport on every side, so a fast pan never shows an empty band. */
const OVERSCAN = 400;

/**
 * How far a pointer must travel before the press counts as a pan rather than a click, in CSS px.
 *
 * A press with a hand behind it is never perfectly still, so a threshold of zero would turn every
 * click into a one-pixel drag. Four is the usual slop — small enough that a deliberate drag feels
 * immediate, large enough to absorb the wobble of a click or a tap.
 */
const DRAG_SLOP = 4;

/** Leaves the tree a little room inside the frame when fitting. */
const FIT_PADDING = 48;

/**
 * A press in progress, and whether it has become a pan.
 *
 * **Pointer capture cannot be taken on `pointerdown`.** A captured pointer retargets more than the
 * pointer events: the spec sends the compatibility `click` to the capture element too, so capturing
 * the surface the moment a button goes down hands every click on the canvas to the `<svg>` and none
 * to the card, the action button or the collapse handle underneath the cursor — their `onClick`
 * never runs and the node's four icons do nothing (#18).
 *
 * So capture waits for movement. Below `DRAG_SLOP` the press is left entirely alone and its click
 * lands where it was aimed; past it the pointer is captured and the press is a pan, which also means
 * a drag released over a card no longer opens that person's drawer on the way out.
 */
interface Drag {
  pointerId: number;
  /** The previous position, for this frame's delta. */
  x: number;
  y: number;
  /** Where the press started, for the distance that decides pan-or-click. */
  originX: number;
  originY: number;
  /** True once the slop was crossed — the pointer is captured and the tree is moving. */
  panning: boolean;
}

export function TreeCanvas({
  graph,
  layout,
  controlRef,
  myPersonId,
  onOpenPerson,
  onNodeAction,
}: TreeCanvasProps) {
  const { t } = useTranslation();
  const hintId = useId();

  const frameRef = useRef<HTMLDivElement>(null);
  const surfaceRef = useRef<SVGSVGElement>(null);
  const dragRef = useRef<Drag | null>(null);
  const frameRef2 = useRef<number | null>(null);
  const pendingRef = useRef<{ x: number; y: number } | null>(null);

  const [size, setSize] = useState({ width: 0, height: 0 });
  const [panning, setPanning] = useState(false);
  const [announcement, setAnnouncement] = useState('');

  const scale = useTreeViewStore((state) => state.scale);
  const translate = useTreeViewStore((state) => state.translate);
  const setViewport = useTreeViewStore((state) => state.setViewport);
  const zoomBy = useTreeViewStore((state) => state.zoomBy);
  const collapsed = useTreeViewStore((state) => state.collapsed);
  const toggleCollapsed = useTreeViewStore((state) => state.toggleCollapsed);
  const focusedSlotKey = useTreeViewStore((state) => state.focusedSlotKey);
  const focusSlot = useTreeViewStore((state) => state.focusSlot);
  const highlightBranchId = useTreeViewStore((state) => state.highlightBranchId);

  // --- the frame's size, which every fit and every viewport calculation needs -------------------

  useEffect(() => {
    const frame = frameRef.current;
    if (frame === null) return;
    const observer = new ResizeObserver(([entry]) => {
      if (entry === undefined) return;
      setSize({ width: entry.contentRect.width, height: entry.contentRect.height });
    });
    observer.observe(frame);
    return () => {
      observer.disconnect();
    };
  }, []);

  /** The part of canvas space currently on screen — the input to virtualization and the minimap. */
  const viewport = useMemo<TreeBounds>(
    () => ({
      minX: -translate.x / scale,
      minY: -translate.y / scale,
      maxX: (size.width - translate.x) / scale,
      maxY: (size.height - translate.y) / scale,
    }),
    [translate, scale, size],
  );

  const margin = OVERSCAN / scale;

  const visibleSlots = useMemo(
    () => layout.slots.filter((slot) => intersects(boxOf(slot), viewport, margin)),
    [layout.slots, viewport, margin],
  );

  const visibleSlotKeys = useMemo(
    () => new Set(visibleSlots.map((slot) => slot.key)),
    [visibleSlots],
  );

  // --- moving the viewport ---------------------------------------------------------------------

  const centreOn = useCallback(
    (point: { x: number; y: number }, nextScale?: number) => {
      const effective = nextScale ?? scale;
      setViewport({
        scale: effective,
        translate: {
          x: size.width / 2 - point.x * effective,
          y: size.height / 2 - point.y * effective,
        },
      });
    },
    [scale, size, setViewport],
  );

  const fit = useCallback(() => {
    if (size.width === 0 || layout.slots.length === 0) return;
    const width = Math.max(1, layout.bounds.maxX - layout.bounds.minX);
    const height = Math.max(1, layout.bounds.maxY - layout.bounds.minY);
    const next = Math.min(
      ZOOM.max,
      Math.max(
        ZOOM.min,
        Math.min((size.width - FIT_PADDING * 2) / width, (size.height - FIT_PADDING * 2) / height),
      ),
    );
    centreOn(
      {
        x: (layout.bounds.minX + layout.bounds.maxX) / 2,
        y: (layout.bounds.minY + layout.bounds.maxY) / 2,
      },
      next,
    );
  }, [size, layout, centreOn]);

  const jumpTo = useCallback(
    (personId: string) => {
      const slotKey = layout.slotOfPerson.get(personId);
      const slot = layout.slots.find((candidate) => candidate.key === slotKey);
      if (slot === undefined) return;
      focusSlot(slot.key);
      centreOn({ x: slot.x + slot.width / 2, y: slot.y + slot.height / 2 });
      setAnnouncement(slot.node.anchor.fullName);
    },
    [layout, centreOn, focusSlot],
  );

  useImperativeHandle(controlRef, () => ({ fit, jumpTo }), [fit, jumpTo]);

  // The first useful frame: a tree nobody has positioned yet should arrive fitted, not at the
  // origin with the founding ancestor half off the top-left corner.
  const fittedFor = useRef<string | null>(null);
  useEffect(() => {
    const signature = `${layout.orientation}:${layout.slots.length}:${size.width}x${size.height}`;
    if (size.width === 0 || layout.slots.length === 0 || fittedFor.current === signature) return;
    fittedFor.current = signature;
    fit();
  }, [layout, size, fit]);

  // --- pan and zoom ------------------------------------------------------------------------------

  const commitPan = useCallback(() => {
    frameRef2.current = null;
    const pending = pendingRef.current;
    if (pending === null) return;
    pendingRef.current = null;
    setViewport({ scale, translate: pending });
  }, [scale, setViewport]);

  const onPointerDown = (event: React.PointerEvent<SVGSVGElement>) => {
    if (event.button !== 0) return;
    // Not captured yet, and not a pan yet: this press may still turn out to be a click on a card or
    // one of its actions, and capturing here would take that click away from them (see `Drag`).
    dragRef.current = {
      pointerId: event.pointerId,
      x: event.clientX,
      y: event.clientY,
      originX: event.clientX,
      originY: event.clientY,
      panning: false,
    };
  };

  const onPointerMove = (event: React.PointerEvent<SVGSVGElement>) => {
    const drag = dragRef.current;
    if (drag === null) return;
    // A second pointer — a two-finger gesture, or a stylus arriving mid-drag — is not this drag.
    if (drag.pointerId !== event.pointerId) return;

    if (!drag.panning) {
      // Inside the slop the press is still a candidate click, so nothing moves and nothing is
      // captured. The origin is the anchor rather than the last position: a slow drift across the
      // threshold is a drag, and comparing consecutive events would never notice it.
      if (Math.hypot(event.clientX - drag.originX, event.clientY - drag.originY) < DRAG_SLOP) {
        drag.x = event.clientX;
        drag.y = event.clientY;
        return;
      }
      drag.panning = true;
      event.currentTarget.setPointerCapture(event.pointerId);
      setPanning(true);
    }

    // Accumulate onto the *pending* translate, not the committed one. Several pointer events land
    // between two frames, and reading the store each time would throw away every delta but the last
    // — a fast drag would move the tree a fraction of the distance the finger travelled.
    const base = pendingRef.current ?? translate;
    const next = {
      x: base.x + (event.clientX - drag.x),
      y: base.y + (event.clientY - drag.y),
    };
    drag.x = event.clientX;
    drag.y = event.clientY;
    pendingRef.current = next;
    // One commit per frame however many pointer events arrive (F5.15).
    frameRef2.current ??= window.requestAnimationFrame(commitPan);
  };

  const endPan = (event: React.PointerEvent<SVGSVGElement>) => {
    if (dragRef.current === null) return;
    dragRef.current = null;
    setPanning(false);
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  };

  useEffect(
    () => () => {
      if (frameRef2.current !== null) window.cancelAnimationFrame(frameRef2.current);
    },
    [],
  );

  // React registers wheel handlers passively, so `preventDefault` inside `onWheel` does nothing and
  // the page scrolls behind the tree. This is the listener that can actually stop it.
  useEffect(() => {
    const surface = surfaceRef.current;
    if (surface === null) return;
    const onWheel = (event: WheelEvent) => {
      event.preventDefault();
      const box = surface.getBoundingClientRect();
      zoomBy(Math.exp(-event.deltaY * 0.0015), {
        x: event.clientX - box.left,
        y: event.clientY - box.top,
      });
    };
    surface.addEventListener('wheel', onWheel, { passive: false });
    return () => {
      surface.removeEventListener('wheel', onWheel);
    };
  }, [zoomBy]);

  // --- keyboard navigation (F5.8) ----------------------------------------------------------------

  const nav = useMemo(() => navigation(layout.slots), [layout.slots]);

  const moveFocus = useCallback(
    (to: string | undefined) => {
      if (to === undefined) return;
      jumpTo(nav.anchorOf.get(to) ?? to);
    },
    [nav, jumpTo],
  );

  const onKeyDown = (event: React.KeyboardEvent<SVGSVGElement>) => {
    const current = focusedSlotKey ?? layout.slots[0]?.key;
    if (current === undefined) return;

    const siblings = nav.siblingsOf.get(current) ?? [];
    const index = siblings.indexOf(current);

    switch (event.key) {
      case 'ArrowLeft':
        event.preventDefault();
        moveFocus(siblings[index - 1]);
        return;
      case 'ArrowRight':
        event.preventDefault();
        moveFocus(siblings[index + 1]);
        return;
      case 'ArrowUp':
        event.preventDefault();
        moveFocus(nav.parentOf.get(current));
        return;
      case 'ArrowDown': {
        event.preventDefault();
        const children = nav.childrenOf.get(current) ?? [];
        moveFocus(children[0]);
        return;
      }
      case 'Home':
        event.preventDefault();
        moveFocus(layout.slots[0]?.key);
        return;
      case 'Enter': {
        event.preventDefault();
        const slot = layout.slots.find((candidate) => candidate.key === current);
        if (slot !== undefined) onOpenPerson(slot.node.anchor.id);
        return;
      }
      case ' ':
        event.preventDefault();
        if ((nav.childrenOf.get(current) ?? []).length > 0) toggleCollapsed(current);
        return;
      case '+':
        event.preventDefault();
        zoomBy(ZOOM.step);
        return;
      case '-':
        event.preventDefault();
        zoomBy(1 / ZOOM.step);
        return;
      default:
    }
  };

  // --- render -------------------------------------------------------------------------------------

  const dimmedSlot = (slot: LaidOutSlot) =>
    highlightBranchId !== null && slot.node.anchor.branchId !== highlightBranchId;

  return (
    <CanvasFrame ref={frameRef}>
      <CanvasSurface
        ref={surfaceRef}
        $panning={panning}
        tabIndex={0}
        role="application"
        aria-label={t('tree.canvas.label')}
        aria-describedby={hintId}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={endPan}
        onPointerCancel={endPan}
        onKeyDown={onKeyDown}
      >
        <desc id={hintId}>{t('tree.canvas.hint')}</desc>

        <g transform={`translate(${translate.x}, ${translate.y}) scale(${scale})`}>
          {layout.rails.map((rail) => {
            const from = layout.bounds.minX - 24;
            const to = layout.bounds.maxX + 24;
            return layout.orientation === 'vertical' ? (
              <g key={rail.depth}>
                <RailLine x1={from} y1={rail.position - 22} x2={to} y2={rail.position - 22} />
                <RailLabel x={from} y={rail.position - 28}>
                  {railLabel(rail.generation, t)}
                </RailLabel>
              </g>
            ) : (
              <g key={rail.depth}>
                <RailLine
                  x1={rail.position - 22}
                  y1={layout.bounds.minY - 24}
                  x2={rail.position - 22}
                  y2={layout.bounds.maxY + 24}
                />
                <RailLabel x={rail.position - 18} y={layout.bounds.minY - 30}>
                  {railLabel(rail.generation, t)}
                </RailLabel>
              </g>
            );
          })}

          {layout.edges.map((edge) =>
            visibleSlotKeys.has(edge.childSlotKey) ? (
              <ParentEdgePath
                key={edge.key}
                d={edge.path}
                $dashed={edge.nature !== 'BIOLOGICAL'}
                $dimmed={highlightBranchId !== null}
              />
            ) : null,
          )}

          {layout.unions.map((union) => (
            <UnionLine
              key={union.key}
              x1={union.x1}
              y1={union.y1}
              x2={union.x2}
              y2={union.y2}
              $secondary={union.unionType !== 'PRIMARY'}
              $dimmed={highlightBranchId !== null}
            />
          ))}

          {visibleSlots.map((slot) => (
            <g key={slot.key}>
              {slot.cards.map((card) => (
                <PersonNode
                  key={card.personId}
                  card={card}
                  dimmed={dimmedSlot(slot)}
                  selected={focusedSlotKey === slot.key}
                  {...branchNameProp(graph.branchNames, card.person.branchId)}
                  branchHighlighted={
                    highlightBranchId !== null && card.person.branchId === highlightBranchId
                  }
                  isSelf={card.personId === myPersonId}
                  onOpen={(personId) => {
                    focusSlot(slot.key);
                    onOpenPerson(personId);
                  }}
                  onAction={onNodeAction}
                />
              ))}

              {slot.node.children.length > 0 && (
                <CollapseToggle
                  transform={toggleAt(slot, layout.orientation)}
                  role="button"
                  tabIndex={-1}
                  aria-expanded={!collapsed.has(slot.key)}
                  aria-label={t(
                    collapsed.has(slot.key) ? 'tree.canvas.expand' : 'tree.canvas.collapse',
                    { name: slot.node.anchor.fullName },
                  )}
                  onClick={(event) => {
                    event.stopPropagation();
                    toggleCollapsed(slot.key);
                  }}
                  onKeyDown={(event) => {
                    if (event.key !== 'Enter' && event.key !== ' ') return;
                    event.preventDefault();
                    toggleCollapsed(slot.key);
                  }}
                >
                  <title>
                    {t(collapsed.has(slot.key) ? 'tree.canvas.expand' : 'tree.canvas.collapse', {
                      name: slot.node.anchor.fullName,
                    })}
                  </title>
                  <circle r={9} />
                  <path d={collapsed.has(slot.key) ? 'M -4 0 H 4 M 0 -4 V 4' : 'M -4 0 H 4'} />
                  {collapsed.has(slot.key) && (
                    <CollapsedCount y={22}>{slot.node.children.length}</CollapsedCount>
                  )}
                </CollapseToggle>
              )}
            </g>
          ))}
        </g>
      </CanvasSurface>

      {layout.slots.length >= MINIMAP_THRESHOLD && (
        <MiniMap
          layout={layout}
          viewport={viewport}
          highlightBranchId={highlightBranchId}
          onJumpTo={(point) => {
            centreOn(point);
          }}
        />
      )}

      <VisuallyHidden aria-live="polite">{announcement}</VisuallyHidden>
    </CanvasFrame>
  );
}

/**
 * `branchName` only when there is one.
 *
 * `exactOptionalPropertyTypes` is on, so an optional prop is either absent or a string — passing an
 * explicit `undefined` is a different thing and does not compile. Worth the ceremony: "unplaced" and
 * "placed in a branch we failed to look up" are different states, and the card renders them
 * differently.
 */
function branchNameProp(
  names: Map<string, string>,
  branchId: string | undefined,
): { branchName?: string } {
  if (branchId === undefined) return {};
  const name = names.get(branchId);
  return name === undefined ? {} : { branchName: name };
}

/** Where the collapse handle sits: under the slot when vertical, to its right when horizontal. */
function toggleAt(slot: LaidOutSlot, orientation: 'vertical' | 'horizontal'): string {
  const anchor = slot.cards[0];
  if (anchor === undefined) return `translate(${slot.x}, ${slot.y})`;
  return orientation === 'vertical'
    ? `translate(${anchor.x + anchor.width / 2}, ${slot.y + slot.height + 12})`
    : `translate(${slot.x + slot.width + 12}, ${anchor.y + anchor.height / 2})`;
}

function railLabel(generation: number | undefined, t: TFunction): string {
  return generation === undefined
    ? t('tree.generation.unknown')
    : t('tree.generation.value', { generation });
}

/**
 * The relations the arrow keys walk.
 *
 * Built from the laid-out slots rather than from the graph, because what the keyboard should follow
 * is what the eye follows: siblings in the order they are drawn, not the order they were recorded.
 * `anchorOf` maps a slot back to the person the canvas will centre on, which is the anchor.
 */
function navigation(slots: LaidOutSlot[]) {
  const parentOf = new Map<string, string>();
  const childrenOf = new Map<string, string[]>();
  const anchorOf = new Map<string, string>();

  const byKey = new Map(slots.map((slot) => [slot.key, slot]));
  for (const slot of slots) {
    anchorOf.set(slot.key, slot.node.anchor.id);
    const children = slot.node.children
      .map((child) => byKey.get(child.key))
      .filter((child): child is LaidOutSlot => child !== undefined)
      .sort((left, right) => alongOf(left) - alongOf(right))
      .map((child) => child.key);
    childrenOf.set(slot.key, children);
    for (const child of children) parentOf.set(child, slot.key);
  }

  const roots = slots
    .filter((slot) => !parentOf.has(slot.key))
    .sort((left, right) => alongOf(left) - alongOf(right))
    .map((slot) => slot.key);

  const siblingsOf = new Map<string, string[]>();
  for (const slot of slots) {
    const parent = parentOf.get(slot.key);
    siblingsOf.set(slot.key, parent === undefined ? roots : (childrenOf.get(parent) ?? []));
  }

  return { parentOf, childrenOf, siblingsOf, anchorOf };
}

/**
 * Position along the sibling axis, whichever axis that currently is.
 *
 * Siblings share a depth, so the depth component is a constant across every comparison this makes
 * and adding the two coordinates orders them correctly in both orientations without the function
 * having to know which one is in play.
 */
function alongOf(slot: LaidOutSlot): number {
  return slot.x + slot.y;
}
