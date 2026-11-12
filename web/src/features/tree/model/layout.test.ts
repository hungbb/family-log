import { describe, expect, it } from 'vitest';

import { type PersonSummary } from '@/api/endpoints/persons.ts';

import { type CoupleNode } from './buildGraph';
import { collapsedBeyond, levelCount } from './depth';
import { layoutTree, type TreeLayout } from './layout';

function node(
  key: string,
  children: CoupleNode[] = [],
  viaFamilyKey = 'parent-family',
): CoupleNode {
  return {
    key,
    anchor: { id: key, fullName: key, generation: 1 } as PersonSummary,
    families:
      children.length === 0 ? [] : [{ key: `${key}-family`, childIds: children.map((c) => c.key) }],
    children: children.map((child) => ({ ...child, viaFamilyKey: `${key}-family` })),
    viaFamilyKey,
  };
}

/** A founder with a big family: two sons with children of their own, and nine childless ones. */
function bigFamily(): CoupleNode[] {
  const grandchildren = (prefix: string) => [node(`${prefix}-a`), node(`${prefix}-b`)];
  const sons = [
    node('son-1', grandchildren('son-1')),
    ...Array.from({ length: 9 }, (_, index) => node(`leaf-${String(index)}`)),
    node('son-2', grandchildren('son-2')),
  ];
  const { viaFamilyKey: _root, ...founder } = node('founder', sons);
  return [founder];
}

function overlaps(layout: TreeLayout): number {
  const cards = layout.slots.flatMap((slot) => slot.cards);
  let count = 0;
  for (let i = 0; i < cards.length; i += 1) {
    for (let j = i + 1; j < cards.length; j += 1) {
      const a = cards[i];
      const b = cards[j];
      if (a === undefined || b === undefined) continue;
      if (
        a.x < b.x + b.width &&
        b.x < a.x + a.width &&
        a.y < b.y + b.height &&
        b.y < a.y + a.height
      ) {
        count += 1;
      }
    }
  }
  return count;
}

describe('layoutTree stacking', () => {
  for (const orientation of ['vertical', 'horizontal'] as const) {
    it(`stacks a large family's childless children (${orientation})`, () => {
      const roots = bigFamily();
      const row = layoutTree(roots, { orientation, collapsed: new Set(), stacking: false });
      const stacked = layoutTree(roots, { orientation, collapsed: new Set() });

      const along = (layout: TreeLayout) =>
        orientation === 'vertical'
          ? layout.bounds.maxX - layout.bounds.minX
          : layout.bounds.maxY - layout.bounds.minY;

      expect(stacked.stacks).toHaveLength(1);
      expect(along(stacked)).toBeLessThan(along(row) * 0.75);
      expect(overlaps(stacked)).toBe(0);
      // Everybody is still drawn, and every child still has its connector.
      expect(stacked.slots).toHaveLength(row.slots.length);
      expect(stacked.edges).toHaveLength(row.edges.length);
    });
  }

  it('keeps no other card inside a stack', () => {
    const layout = layoutTree(bigFamily(), { orientation: 'vertical', collapsed: new Set() });
    const panel = layout.stacks[0];
    if (panel === undefined) throw new Error('expected a stack');
    const intruders = layout.slots
      .filter((slot) => !slot.key.startsWith('leaf-'))
      .filter(
        (slot) =>
          slot.x < panel.x + panel.width &&
          panel.x < slot.x + slot.width &&
          slot.y < panel.y + panel.height &&
          panel.y < slot.y + slot.height,
      );
    expect(intruders).toEqual([]);
  });

  it('leaves a small family in its row', () => {
    const { viaFamilyKey: _root, ...founder } = node('founder', [node('a'), node('b'), node('c')]);
    expect(layoutTree([founder], { orientation: 'vertical', collapsed: new Set() }).stacks).toEqual(
      [],
    );
  });
});

describe('generation depth', () => {
  it('counts levels and collapses exactly below the limit', () => {
    const roots = bigFamily();

    expect(levelCount(roots)).toBe(3);
    expect(collapsedBeyond(roots, 1)).toEqual(new Set(['founder']));
    expect(collapsedBeyond(roots, 2)).toEqual(new Set(['son-1', 'son-2']));
  });
});
