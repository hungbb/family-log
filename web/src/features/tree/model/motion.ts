import { type LaidOutSlot, type TreeLayout } from './layout';

/**
 * The canvas's motion, as maths (#61).
 *
 * **A little, not a lot.** Motion here exists to answer one question — "where did that go?" — when
 * the layout or the camera changes under somebody. A collapse that jumps loses the reader; one that
 * glides for a quarter of a second does not. Anything longer is decoration, and on a five-thousand
 * person clan decoration costs frames.
 *
 * **Never on the hot path.** Panning and wheel zoom are direct manipulation and are never eased:
 * the tree follows the hand. Only discrete changes animate — collapse/expand, jump-to-person, fit.
 *
 * **Honours `prefers-reduced-motion`.** With it set every duration here is zero; the change still
 * happens, it just happens at once.
 */

export const DURATION = {
  /** A layout change: collapse, expand, "show N generations". */
  layout: 260,
  /** A camera move: jump to a person, fit. */
  camera: 300,
} as const;

/**
 * Above this many slots in view a layout change does not animate. Moving a few hundred groups for
 * a quarter of a second is cheap; moving a few thousand is a dropped frame on a mid-range phone,
 * and at that zoom nobody can follow an individual card anyway.
 */
export const MAX_ANIMATED_SLOTS = 600;

/** The standard ease-out: quick to start, gentle to land — a cubic, so it needs no table. */
export function easeOutCubic(t: number): number {
  const clamped = Math.min(1, Math.max(0, t));
  return 1 - (1 - clamped) ** 3;
}

/** Whether the person has asked the system for less motion. False where there is no `matchMedia`. */
export function prefersReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

export interface Viewport {
  scale: number;
  translate: { x: number; y: number };
}

/**
 * The camera part-way between two viewports.
 *
 * Interpolating `translate` directly makes the tree swing in an arc whenever the scale changes as
 * well, because a translate only means anything at one scale. So this interpolates what the eye
 * tracks instead — the canvas point at the centre of the frame — and the scale geometrically, so a
 * zoom from 0.1 to 1 spends as long on each doubling as a zoom from 1 to 2 would. The translate is
 * then derived from the two.
 */
export function viewportBetween(
  from: Viewport,
  to: Viewport,
  frame: { width: number; height: number },
  progress: number,
): Viewport {
  const e = Math.min(1, Math.max(0, progress));
  const centreOf = (view: Viewport) => ({
    x: (frame.width / 2 - view.translate.x) / view.scale,
    y: (frame.height / 2 - view.translate.y) / view.scale,
  });
  const a = centreOf(from);
  const b = centreOf(to);
  const scale = from.scale * (to.scale / from.scale) ** e;
  const centre = { x: a.x + (b.x - a.x) * e, y: a.y + (b.y - a.y) * e };
  return {
    scale,
    translate: {
      x: frame.width / 2 - centre.x * scale,
      y: frame.height / 2 - centre.y * scale,
    },
  };
}

/**
 * What one layout change has to animate: where each surviving slot was, which slots are leaving,
 * and where new ones grow from.
 */
export interface LayoutTransition {
  /** Bumped per transition, so the connectors' fade-in restarts each time. */
  id: number;
  /** Each surviving slot's previous top-left, by slot key. */
  from: Map<string, { x: number; y: number }>;
  /** Slots in the old layout and not in the new — drawn fading out over the transition. */
  exiting: LaidOutSlot[];
  /**
   * Where entering slots grow from and exiting ones shrink toward: the slot that was toggled, at
   * its new position. Null for a change with no single source — "expand all", a generation limit
   * — where new slots simply fade in where they belong.
   */
  origin: { x: number; y: number } | null;
}

/**
 * The transition from one layout to the next, or null when there is nothing worth animating.
 *
 * Nothing animates across an orientation change or into a different tree (the coordinate spaces
 * are unrelated), nor when the slots did not move at all — a re-layout after an edit elsewhere
 * should not shimmer.
 */
export function transitionBetween(
  previous: TreeLayout,
  next: TreeLayout,
  pinnedKey: string | null,
  id: number,
): LayoutTransition | null {
  if (previous.orientation !== next.orientation) return null;
  if (previous.slots.length === 0 || next.slots.length === 0) return null;
  if (previous.slots[0]?.key !== next.slots[0]?.key) return null;
  if (Math.max(previous.slots.length, next.slots.length) > MAX_ANIMATED_SLOTS * 8) return null;

  const nextKeys = new Set(next.slots.map((slot) => slot.key));
  const from = new Map<string, { x: number; y: number }>();
  let moved = false;
  const nextByKey = new Map(next.slots.map((slot) => [slot.key, slot]));
  for (const slot of previous.slots) {
    const after = nextByKey.get(slot.key);
    if (after === undefined) continue;
    from.set(slot.key, { x: slot.x, y: slot.y });
    if (after.x !== slot.x || after.y !== slot.y) moved = true;
  }
  const exiting = previous.slots.filter((slot) => !nextKeys.has(slot.key));
  const entering = next.slots.length - from.size;
  if (!moved && exiting.length === 0 && entering === 0) return null;

  const pinned = pinnedKey === null ? undefined : nextByKey.get(pinnedKey);
  return {
    id,
    from,
    exiting,
    origin: pinned === undefined ? null : { x: pinned.x, y: pinned.y },
  };
}

/** Where a slot is drawn, and how opaque, at eased progress `e` through a transition. */
export interface SlotFrame {
  dx: number;
  dy: number;
  opacity: number;
}

const AT_REST: SlotFrame = { dx: 0, dy: 0, opacity: 1 };

/** The offset from its final position at which a slot of the new layout is drawn. */
export function enteringFrame(
  slot: { key: string; x: number; y: number },
  transition: LayoutTransition | null,
  e: number,
): SlotFrame {
  if (transition === null || e >= 1) return AT_REST;
  const before = transition.from.get(slot.key);
  if (before !== undefined) {
    return { dx: (before.x - slot.x) * (1 - e), dy: (before.y - slot.y) * (1 - e), opacity: 1 };
  }
  const origin = transition.origin;
  return origin === null
    ? { dx: 0, dy: 0, opacity: e }
    : { dx: (origin.x - slot.x) * (1 - e), dy: (origin.y - slot.y) * (1 - e), opacity: e };
}

/** The offset from its old position at which a slot that is leaving is drawn. */
export function exitingFrame(
  slot: { x: number; y: number },
  transition: LayoutTransition,
  e: number,
): SlotFrame {
  const origin = transition.origin;
  return origin === null
    ? { dx: 0, dy: 0, opacity: 1 - e }
    : { dx: (origin.x - slot.x) * e, dy: (origin.y - slot.y) * e, opacity: 1 - e };
}
