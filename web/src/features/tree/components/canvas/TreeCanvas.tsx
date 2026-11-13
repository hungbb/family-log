import { type TFunction } from 'i18next';
import {
  type RefObject,
  useCallback,
  useEffect,
  useId,
  useImperativeHandle,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { useTranslation } from 'react-i18next';

import { type PersonSummary } from '@/api/endpoints/persons.ts';

import { type TreeGraph } from '../../model/buildGraph';
import { estimateTextWidth } from '../../model/fitText';
import { formatGeneration } from '../../model/format';
import {
  boxOf,
  intersects,
  type LaidOutSlot,
  type TreeBounds,
  type TreeLayout,
} from '../../model/layout';
import {
  DURATION,
  easeOutCubic,
  MAX_ANIMATED_SLOTS,
  prefersReducedMotion,
  transitionBetween,
  viewportBetween,
  type LayoutTransition,
  type Viewport,
} from '../../model/motion';
import { ZOOM, useTreeViewStore } from '../../stores/useTreeViewStore';

import { Legend } from './Legend';
import { MiniMap, MINIMAP_THRESHOLD } from './MiniMap';
import { type NodeAction } from './PersonNode';
import { CanvasFrame, CanvasSurface, RailLabel, RailLabelPill, VisuallyHidden } from './styles';
import { TreeScene } from './TreeScene';

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
 *
 * ## Motion (#61)
 *
 * Panning and wheel zoom are never eased — the tree follows the hand. Discrete changes are: a
 * collapse or expand glides the cards from where they were to where they now belong for about a
 * quarter of a second, children growing out of (or folding into) the handle that was pressed; and
 * jump-to-person and fit move the camera rather than cut to it. Both are skipped under
 * `prefers-reduced-motion`, and the layout glide is skipped when too much is on screen to move
 * smoothly (see `model/motion.ts`). The drawing itself is `TreeScene`, shared with the print sheet.
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
  /** The person a deep link named (#48): the first view centres on them instead of fitting. */
  focusPersonId?: string;
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
  focusPersonId,
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
  const scheme = useTreeViewStore((state) => state.scheme);
  const legendOpen = useTreeViewStore((state) => state.legendOpen);
  const setLegendOpen = useTreeViewStore((state) => state.setLegendOpen);

  // --- the camera (#61) --------------------------------------------------------------------------

  /** The camera move in flight, if any. Any direct manipulation cancels it. */
  const cameraRef = useRef<number | null>(null);
  const stopCamera = useCallback(() => {
    if (cameraRef.current !== null) window.cancelAnimationFrame(cameraRef.current);
    cameraRef.current = null;
  }, []);

  // --- the frame's size, which every fit and every viewport calculation needs -------------------

  // A resize — the window, or entering and leaving fullscreen — keeps whatever was in the middle of
  // the frame in the middle of it. Re-fitting instead would throw away the zoom somebody chose.
  const lastSizeRef = useRef({ width: 0, height: 0 });
  useEffect(() => {
    const frame = frameRef.current;
    if (frame === null) return;
    const observer = new ResizeObserver(([entry]) => {
      if (entry === undefined) return;
      const next = { width: entry.contentRect.width, height: entry.contentRect.height };
      const last = lastSizeRef.current;
      lastSizeRef.current = next;
      if (last.width > 0 && last.height > 0) {
        if (cameraRef.current !== null) window.cancelAnimationFrame(cameraRef.current);
        cameraRef.current = null;
        const view = useTreeViewStore.getState();
        view.setViewport({
          scale: view.scale,
          translate: {
            x: view.translate.x + (next.width - last.width) / 2,
            y: view.translate.y + (next.height - last.height) / 2,
          },
        });
      }
      setSize(next);
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

  // --- moving the viewport ---------------------------------------------------------------------

  /** Moves the camera to `target`, easing unless the person asked for less motion. */
  const moveCamera = useCallback(
    (target: Viewport) => {
      stopCamera();
      const view = useTreeViewStore.getState();
      const from: Viewport = { scale: view.scale, translate: view.translate };
      if (prefersReducedMotion() || size.width === 0) {
        setViewport(target);
        return;
      }
      const frame = { width: size.width, height: size.height };
      const start = performance.now();
      const tick = (now: number) => {
        const progress = (now - start) / DURATION.camera;
        setViewport(viewportBetween(from, target, frame, easeOutCubic(progress)));
        cameraRef.current = progress < 1 ? window.requestAnimationFrame(tick) : null;
      };
      cameraRef.current = window.requestAnimationFrame(tick);
    },
    [size, setViewport, stopCamera],
  );

  const centreOn = useCallback(
    (point: { x: number; y: number }, nextScale?: number, animate = false) => {
      const effective = nextScale ?? useTreeViewStore.getState().scale;
      const target = {
        scale: effective,
        translate: {
          x: size.width / 2 - point.x * effective,
          y: size.height / 2 - point.y * effective,
        },
      };
      if (animate) {
        moveCamera(target);
      } else {
        stopCamera();
        setViewport(target);
      }
    },
    [size, setViewport, moveCamera, stopCamera],
  );

  const fitTo = useCallback(
    (animate: boolean) => {
      if (size.width === 0 || layout.slots.length === 0) return;
      const width = Math.max(1, layout.bounds.maxX - layout.bounds.minX);
      const height = Math.max(1, layout.bounds.maxY - layout.bounds.minY);
      const next = Math.min(
        ZOOM.max,
        Math.max(
          ZOOM.min,
          Math.min(
            (size.width - FIT_PADDING * 2) / width,
            (size.height - FIT_PADDING * 2) / height,
          ),
        ),
      );
      centreOn(
        {
          x: (layout.bounds.minX + layout.bounds.maxX) / 2,
          y: (layout.bounds.minY + layout.bounds.maxY) / 2,
        },
        next,
        animate,
      );
    },
    [size, layout, centreOn],
  );

  const fit = useCallback(() => {
    fitTo(true);
  }, [fitTo]);

  /**
   * Centres on a person and selects their slot, returning it — or null if they are not drawn.
   *
   * Separate from {@link jumpTo} because this half touches only the view store, and so may be
   * called from an effect; `setAnnouncement` is React state, and `react-hooks/set-state-in-effect`
   * rightly forbids that. Nobody is drawn when they are unplaced or inside a collapsed branch, and
   * the null lets the caller decide what to do instead.
   */
  const centreOnPerson = useCallback(
    (personId: string, animate: boolean): LaidOutSlot | null => {
      const slotKey = layout.slotOfPerson.get(personId);
      const slot = layout.slots.find((candidate) => candidate.key === slotKey);
      if (slot === undefined) return null;
      focusSlot(slot.key);
      centreOn({ x: slot.x + slot.width / 2, y: slot.y + slot.height / 2 }, undefined, animate);
      return slot;
    },
    [layout, centreOn, focusSlot],
  );

  const jumpTo = useCallback(
    (personId: string) => {
      const slot = centreOnPerson(personId, true);
      if (slot !== null) setAnnouncement(slot.node.anchor.fullName);
    },
    [centreOnPerson],
  );

  useImperativeHandle(controlRef, () => ({ fit, jumpTo }), [fit, jumpTo]);

  // The first useful frame: a tree nobody has positioned yet should arrive fitted, not at the
  // origin with the founding ancestor half off the top-left corner.
  //
  // A deep link (#48) names where that first frame belongs instead: `?person=` arrives centred on
  // that person, which is the point of the URL — a fitted five-thousand-person clan does not show
  // anybody the relative they were sent to look at. Only the first frame: the signature guard makes
  // this run once per layout and size, and opening a card later changes neither, so clicking around
  // the tree never yanks the viewport out from under the pointer. A person outside the layout —
  // unplaced, or inside a collapsed branch — falls back to fitting rather than to the empty origin.
  const positionedFor = useRef<string | null>(null);
  useEffect(() => {
    // Keyed on the orientation and the tree, never on the slot count or the frame size: collapsing a
    // branch changes the first and fullscreen changes the second, and neither should re-fit (#60).
    const signature = `${layout.orientation}:${layout.slots[0]?.key ?? ''}`;
    if (size.width === 0 || layout.slots.length === 0 || positionedFor.current === signature) {
      return;
    }
    positionedFor.current = signature;
    // The first frame is placed, not flown to: there is nothing on screen yet to move from.
    if (focusPersonId !== undefined && centreOnPerson(focusPersonId, false) !== null) return;
    fitTo(false);
  }, [layout, size, fitTo, centreOnPerson, focusPersonId]);

  // --- collapsing without losing your place -------------------------------------------------------

  /**
   * Collapses or expands a slot, keeping that slot exactly where it was on screen.
   *
   * The tidy layout re-centres every parent over its remaining children, so without this the node
   * somebody just clicked jumps sideways — on a wide clan, often off the screen entirely.
   */
  const pinnedRef = useRef<{ key: string; x: number; y: number } | null>(null);
  /**
   * The same slot, for the glide: which layout it was toggled in. Kept as state rather than read
   * from the ref because the transition is derived during render, where refs are off limits; tying
   * it to the layout it was pressed in means it can never be applied to a later, unrelated change.
   */
  const [pin, setPin] = useState<{ key: string; before: TreeLayout } | null>(null);
  const toggleAnchored = useCallback(
    (slotKey: string) => {
      stopCamera();
      const slot = layout.slots.find((candidate) => candidate.key === slotKey);
      if (slot !== undefined) pinnedRef.current = { key: slotKey, x: slot.x, y: slot.y };
      setPin({ key: slotKey, before: layout });
      toggleCollapsed(slotKey);
    },
    [layout, toggleCollapsed, stopCamera],
  );

  useLayoutEffect(() => {
    const pinned = pinnedRef.current;
    if (pinned === null) return;
    pinnedRef.current = null;
    const slot = layout.slots.find((candidate) => candidate.key === pinned.key);
    if (slot === undefined) return;
    const view = useTreeViewStore.getState();
    view.setViewport({
      scale: view.scale,
      translate: {
        x: view.translate.x - (slot.x - pinned.x) * view.scale,
        y: view.translate.y - (slot.y - pinned.y) * view.scale,
      },
    });
  }, [layout]);

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
    stopCamera();
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
      stopCamera();
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
  }, [zoomBy, stopCamera]);

  useEffect(() => stopCamera, [stopCamera]);

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
        if ((nav.childrenOf.get(current) ?? []).length > 0 || collapsed.has(current)) {
          toggleAnchored(current);
        }
        return;
      case '+':
        event.preventDefault();
        stopCamera();
        zoomBy(ZOOM.step);
        return;
      case '-':
        event.preventDefault();
        stopCamera();
        zoomBy(1 / ZOOM.step);
        return;
      default:
    }
  };

  // --- the layout glide (#61) ------------------------------------------------------------------

  /**
   * The layout on screen and the change being animated into it.
   *
   * Derived during render — React's "storing information from previous renders" pattern — so the
   * first frame of a new layout is already drawn at the old positions. Detecting the change in an
   * effect instead would paint one frame of the final layout before the glide began: a flash of the
   * very jump this exists to remove.
   */
  const [shown, setShown] = useState<{
    layout: TreeLayout;
    transition: LayoutTransition | null;
    progress: number;
    connectorKey: number;
  }>({ layout, transition: null, progress: 1, connectorKey: 0 });
  if (shown.layout !== layout) {
    const pinKey = pin !== null && pin.before === shown.layout ? pin.key : null;
    const id = (shown.transition?.id ?? shown.connectorKey) + 1;
    const transition =
      prefersReducedMotion() || visibleSlots.length > MAX_ANIMATED_SLOTS
        ? null
        : transitionBetween(shown.layout, layout, pinKey, id);
    setShown({ layout, transition, progress: 0, connectorKey: shown.connectorKey });
  }
  const transition = shown.layout === layout ? shown.transition : null;

  useEffect(() => {
    const running = shown.transition;
    if (running === null) return;
    const start = performance.now();
    let frame = window.requestAnimationFrame(function tick(now) {
      const progress = Math.min(1, (now - start) / DURATION.layout);
      setShown((current) =>
        current.transition !== running
          ? current
          : progress < 1
            ? { ...current, progress }
            : { ...current, transition: null, progress: 1, connectorKey: running.id },
      );
      if (progress < 1) frame = window.requestAnimationFrame(tick);
    });
    return () => {
      window.cancelAnimationFrame(frame);
    };
  }, [shown.transition]);

  // --- render -------------------------------------------------------------------------------------

  /** Stable, so the memoized cards keep their memo across pans (F5.15). */
  const openCard = useCallback(
    (personId: string) => {
      const slotKey = layout.slotOfPerson.get(personId);
      if (slotKey !== undefined) focusSlot(slotKey);
      onOpenPerson(personId);
    },
    [layout.slotOfPerson, focusSlot, onOpenPerson],
  );

  const interaction = useMemo(
    () => ({
      focusedSlotKey,
      collapsed,
      onOpen: openCard,
      onAction: onNodeAction,
      onToggle: toggleAnchored,
    }),
    [focusedSlotKey, collapsed, openCard, onNodeAction, toggleAnchored],
  );

  const selfSlotKey = myPersonId === undefined ? undefined : layout.slotOfPerson.get(myPersonId);

  return (
    <CanvasFrame ref={frameRef} $scheme={scheme}>
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
          <TreeScene
            layout={layout}
            slots={visibleSlots}
            window={viewport}
            margin={margin}
            scale={scale}
            branchNames={graph.branchNames}
            highlightBranchId={highlightBranchId}
            {...(myPersonId === undefined ? {} : { myPersonId })}
            transition={transition}
            progress={easeOutCubic(shown.progress)}
            connectorKey={shown.connectorKey}
            interaction={interaction}
          />
        </g>

        <RailLabels layout={layout} scale={scale} translate={translate} size={size} t={t} />
      </CanvasSurface>

      <Legend open={legendOpen} onToggle={setLegendOpen} showSelf={myPersonId !== undefined} />

      {layout.slots.length >= MINIMAP_THRESHOLD && (
        <MiniMap
          layout={layout}
          viewport={viewport}
          highlightBranchId={highlightBranchId}
          {...(selfSlotKey === undefined ? {} : { selfSlotKey })}
          onJumpTo={(point) => {
            centreOn(point);
          }}
        />
      )}

      <VisuallyHidden aria-live="polite">{announcement}</VisuallyHidden>
    </CanvasFrame>
  );
}

/** The font size of `RailLabel`, and the pill's padding around it. */
const RAIL_LABEL = { fontSize: 12, padX: 9, height: 20, inset: 10 } as const;

/**
 * The *đời* labels, in screen space (#61).
 *
 * Drawn outside the zoomed group so they are the same legible size at every zoom, and pinned to the
 * frame's leading edge — the left when generations run down, the top when they run across — so the
 * reader always knows which generation they are looking at, however far they have panned. The rail
 * *lines* stay in canvas space with the tree.
 */
function RailLabels({
  layout,
  scale,
  translate,
  size,
  t,
}: {
  layout: TreeLayout;
  scale: number;
  translate: { x: number; y: number };
  size: { width: number; height: number };
  t: TFunction;
}) {
  const vertical = layout.orientation === 'vertical';
  const extent = vertical ? size.height : size.width;
  // Zoomed far out the rails crowd together; a label is drawn only where it has room, so the
  // ones shown never overlap. The first rail always gets one.
  let lastEnd = -Infinity;
  const labels = [];
  for (const rail of layout.rails) {
    const at = (rail.position - 22) * scale + (vertical ? translate.y : translate.x);
    if (at < -RAIL_LABEL.height || at > extent + RAIL_LABEL.height) continue;
    const label = formatGeneration(rail.generation, t);
    const width = estimateTextWidth(label, RAIL_LABEL.fontSize) * 1.1 + RAIL_LABEL.padX * 2;
    const start = vertical ? at - RAIL_LABEL.height / 2 : at - width / 2;
    const span = vertical ? RAIL_LABEL.height : width;
    if (start < lastEnd + 4) continue;
    lastEnd = start + span;
    const x = vertical ? RAIL_LABEL.inset : start;
    const y = vertical ? start : RAIL_LABEL.inset;
    labels.push(
      <g key={rail.depth} transform={`translate(${x}, ${y})`}>
        <RailLabelPill width={width} height={RAIL_LABEL.height} rx={RAIL_LABEL.height / 2} />
        <RailLabel x={RAIL_LABEL.padX} y={RAIL_LABEL.height / 2 + RAIL_LABEL.fontSize * 0.36}>
          {label}
        </RailLabel>
      </g>,
    );
  }
  return <g aria-hidden="true">{labels}</g>;
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
