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
  rails: GenerationRail[];
  bounds: TreeBounds;
  /** Slot key by person id, so a search hit resolves to something on the canvas. */
  slotOfPerson: Map<string, string>;
}

export interface LayoutOptions {
  orientation: Orientation;
  /** Slots whose descendants are hidden. The slot itself still renders. */
  collapsed: ReadonlySet<string>;
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
  const { orientation, collapsed } = options;
  const along = orientation === 'vertical' ? CARD.width : CARD.height;
  const depth = orientation === 'vertical' ? CARD.height : CARD.width;

  const empty: TreeLayout = {
    orientation,
    slots: [],
    unions: [],
    edges: [],
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

  const root = hierarchy<CoupleNode>(forest, (node) =>
    node.key !== FOREST_KEY && collapsed.has(node.key) ? [] : node.children,
  );

  const step = along + GAP.sibling;
  const laid = tidyTree<CoupleNode>()
    .nodeSize([step, depth + GAP.generation])
    .separation((a, b) => {
      const needed = (slotExtent(a.data) + slotExtent(b.data)) / 2 + GAP.sibling;
      return (needed / step) * (a.parent === b.parent ? 1 : COUSIN_SPREAD);
    })(root);

  const slots: LaidOutSlot[] = [];
  const unions: LaidOutUnion[] = [];
  const edges: LaidOutParentEdge[] = [];
  const slotOfPerson = new Map<string, string>();
  /** Where a family's connector leaves its slot, keyed by family key. */
  const descentPoints = new Map<string, { x: number; y: number }>();

  laid.each((point: HierarchyPointNode<CoupleNode>) => {
    if (point.data.key === FOREST_KEY) return;

    // The synthetic root occupies depth 0, so every real slot is one deeper than its generation.
    const depthIndex = point.depth - 1;
    const extent = slotExtent(point.data);
    const people = peopleInSlot(point.data);

    const cards: LaidOutCard[] = people.map((person, index) => {
      const offset = -extent / 2 + index * (along + GAP.spouse);
      const alongPos = point.x + offset;
      const depthPos = point.y - (depth + GAP.generation);
      return {
        personId: person.id,
        person,
        slotKey: point.data.key,
        role: index === 0 ? 'anchor' : 'spouse',
        x: orientation === 'vertical' ? alongPos : depthPos,
        y: orientation === 'vertical' ? depthPos : alongPos,
        width: CARD.width,
        height: CARD.height,
      };
    });

    for (const card of cards) slotOfPerson.set(card.personId, point.data.key);

    const box = boundsOf(cards);
    slots.push({
      key: point.data.key,
      node: point.data,
      cards,
      x: box.minX,
      y: box.minY,
      width: box.maxX - box.minX,
      height: box.maxY - box.minY,
      depth: depthIndex,
      collapsed: collapsed.has(point.data.key) && point.data.children.length > 0,
    });

    // A family's connector leaves from the gap between the two cards it joins; a family with no
    // recorded spouse leaves from under the anchor, which is where a one-parent line belongs.
    const anchorCard = cards[0];
    if (anchorCard === undefined) return;

    let spouseIndex = 0;
    for (const family of point.data.families) {
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
      edges.push({
        key: `${slot.key}->${child.key}`,
        path: elbow(from, topCentre(into, orientation), orientation),
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

  return [...byDepth.entries()]
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
}
