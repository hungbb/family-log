import { describe, expect, it } from 'vitest';

import { type PartialDate, type PersonSummary } from '@/api/endpoints/persons.ts';
import { translator } from '@/test/translator.ts';

import {
  formatGeneration,
  formatLifespan,
  formatPartialDate,
  parentLabel,
  personLabel,
  spouseLabel,
} from './format';

const vi = translator('vi');
const en = translator('en');

/** The few fields these formatters read; the rest of a summary is irrelevant to them. */
function person(fields: Partial<PersonSummary>): PersonSummary {
  return { id: 'p1', fullName: 'Nguyễn Văn An', ...fields } as PersonSummary;
}

describe('formatPartialDate', () => {
  it('shows a year as a year, and never invents a day', () => {
    const date: PartialDate = { precision: 'YEAR', circa: false, year: 1901 };

    expect(formatPartialDate(date, vi)).toBe('1901');
  });

  it('marks a remembered year as approximate', () => {
    const date: PartialDate = { precision: 'YEAR', circa: true, year: 1890 };

    expect(formatPartialDate(date, vi)).toBe('khoảng 1890');
    expect(formatPartialDate(date, en)).toBe('about 1890');
  });

  it('renders month and exact precision day-first', () => {
    expect(formatPartialDate({ precision: 'MONTH', circa: false, year: 1943, month: 6 }, vi)).toBe(
      '6/1943',
    );
    expect(
      formatPartialDate({ precision: 'EXACT', circa: false, year: 1968, month: 2, day: 1 }, vi),
    ).toBe('1/2/1968');
  });

  it('renders a range as its two ends', () => {
    const date: PartialDate = { precision: 'RANGE', circa: false, year: 1930, rangeEndYear: 1945 };

    expect(formatPartialDate(date, vi)).toBe('1930–1945');
  });

  it("appends the book's lunar reading verbatim", () => {
    const date: PartialDate = {
      precision: 'MONTH',
      circa: false,
      year: 1943,
      month: 7,
      lunarText: 'tháng 6 năm Quý Mùi',
    };

    expect(formatPartialDate(date, vi)).toBe('7/1943 (tháng 6 năm Quý Mùi)');
  });

  it('shows a date that is only a lunar reading, and says that it is lunar', () => {
    const anniversary: PartialDate = {
      precision: 'UNKNOWN',
      circa: false,
      lunarText: 'mồng 3 tháng Chạp',
    };

    expect(formatPartialDate(anniversary, vi)).toBe('mồng 3 tháng Chạp (âm lịch)');
    expect(formatPartialDate(anniversary, en)).toBe('mồng 3 tháng Chạp (lunar calendar)');
  });

  it('says nothing when nothing is recorded', () => {
    expect(formatPartialDate(undefined, vi)).toBeUndefined();
    expect(formatPartialDate({ precision: 'UNKNOWN', circa: false }, vi)).toBeUndefined();
  });
});

describe('formatLifespan', () => {
  const born: PartialDate = { precision: 'YEAR', circa: false, year: 1901 };
  const died: PartialDate = { precision: 'YEAR', circa: false, year: 1968 };

  it('joins both ends when both are known', () => {
    expect(formatLifespan(person({ birth: born, death: died }), vi)).toBe('1901 – 1968');
  });

  it('names the one end it has', () => {
    expect(formatLifespan(person({ birth: born }), vi)).toBe('sinh 1901');
    expect(formatLifespan(person({ death: died }), vi)).toBe('mất 1968');
    expect(formatLifespan(person({ birth: born }), en)).toBe('b. 1901');
  });

  it('uses a lunar-only end like any other', () => {
    const anniversary: PartialDate = {
      precision: 'UNKNOWN',
      circa: false,
      lunarText: 'mồng 3 tháng Chạp',
    };

    expect(formatLifespan(person({ death: anniversary }), vi)).toBe(
      'mất mồng 3 tháng Chạp (âm lịch)',
    );
    expect(formatLifespan(person({ birth: born, death: anniversary }), vi)).toBe(
      '1901 – mồng 3 tháng Chạp (âm lịch)',
    );
  });

  it('is absent, not an empty dash, when neither is known', () => {
    expect(formatLifespan(person({}), vi)).toBeUndefined();
  });
});

describe('generation and kinship words', () => {
  it('prints đời when it is known and says so when it is not', () => {
    expect(formatGeneration(3, vi)).toBe('đời 3');
    expect(formatGeneration(undefined, vi)).toBe(vi('tree.generation.unknown'));
    expect(formatGeneration(undefined, vi)).not.toContain('0');
  });

  it('uses the Vietnamese kinship word for a parent', () => {
    expect(parentLabel('FATHER', vi)).toBe(vi('tree.kin.father'));
    expect(parentLabel('MOTHER', vi)).toBe(vi('tree.kin.mother'));
  });

  it('picks vợ or chồng by gender, and the neutral word when the book does not say', () => {
    expect(spouseLabel(person({ gender: 'FEMALE' }), vi)).toBe(vi('tree.kin.wife'));
    expect(spouseLabel(person({ gender: 'MALE' }), vi)).toBe(vi('tree.kin.husband'));
    expect(spouseLabel(person({ gender: 'UNKNOWN' }), vi)).toBe(vi('tree.kin.spouse'));
  });

  it('labels a person with their đời only when there is one', () => {
    expect(personLabel(person({}), vi)).toBe('Nguyễn Văn An');
    expect(personLabel(person({ generation: 4 }), vi)).toContain('4');
  });
});
