import { describe, expect, it } from 'vitest';

import { type LaidOutSlot, type TreeLayout } from './layout';
import {
  easeOutCubic,
  enteringFrame,
  exitingFrame,
  transitionBetween,
  viewportBetween,
} from './motion';

function slot(key: string, x: number, y: number): LaidOutSlot {
  return { key, x, y } as LaidOutSlot;
}

function layoutOf(slots: LaidOutSlot[], orientation: 'vertical' | 'horizontal' = 'vertical') {
  return { orientation, slots } as TreeLayout;
}

describe('easeOutCubic', () => {
  it('starts at 0, ends at 1, and is clamped outside', () => {
    expect(easeOutCubic(0)).toBe(0);
    expect(easeOutCubic(1)).toBe(1);
    expect(easeOutCubic(-1)).toBe(0);
    expect(easeOutCubic(2)).toBe(1);
    expect(easeOutCubic(0.5)).toBeGreaterThan(0.5);
  });
});

describe('viewportBetween', () => {
  const frame = { width: 800, height: 600 };
  const from = { scale: 1, translate: { x: 0, y: 0 } };
  const to = { scale: 2, translate: { x: -400, y: -300 } };

  it('returns the ends at 0 and 1', () => {
    expect(viewportBetween(from, to, frame, 0)).toEqual(from);
    const end = viewportBetween(from, to, frame, 1);
    expect(end.scale).toBeCloseTo(2);
    expect(end.translate.x).toBeCloseTo(-400);
    expect(end.translate.y).toBeCloseTo(-300);
  });

  it('interpolates the scale geometrically', () => {
    expect(viewportBetween(from, to, frame, 0.5).scale).toBeCloseTo(Math.SQRT2);
  });

  it('moves the centre of the frame in a straight line through canvas space', () => {
    const half = viewportBetween(from, to, frame, 0.5);
    const centre = {
      x: (frame.width / 2 - half.translate.x) / half.scale,
      y: (frame.height / 2 - half.translate.y) / half.scale,
    };
    // From (400, 300) to (400, 300): this zoom is about the centre, so the centre never moves.
    expect(centre.x).toBeCloseTo(400);
    expect(centre.y).toBeCloseTo(300);
  });
});

describe('transitionBetween', () => {
  it('animates nothing across an orientation change', () => {
    const before = layoutOf([slot('a', 0, 0)]);
    const after = layoutOf([slot('a', 10, 0)], 'horizontal');
    expect(transitionBetween(before, after, null, 1)).toBeNull();
  });

  it('animates nothing when nothing moved', () => {
    const before = layoutOf([slot('a', 0, 0), slot('b', 10, 0)]);
    const after = layoutOf([slot('a', 0, 0), slot('b', 10, 0)]);
    expect(transitionBetween(before, after, null, 1)).toBeNull();
  });

  it('remembers where survivors were and which slots are leaving', () => {
    const before = layoutOf([slot('a', 0, 0), slot('b', 100, 0), slot('c', 200, 0)]);
    const after = layoutOf([slot('a', 0, 0), slot('b', 50, 0)]);
    const transition = transitionBetween(before, after, 'b', 7);
    expect(transition).not.toBeNull();
    if (transition === null) return;
    expect(transition.id).toBe(7);
    expect(transition.from.get('b')).toEqual({ x: 100, y: 0 });
    expect(transition.exiting.map((leaving) => leaving.key)).toEqual(['c']);
    expect(transition.origin).toEqual({ x: 50, y: 0 });
  });
});

describe('slot frames', () => {
  const before = layoutOf([slot('a', 0, 0), slot('b', 100, 0)]);
  const after = layoutOf([slot('a', 0, 0), slot('b', 40, 0), slot('new', 40, 200)]);
  const transition = transitionBetween(before, after, 'b', 1);

  it('moves a survivor from its old position to its new one', () => {
    expect(enteringFrame(slot('b', 40, 0), transition, 0)).toEqual({ dx: 60, dy: 0, opacity: 1 });
    expect(enteringFrame(slot('b', 40, 0), transition, 1)).toEqual({ dx: 0, dy: 0, opacity: 1 });
  });

  it('grows a new slot out of the toggled one, fading in', () => {
    const frame = enteringFrame(slot('new', 40, 200), transition, 0);
    expect(frame).toEqual({ dx: 0, dy: -200, opacity: 0 });
  });

  it('draws a leaving slot shrinking toward the toggled one, fading out', () => {
    if (transition === null) throw new Error('expected a transition');
    const frame = exitingFrame(slot('gone', 40, 200), transition, 1);
    expect(frame).toEqual({ dx: 0, dy: -200, opacity: 0 });
  });

  it('is at rest without a transition', () => {
    expect(enteringFrame(slot('a', 0, 0), null, 0)).toEqual({ dx: 0, dy: 0, opacity: 1 });
  });
});
