import { describe, expect, it } from 'vitest';

import { PRINT, planPrint } from './print';

const box = (width: number, height: number) => ({ minX: 0, minY: 0, maxX: width, maxY: height });

describe('planPrint', () => {
  it('prints a broad clan landscape with descent running down the page', () => {
    // Four times wider than deep when vertical; the same tree turned sideways is four times deeper.
    const plan = planPrint({ vertical: box(4000, 1000), horizontal: box(1000, 4000) }, 'A4', 'fit');
    expect(plan.orientation).toBe('vertical');
    expect(plan.paper).toBe('landscape');
    expect(plan.tiles).toHaveLength(1);
  });

  it('turns a deep, narrow line to whichever shape prints it largest', () => {
    const plan = planPrint({ vertical: box(600, 3000), horizontal: box(3000, 600) }, 'A4', 'fit');
    // 3000 units deep fits a landscape page better sideways than a portrait page standing up.
    expect(plan.scale).toBeGreaterThan(
      planPrint({ vertical: box(600, 3000) }, 'A4', 'fit').scale - 1e-9,
    );
    expect(plan.tiles).toHaveLength(1);
  });

  it('never blows a small tree up past the maximum scale', () => {
    const plan = planPrint({ vertical: box(300, 100) }, 'A3', 'fit');
    expect(plan.scale).toBe(PRINT.max);
    expect(plan.tooSmall).toBe(false);
  });

  it('warns when fitting a huge clan on one page would make the names unreadable', () => {
    const plan = planPrint({ vertical: box(40_000, 3000) }, 'A4', 'fit');
    expect(plan.tiles).toHaveLength(1);
    expect(plan.tooSmall).toBe(true);
  });

  it('tiles a huge clan at a legible scale, with every part of the tree on some page', () => {
    const tree = box(8000, 1500);
    const plan = planPrint({ vertical: tree }, 'A4', 'pages');
    expect(plan.scale).toBe(PRINT.readable);
    expect(plan.tiles.length).toBe(plan.columns * plan.rows);
    expect(plan.tiles.length).toBeGreaterThan(1);

    const minX = Math.min(...plan.tiles.map((tile) => tile.minX));
    const maxX = Math.max(...plan.tiles.map((tile) => tile.maxX));
    const minY = Math.min(...plan.tiles.map((tile) => tile.minY));
    const maxY = Math.max(...plan.tiles.map((tile) => tile.maxY));
    expect(minX).toBeLessThanOrEqual(tree.minX - PRINT.padding);
    expect(maxX).toBeGreaterThanOrEqual(tree.maxX + PRINT.padding);
    expect(minY).toBeLessThanOrEqual(tree.minY - PRINT.padding);
    expect(maxY).toBeGreaterThanOrEqual(tree.maxY + PRINT.padding);
  });

  it('overlaps neighbouring tiles so a cut connector can be followed across', () => {
    const plan = planPrint({ vertical: box(8000, 600) }, 'A4', 'pages');
    const [first, second] = plan.tiles;
    expect(first).toBeDefined();
    expect(second).toBeDefined();
    if (first === undefined || second === undefined) return;
    expect(first.maxX - second.minX).toBeCloseTo(PRINT.overlap / plan.scale, 5);
  });

  it('prints a tree that already fits legibly on a single page even in pages mode', () => {
    const plan = planPrint({ vertical: box(800, 400) }, 'A4', 'pages');
    expect(plan.tiles).toHaveLength(1);
    expect(plan.scale).toBeGreaterThanOrEqual(PRINT.readable);
  });

  it('chooses the arrangement that needs the fewest sheets', () => {
    const both = planPrint({ vertical: box(9000, 900), horizontal: box(900, 9000) }, 'A4', 'pages');
    const onlyVertical = planPrint({ vertical: box(9000, 900) }, 'A4', 'pages');
    expect(both.tiles.length).toBeLessThanOrEqual(onlyVertical.tiles.length);
  });
});
