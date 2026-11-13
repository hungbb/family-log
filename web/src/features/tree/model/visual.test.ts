import { describe, expect, it } from 'vitest';

import { type PersonSummary } from '@/api/endpoints/persons.ts';

import {
  GENDER_STYLE,
  GENDERS,
  STROKE,
  genderShapePath,
  isDashedDescent,
  isSecondaryUnion,
  screenStroke,
  visualOf,
} from './visual';

describe('the visual vocabulary', () => {
  it('gives every gender its own shape, so colour is never the only difference', () => {
    const shapes = GENDERS.map((gender) => GENDER_STYLE[gender].shape);
    expect(new Set(shapes).size).toBe(GENDERS.length);
  });

  it('reads the categories off a person', () => {
    const person = {
      gender: 'FEMALE',
      lifeStatus: 'DECEASED',
      linkedUserId: 'u1',
    } as PersonSummary;
    expect(visualOf(person, true)).toEqual({
      gender: 'FEMALE',
      deceased: true,
      self: true,
      linked: true,
    });
    expect(visualOf({ ...person, lifeStatus: 'UNKNOWN' }, false).deceased).toBe(false);
  });

  it('dashes every non-biological descent and every non-primary marriage', () => {
    expect(isDashedDescent('BIOLOGICAL')).toBe(false);
    expect(isDashedDescent('ADOPTED')).toBe(true);
    expect(isDashedDescent('STEP')).toBe(true);
    expect(isDashedDescent('FOSTER')).toBe(true);
    expect(isSecondaryUnion('PRIMARY')).toBe(false);
    expect(isSecondaryUnion('SECONDARY')).toBe(true);
    expect(isSecondaryUnion('SEQUENTIAL')).toBe(true);
  });
});

describe('screenStroke', () => {
  it('never lets a connector fall below its floor, however far out the zoom', () => {
    expect(screenStroke(STROKE.edge, 0.08)).toBe(STROKE.edge.min);
    expect(screenStroke(STROKE.union, 0.08)).toBe(STROKE.union.min);
  });

  it('follows the zoom above the floor', () => {
    expect(screenStroke(STROKE.edge, 2)).toBeCloseTo(3.2);
    expect(screenStroke(STROKE.union, 1)).toBeCloseTo(2.6);
  });

  it('keeps a primary marriage heavier than a secondary one at every scale', () => {
    for (const scale of [0.08, 0.3, 1, 2.5]) {
      expect(screenStroke(STROKE.union, scale)).toBeGreaterThan(
        screenStroke(STROKE.unionSecondary, scale),
      );
    }
  });
});

describe('genderShapePath', () => {
  it('draws a closed outline for every shape', () => {
    for (const gender of GENDERS) {
      const path = genderShapePath(GENDER_STYLE[gender].shape, 30, 26, 14);
      expect(path.startsWith('M ')).toBe(true);
      expect(path.endsWith('Z')).toBe(true);
    }
  });
});
