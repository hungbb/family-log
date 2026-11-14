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
 * | Category        | How it is drawn                                                  |
 * | --------------- | ---------------------------------------------------------------- |
 * | Gender          | the card's **background colour** — blue for male, pink for female |
 * | Deceased        | a **gray** card, whatever the gender                             |
 * | You             | accent ring and a "You" **tag** above the card                   |
 * | Has an account  | green **ring** around the avatar                                 |
 * | Adopted / step / foster | **dashed** connector                                     |
 * | Secondary marriage | **thinner, dashed** bar                                       |
 *
 * Gender and life status are carried by the card's fill alone, so a reader takes them in at a
 * glance across a whole generation. Both are also said in words in each card's accessible label.
 */

/** A card's colour family: a `--tree-<tone>Card` (and `…Border`, `…Avatar`, `…Initials`) role. */
export type CardTone = 'male' | 'female' | 'deceased';

/** The colour family of each gender, for a living person. */
export const GENDER_TONE: Record<Gender, Exclude<CardTone, 'deceased'>> = {
  MALE: 'male',
  FEMALE: 'female',
};

/** Every gender, in the order the legend and the person form list them. */
export const GENDERS: Gender[] = ['MALE', 'FEMALE'];

/** The colour a card is filled with: gray for the deceased, otherwise the gender's tint. */
export function cardToneOf(visual: Pick<PersonVisual, 'gender' | 'deceased'>): CardTone {
  return visual.deceased ? 'deceased' : GENDER_TONE[visual.gender];
}

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
