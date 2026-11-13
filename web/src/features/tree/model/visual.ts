import {
  type Gender,
  type ParentageNature,
  type PersonSummary,
  type UnionType,
} from '@/api/endpoints/persons.ts';

/**
 * The tree's visual vocabulary, as data (#61).
 *
 * Every category the canvas distinguishes — gender, life status, the viewer's own node, an account
 * link, adoptive vs. biological descent, primary vs. secondary marriage — is decided here, once,
 * and read by the canvas, the minimap, the legend, the list view and the print sheet. A category
 * that is styled ad hoc in one renderer drifts from the others; a category that is a value here
 * cannot.
 *
 * **Colour is never the only carrier** (WCAG 1.4.1). Each category has a second channel that
 * survives greyscale, a colour-blind reader and a black-and-white printer:
 *
 * | Category        | Colour                     | Second channel                                 |
 * | --------------- | -------------------------- | ---------------------------------------------- |
 * | Gender          | rail + avatar outline hue  | avatar **shape**: square, circle, diamond, dashed circle |
 * | Deceased        | muted card, name and avatar | a dark **mourning band** across the card corner |
 * | You             | accent ring                | a "You" **tag** above the card                  |
 * | Has an account  | green ring                 | a **ring** at all (none without an account)     |
 * | Adopted / step / foster | —                  | **dashed** connector                           |
 * | Secondary marriage | lighter                 | **thinner, dashed** bar                        |
 *
 * The shapes follow the pedigree-chart convention genealogists already read (square male, circle
 * female, diamond for anything else), so the legend confirms what a reader expects rather than
 * teaching something new.
 */

export type GenderShape = 'square' | 'circle' | 'diamond' | 'open';

/** The tone (a `--tree-*` palette role) and the shape of each gender. */
export const GENDER_STYLE: Record<
  Gender,
  { tone: 'male' | 'female' | 'other' | 'unknown'; shape: GenderShape }
> = {
  MALE: { tone: 'male', shape: 'square' },
  FEMALE: { tone: 'female', shape: 'circle' },
  OTHER: { tone: 'other', shape: 'diamond' },
  UNKNOWN: { tone: 'unknown', shape: 'open' },
};

/** The order the legend lists genders in. */
export const GENDERS: Gender[] = ['MALE', 'FEMALE', 'OTHER', 'UNKNOWN'];

/** Everything about a person's card that is a category rather than text. */
export interface PersonVisual {
  gender: Gender;
  deceased: boolean;
  /** The signed-in account's own node. */
  self: boolean;
  /** Linked to some account — the viewer's or another member's. */
  linked: boolean;
}

export function visualOf(person: PersonSummary, isSelf: boolean): PersonVisual {
  return {
    gender: person.gender,
    deceased: person.lifeStatus === 'DECEASED',
    self: isSelf,
    linked: person.linkedUserId !== undefined,
  };
}

/** Adopted, step and fostered children are drawn dashed; only a biological edge is solid. */
export function isDashedDescent(nature: ParentageNature): boolean {
  return nature !== 'BIOLOGICAL';
}

/** A *thứ thất* or a later marriage is the secondary style; only the primary union is heavy. */
export function isSecondaryUnion(unionType: UnionType): boolean {
  return unionType !== 'PRIMARY';
}

/**
 * Connector weights, in screen pixels at scale 1.
 *
 * Connectors are drawn with `vector-effect: non-scaling-stroke`, so their width is in screen pixels
 * rather than canvas units. That alone fixes the disappearing-edges bug — a 1.5-unit stroke at the
 * 0.08 floor is 0.12px and the browser drops it — but a stroke that never scales also looks
 * spindly at 2.5×. So the weight still follows the zoom, and is only floored: never thinner than
 * `min`, whatever the scale.
 */
export const STROKE = {
  edge: { base: 1.6, min: 1.25 },
  union: { base: 2.6, min: 1.75 },
  unionSecondary: { base: 1.6, min: 1.25 },
  rail: { base: 1, min: 1 },
} as const;

/** Screen-pixel width of a connector at `scale`: proportional above the floor, the floor below. */
export function screenStroke(weight: { base: number; min: number }, scale: number): number {
  return Math.max(weight.min, Math.round(weight.base * scale * 100) / 100);
}

/**
 * The outline of a gender's avatar, centred on (`cx`, `cy`) with "radius" `r`.
 *
 * The square and the diamond are drawn a little smaller than the circle's bounding box so the
 * three read as the same size — a square as wide as a circle looks heavier than it.
 */
export function genderShapePath(shape: GenderShape, cx: number, cy: number, r: number): string {
  switch (shape) {
    case 'square': {
      const half = r * 0.9;
      const corner = r * 0.28;
      const left = cx - half;
      const top = cy - half;
      const side = half * 2;
      return [
        `M ${f(left + corner)} ${f(top)}`,
        `H ${f(left + side - corner)}`,
        `Q ${f(left + side)} ${f(top)} ${f(left + side)} ${f(top + corner)}`,
        `V ${f(top + side - corner)}`,
        `Q ${f(left + side)} ${f(top + side)} ${f(left + side - corner)} ${f(top + side)}`,
        `H ${f(left + corner)}`,
        `Q ${f(left)} ${f(top + side)} ${f(left)} ${f(top + side - corner)}`,
        `V ${f(top + corner)}`,
        `Q ${f(left)} ${f(top)} ${f(left + corner)} ${f(top)}`,
        'Z',
      ].join(' ');
    }
    case 'diamond': {
      const reach = r * 1.12;
      return `M ${f(cx)} ${f(cy - reach)} L ${f(cx + reach)} ${f(cy)} L ${f(cx)} ${f(cy + reach)} L ${f(cx - reach)} ${f(cy)} Z`;
    }
    case 'circle':
    case 'open':
      return `M ${f(cx - r)} ${f(cy)} A ${f(r)} ${f(r)} 0 1 0 ${f(cx + r)} ${f(cy)} A ${f(r)} ${f(r)} 0 1 0 ${f(cx - r)} ${f(cy)} Z`;
  }
}

/** Two decimals is sub-pixel at every zoom the canvas allows, and keeps the markup short. */
function f(value: number): string {
  return String(Math.round(value * 100) / 100);
}
