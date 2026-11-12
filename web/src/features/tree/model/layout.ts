import { hierarchy, tree as tidyTree, type HierarchyPointNode } from 'd3-hierarchy';

import {
  type ParentageNature,
  type PersonSummary,
  type UnionType,
} from '@/api/endpoints/persons.ts';

import { peopleInSlot, slotWidthInCards, type CoupleNode } from './buildGraph';

/**
 * Where every card, connector and rail goes (F5.2).
 *
 * **Layout maths only.** No React, no SVG, no strings a person reads — this file turns a graph into
 * numbers, and the renderer turns numbers into elements. Keeping the two apart is what lets the
 * hard part be reasoned about without a browser, and is why the couple problem below is solvable at
 * all.
 *
 * ## Couples occupy one slot
 *
 * `d3-hierarchy`'s tidy layout ([Reingold–Tilford], as `d3.tree`) assigns one position per node and
 * spaces siblings by a separation function. A marriage is not a node in the descent — it is two or
 * three people standing side by side in the *same* position — so the slot is the unit that gets a
 * position, and the separation function is told how wide each slot actually is. Without that, a man
 * with three wives overlaps his brother, which is the failure mode phase 5 §8 warns about and the
 * reason a stock layout call is not enough.
 *
 * ## Two orientations, one set of maths
 *
 * Everything below is computed in *axis space*: `along` is the axis siblings spread across and
 * `depth` is the axis generations descend. Vertical maps `along → x`, horizontal maps `along → y`,
 * and nothing else in the file knows which is which.
 */

export type Orientation = 'vertical' | 'horizontal';

/** One person's card. Fixed size: a tree whose boxes resize with the name is a tree that jitters. */
export const CARD = { width: 176, height: 72 } as const;

export const GAP = {
  /** Between a person and their spouse, inside one slot. Small — they read as one family. */
  spouse: 14,
  /** Between neighbouring slots. Large enough that two families never read as one. */
  sibling: 36,
  /** Between generations. Carries the connector elbow and the *đời* rail. */
  generation: 84,
} as const;

/** The synthetic parent every root hangs from, so several lines lay out beside each other. */
const FOREST_KEY = '__forest__';

/** Cousins are pushed apart harder than brothers, so a branch reads as a branch. */
const COUSIN_SPREAD = 1.35;

/**
 * Stacking a large family (#60).
 *
 * A couple with a dozen children laid out in one row makes the whole tree as wide as that row, and
 * most of those children are usually leaves — young, unmarried, or simply without recorded
 * descendants. So when one family has at least `STACK_MIN` children with nothing drawn below them,
 * those children are set in a few columns under their parents instead of in the row. Children who
 * have families of their own stay in the row, because their subtrees need the room beneath them.
 *
 * In the layout a stack is one wide node followed by a chain of invisible nodes, one per level it
 * reaches down into, so the tidy layout keeps every other branch out of the space it occupies.
 */
const STACK_MIN = 5;

const STACK = {
  /** Between two rows of a stack: room for a collapse handle and its count under a card. */
  rowGap: 40,
  /** Space before each column for the trunk and the short connector into each card. */
  lead: 18,
  /** How far the panel behind a stack reaches past its cards. */
  inset: 10,
} as const;

interface Stack {
  key: string;
  /** Members by column, top to bottom. */
  columns: CoupleNode[][];
  /** Each column's width along the sibling axis, including its lead. */
  widths: number[];
  /** The whole stack's width along the sibling axis. */
  extent: number;
  /** How many layout levels the stack reaches down into. */
  levels: number;
}

/** A layout node: a real slot, or one level of a stack. */
type Item =
  | { kind: 'slot'; key: string; node: CoupleNode }
  | { kind: 'stack'; key: string; part: number; stack: Stack };

/** Axis-space coordinates of the connector into one stacked child. */
interface StackRoute {
  /** The along position of the column's trunk. */
  trunk: number;
  /** Where the card begins along the sibling axis. */
  enter: number;
  /** The card's middle on the depth axis. */
  middle: number;
  /** Where the stack begins on the depth axis. */
  top: number;
}

/** The panel behind one stack, so a stack reads as one family. */
export interface LaidOutStack {
  key: string;
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface LaidOutCard {
  personId: string;
  person: PersonSummary;
  /** The slot this card belongs to — clicking a wife should focus her husband's slot. */
  slotKey: string;
  role: 'anchor' | 'spouse';
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface LaidOutSlot {
  key: string;
  node: CoupleNode;
  cards: LaidOutCard[];
  /** Bounding box of the whole slot, for virtualization and for the minimap. */
  x: number;
  y: number;
  width: number;
  height: number;
  depth: number;
  /** True when this slot has descendants that the collapsed set is hiding. */
  collapsed: boolean;
}

/** The bar between two people who married. Its type decides how it is drawn, not where. */
export interface LaidOutUnion {
  key: string;
  unionType: UnionType;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

/** An orthogonal connector from a marriage down to one child. */
export interface LaidOutParentEdge {
  key: string;
  path: string;
  nature: ParentageNature;
  childSlotKey: string;
}

/** One *đời* line across the canvas. */
export interface GenerationRail {
  depth: number;
  /** Position along the depth axis: a `y` when vertical, an `x` when horizontal. */
  position: number;
  /** The *đời* of the people on this rail; undefined when none of them has one recorded. */
  generation?: number;
}

export interface TreeBounds {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
}

export interface TreeLayout {
  orientation: Orientation;
  slots: LaidOutSlot[];
  unions: LaidOutUnion[];
  edges: LaidOutParentEdge[];
  stacks: LaidOutStack[];
  rails: GenerationRail[];
  bounds: TreeBounds;
  /** Slot key by person id, so a search hit resolves to something on the canvas. */
  slotOfPerson: Map<string, string>;
}

export interface LayoutOptions {
  orientation: Orientation;
  /** Slots whose descendants are hidden. The slot itself still renders. */
  collapsed: ReadonlySet<string>;
  /** Stack a large family's childless children into columns. On unless turned off. */
  stacking?: boolean;
}

/**
 * Lays out a forest.
 *
 * A forest rather than a tree: the roots are the founding ancestor *and* every fragment the payload
 * could not attach to him. They are laid out under one synthetic parent, which is dropped before
 * anything is returned — it exists only so the tidy layout spaces the forests apart instead of
 * stacking them at the origin.
 */
export function layoutTree(roots: CoupleNode[], options: LayoutOptions): TreeLayout {
  const { orientation, collapsed, stacking = true } = options;
  const along = orientation === 'vertical' ? CARD.width : CARD.height;
  const depth = orientation === 'vertical' ? CARD.height : CARD.width;

  const empty: TreeLayout = {
    orientation,
    slots: [],
    unions: [],
    edges: [],
    stacks: [],
    rails: [],
    bounds: { minX: 0, minY: 0, maxX: 0, maxY: 0 },
    slotOfPerson: new Map(),
  };
  const first = roots[0];
  if (first === undefined) return empty;

  const forest: CoupleNode = {
    key: FOREST_KEY,
    // Never rendered — the forest node is dropped below. It carries a person only because the
    // hierarchy is typed on real nodes, and inventing a placeholder type for one throwaway value
    // would spread `undefined` through every consumer of `CoupleNode`.
    anchor: first.anchor,
    families: [],
    children: roots,
  };

  const slotExtent = (node: CoupleNode) =>
    node.key === FOREST_KEY
      ? 0
      : slotWidthInCards(node) * along + (slotWidthInCards(node) - 1) * GAP.spouse;

  const levelStep = depth + GAP.generation;
  const isLeaf = (node: CoupleNode) => node.children.length === 0 || collapsed.has(node.key);

  const stackOf = (members: CoupleNode[], key: string): Stack => {
    // Taller than wide: a card is much wider than it is deep, so two columns of four take less room
    // than four columns of two and still read as a list.
    const columnCount = Math.min(members.length, Math.ceil(Math.sqrt(members.length / 2)));
    const rows = Math.ceil(members.length / columnCount);
    const columns: CoupleNode[][] = [];
    for (let column = 0; column < columnCount; column += 1) {
      columns.push(members.slice(column * rows, (column + 1) * rows));
    }
    const widths = columns.map(
      (column) => STACK.lead + Math.max(...column.map((member) => slotExtent(member))),
    );
    const extent = widths.reduce((sum, width) => sum + width, 0) + (columnCount - 1) * GAP.sibling;
    const stackDepth = rows * depth + (rows - 1) * STACK.rowGap;
    return {
      key,
      columns,
      widths,
      extent,
      // Every level the stack reaches into is reserved, including the band where the next
      // generation's connectors run, so no cousin's line is ever drawn through it.
      levels: Math.max(1, Math.ceil((stackDepth + GAP.generation) / levelStep)),
    };
  };

  const childrenOf = (item: Item): Item[] => {
    if (item.kind === 'stack') {
      const part = item.part + 1;
      return part < item.stack.levels
        ? [{ kind: 'stack', key: `${item.stack.key}#${String(part)}`, part, stack: item.stack }]
        : [];
    }
    const node = item.node;
    if (node.key === FOREST_KEY) {
      return node.children.map((child) => ({ kind: 'slot', key: child.key, node: child }));
    }
    if (collapsed.has(node.key)) return [];

    // Children grouped by the family they hang from, in the order the families are held. Within a
    // family the childless children are stacked when there are enough of them to make the row too
    // wide; the ones with families of their own stay in the row, in book order, before the stack.
    const items: Item[] = [];
    let index = 0;
    while (index < node.children.length) {
      const family = node.children[index]?.viaFamilyKey;
      const group: CoupleNode[] = [];
      for (
        let child = node.children[index];
        child !== undefined && child.viaFamilyKey === family;
        child = node.children[index]
      ) {
        group.push(child);
        index += 1;
      }
      const leaves = group.filter(isLeaf);
      if (stacking && leaves.length >= STACK_MIN) {
        for (const child of group) {
          if (!isLeaf(child)) items.push({ kind: 'slot', key: child.key, node: child });
        }
        const key = `${node.key}:stack:${family ?? ''}`;
        items.push({ kind: 'stack', key, part: 0, stack: stackOf(leaves, key) });
      } else {
        for (const child of group) items.push({ kind: 'slot', key: child.key, node: child });
      }
    }
    return items;
  };

  const extentOf = (item: Item) =>
    item.kind === 'stack' ? item.stack.extent : slotExtent(item.node);

  const root = hierarchy<Item>({ kind: 'slot', key: FOREST_KEY, node: forest }, childrenOf);

  const step = along + GAP.sibling;
  const laid = tidyTree<Item>()
    .nodeSize([step, levelStep])
    .separation((a, b) => {
      const needed = (extentOf(a.data) + extentOf(b.data)) / 2 + GAP.sibling;
      return (needed / step) * (a.parent === b.parent ? 1 : COUSIN_SPREAD);
    })(root);

  const slots: LaidOutSlot[] = [];
  const unions: LaidOutUnion[] = [];
  const edges: LaidOutParentEdge[] = [];
  const stacks: LaidOutStack[] = [];
  const slotOfPerson = new Map<string, string>();
  /** Where a family's connector leaves its slot, keyed by family key. */
  const descentPoints = new Map<string, { x: number; y: number }>();
  /** How a stacked child is reached: down a trunk beside its column and in from the side. */
  const stackRoutes = new Map<string, StackRoute>();

  const toPoint = (alongPos: number, depthPos: number): Point =>
    orientation === 'vertical' ? { x: alongPos, y: depthPos } : { x: depthPos, y: alongPos };

  /** Places one slot whose leading edge is at `alongStart` and whose cards start at `depthPos`. */
  const placeSlot = (
    node: CoupleNode,
    alongStart: number,
    depthPos: number,
    depthIndex: number,
  ) => {
    const cards: LaidOutCard[] = peopleInSlot(node).map((person, index) => {
      const at = toPoint(alongStart + index * (along + GAP.spouse), depthPos);
      return {
        personId: person.id,
        person,
        slotKey: node.key,
        role: index === 0 ? 'anchor' : 'spouse',
        x: at.x,
        y: at.y,
        width: CARD.width,
        height: CARD.height,
      };
    });

    for (const card of cards) slotOfPerson.set(card.personId, node.key);

    const box = boundsOf(cards);
    slots.push({
      key: node.key,
      node,
      cards,
      x: box.minX,
      y: box.minY,
      width: box.maxX - box.minX,
      height: box.maxY - box.minY,
      depth: depthIndex,
      collapsed: collapsed.has(node.key) && node.children.length > 0,
    });

    // A family's connector leaves from the gap between the two cards it joins; a family with no
    // recorded spouse leaves from under the anchor, which is where a one-parent line belongs.
    const anchorCard = cards[0];
    if (anchorCard === undefined) return;

    let spouseIndex = 0;
    for (const family of node.families) {
      if (family.spouse === undefined) {
        descentPoints.set(family.key, bottomCentre(anchorCard, orientation));
        continue;
      }
      const left = cards[spouseIndex];
      const right = cards[spouseIndex + 1];
      spouseIndex += 1;
      if (left === undefined || right === undefined) continue;
      unions.push({
        key: family.key,
        unionType: family.union?.unionType ?? 'PRIMARY',
        ...connector(left, right, orientation),
      });
      descentPoints.set(family.key, midpointBetween(left, right, orientation));
    }
  };

  laid.each((point: HierarchyPointNode<Item>) => {
    const item = point.data;
    if (item.key === FOREST_KEY) return;

    // The synthetic root occupies depth 0, so every real slot is one deeper than its generation.
    const depthIndex = point.depth - 1;
    const depthPos = point.y - levelStep;

    if (item.kind === 'slot') {
      placeSlot(item.node, point.x - slotExtent(item.node) / 2, depthPos, depthIndex);
      return;
    }
    // The chain below a stack only reserves room; the stack itself is placed from its first level.
    if (item.part !== 0) return;

    // Columns side by side, each read top to bottom, each with a trunk down its leading edge that
    // the connectors run along.
    const { stack } = item;
    let columnStart = point.x - stack.extent / 2;
    let deepest = depthPos;
    stack.columns.forEach((column, columnIndex) => {
      column.forEach((member, row) => {
        const rowDepth = depthPos + row * (depth + STACK.rowGap);
        placeSlot(member, columnStart + STACK.lead, rowDepth, depthIndex);
        stackRoutes.set(member.key, {
          trunk: columnStart + STACK.lead / 2,
          enter: columnStart + STACK.lead,
          middle: rowDepth + depth / 2,
          top: depthPos,
        });
        deepest = Math.max(deepest, rowDepth + depth);
      });
      columnStart += (stack.widths[columnIndex] ?? 0) + GAP.sibling;
    });

    const from = toPoint(point.x - stack.extent / 2 - STACK.inset, depthPos - STACK.inset);
    const to = toPoint(point.x + stack.extent / 2 + STACK.inset, deepest + STACK.inset);
    stacks.push({
      key: stack.key,
      x: Math.min(from.x, to.x),
      y: Math.min(from.y, to.y),
      width: Math.abs(to.x - from.x),
      height: Math.abs(to.y - from.y),
    });
  });

  // Connectors are built after every slot has a position, because an edge needs both ends.
  const slotByKey = new Map(slots.map((slot) => [slot.key, slot]));
  for (const slot of slots) {
    for (const child of slot.node.children) {
      const target = slotByKey.get(child.key);
      const from =
        child.viaFamilyKey === undefined ? undefined : descentPoints.get(child.viaFamilyKey);
      const into = target?.cards[0];
      if (from === undefined || into === undefined) continue;
      const route = stackRoutes.get(child.key);
      edges.push({
        key: `${slot.key}->${child.key}`,
        path:
          route === undefined
            ? elbow(from, topCentre(into, orientation), orientation)
            : stackElbow(from, route, orientation),
        nature: child.nature ?? 'BIOLOGICAL',
        childSlotKey: child.key,
      });
    }
  }

  return {
    orientation,
    slots,
    unions,
    edges,
    stacks,
    rails: railsOf(slots, orientation),
    bounds: boundsOf(slots.flatMap((slot) => slot.cards)),
    slotOfPerson,
  };
}

/** Whether a box is inside the viewport, with a margin so scrolling never reveals a blank. */
export function intersects(box: TreeBounds, viewport: TreeBounds, margin: number): boolean {
  return (
    box.maxX >= viewport.minX - margin &&
    box.minX <= viewport.maxX + margin &&
    box.maxY >= viewport.minY - margin &&
    box.minY <= viewport.maxY + margin
  );
}

/** The bounding box of a laid-out slot, in the same shape {@link intersects} takes. */
export function boxOf(slot: LaidOutSlot): TreeBounds {
  return {
    minX: slot.x,
    minY: slot.y,
    maxX: slot.x + slot.width,
    maxY: slot.y + slot.height,
  };
}

// --- internals ---------------------------------------------------------------------------------

interface Point {
  x: number;
  y: number;
}

function boundsOf(cards: { x: number; y: number; width: number; height: number }[]): TreeBounds {
  if (cards.length === 0) return { minX: 0, minY: 0, maxX: 0, maxY: 0 };
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  for (const card of cards) {
    minX = Math.min(minX, card.x);
    minY = Math.min(minY, card.y);
    maxX = Math.max(maxX, card.x + card.width);
    maxY = Math.max(maxY, card.y + card.height);
  }
  return { minX, minY, maxX, maxY };
}

function bottomCentre(card: LaidOutCard, orientation: Orientation): Point {
  return orientation === 'vertical'
    ? { x: card.x + card.width / 2, y: card.y + card.height }
    : { x: card.x + card.width, y: card.y + card.height / 2 };
}

function topCentre(card: LaidOutCard, orientation: Orientation): Point {
  return orientation === 'vertical'
    ? { x: card.x + card.width / 2, y: card.y }
    : { x: card.x, y: card.y + card.height / 2 };
}

/** The bar joining two cards that sit side by side along the sibling axis. */
function connector(
  left: LaidOutCard,
  right: LaidOutCard,
  orientation: Orientation,
): { x1: number; y1: number; x2: number; y2: number } {
  return orientation === 'vertical'
    ? {
        x1: left.x + left.width,
        y1: left.y + left.height / 2,
        x2: right.x,
        y2: right.y + right.height / 2,
      }
    : {
        x1: left.x + left.width / 2,
        y1: left.y + left.height,
        x2: right.x + right.width / 2,
        y2: right.y,
      };
}

function midpointBetween(left: LaidOutCard, right: LaidOutCard, orientation: Orientation): Point {
  const bar = connector(left, right, orientation);
  return orientation === 'vertical'
    ? { x: (bar.x1 + bar.x2) / 2, y: left.y + left.height }
    : { x: left.x + left.width, y: (bar.y1 + bar.y2) / 2 };
}

/**
 * An orthogonal connector: out of the parents, across, and into the child.
 *
 * Right angles rather than curves, because a *gia phả* is drawn with rules and a curve reads as a
 * suggestion. The turn is halfway down the generation gap, so every connector in a generation turns
 * on the same line and the result looks like a diagram rather than a bundle of wires.
 */
function elbow(from: Point, to: Point, orientation: Orientation): string {
  if (orientation === 'vertical') {
    const turn = from.y + (to.y - from.y) / 2;
    return `M ${from.x} ${from.y} V ${turn} H ${to.x} V ${to.y}`;
  }
  const turn = from.x + (to.x - from.x) / 2;
  return `M ${from.x} ${from.y} H ${turn} V ${to.y} H ${to.x}`;
}

/**
 * The connector into a stacked child: down from the parents to the turn, across to the column's
 * trunk, down the trunk, and in from the side. Every child in a column shares the trunk, so the
 * column reads as one family hanging from one line.
 */
function stackElbow(from: Point, route: StackRoute, orientation: Orientation): string {
  const fromAlong = orientation === 'vertical' ? from.x : from.y;
  const fromDepth = orientation === 'vertical' ? from.y : from.x;
  const turn = fromDepth + (route.top - fromDepth) / 2;
  const at = (alongPos: number, depthPos: number) =>
    orientation === 'vertical'
      ? `${String(alongPos)} ${String(depthPos)}`
      : `${String(depthPos)} ${String(alongPos)}`;
  return [
    `M ${at(fromAlong, fromDepth)}`,
    `L ${at(fromAlong, turn)}`,
    `L ${at(route.trunk, turn)}`,
    `L ${at(route.trunk, route.middle)}`,
    `L ${at(route.enter, route.middle)}`,
  ].join(' ');
}

/**
 * One rail per layout depth, labelled with the *đời* the people on it actually carry.
 *
 * Labelled from the data rather than from the depth, because the two are not the same number: a
 * fragment attached at the wrong height, or a subtree read that starts at *đời* 4, both put depth 0
 * somewhere other than the founding ancestor. The most common generation on a rail is the label;
 * where nobody on it has one, the rail is drawn without a number rather than with a made-up one
 * (phase 4 §8).
 */
function railsOf(slots: LaidOutSlot[], orientation: Orientation): GenerationRail[] {
  const byDepth = new Map<number, { position: number; generations: Map<number, number> }>();

  for (const slot of slots) {
    const position = orientation === 'vertical' ? slot.y : slot.x;
    const rail = byDepth.get(slot.depth) ?? { position, generations: new Map<number, number>() };
    rail.position = Math.min(rail.position, position);
    const generation = slot.node.anchor.generation;
    if (generation !== undefined) {
      rail.generations.set(generation, (rail.generations.get(generation) ?? 0) + 1);
    }
    byDepth.set(slot.depth, rail);
  }

  const rails = [...byDepth.entries()]
    .sort((left, right) => left[0] - right[0])
    .map(([depth, rail]) => {
      let generation: number | undefined;
      let best = 0;
      for (const [candidate, count] of rail.generations) {
        if (count > best) {
          best = count;
          generation = candidate;
        }
      }
      return {
        depth,
        position: rail.position,
        ...(generation === undefined ? {} : { generation }),
      };
    });

  // A rail where nobody carries a đời still sits a known number of steps from one that does — the
  // depth axis *is* the generation axis — so it is labelled by counting from the nearest numbered
  // rail rather than shown as unknown. Only when no rail at all has a number is there nothing to
  // count from, and then the first rail is đời 1.
  const numbered = rails.filter((rail) => rail.generation !== undefined);
  return rails.map((rail) => {
    if (rail.generation !== undefined) return rail;
    let nearest: GenerationRail | undefined;
    for (const candidate of numbered) {
      if (
        nearest === undefined ||
        Math.abs(candidate.depth - rail.depth) < Math.abs(nearest.depth - rail.depth)
      ) {
        nearest = candidate;
      }
    }
    const generation =
      nearest?.generation === undefined
        ? rail.depth + 1
        : nearest.generation + (rail.depth - nearest.depth);
    return generation >= 1 ? { ...rail, generation } : rail;
  });
}
