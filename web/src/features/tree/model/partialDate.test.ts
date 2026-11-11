import { describe, expect, it } from 'vitest';

import { type PartialDate } from '@/api/endpoints/persons.ts';

import { withField, withLunarText, withPrecision } from './partialDate';

const ANNIVERSARY = 'giỗ ngày mồng 3 tháng Chạp';

describe('withLunarText', () => {
  it('records a lunar reading on a field that has no date yet', () => {
    expect(withLunarText(undefined, ANNIVERSARY)).toEqual({
      precision: 'UNKNOWN',
      circa: false,
      lunarText: ANNIVERSARY,
    });
  });

  it('adds the reading to a dated field without disturbing the date', () => {
    const born: PartialDate = { precision: 'MONTH', circa: false, year: 1943, month: 7 };

    expect(withLunarText(born, 'tháng 6 năm Quý Mùi')).toEqual({
      ...born,
      lunarText: 'tháng 6 năm Quý Mùi',
    });
  });

  it('clears the whole field when the last thing in it is erased', () => {
    const lunarOnly: PartialDate = { precision: 'UNKNOWN', circa: false, lunarText: ANNIVERSARY };

    expect(withLunarText(lunarOnly, '')).toBeUndefined();
    expect(withLunarText(lunarOnly, '   ')).toBeUndefined();
  });

  it('removes only the reading from a dated field', () => {
    const dated: PartialDate = { precision: 'YEAR', circa: true, year: 1890, lunarText: 'x' };

    expect(withLunarText(dated, '')).toEqual({ precision: 'YEAR', circa: true, year: 1890 });
  });

  it('keeps what is being typed, spaces included, until it is empty', () => {
    expect(withLunarText(undefined, 'giỗ ')?.lunarText).toBe('giỗ ');
  });
});

describe('withPrecision', () => {
  it('keeps the lunar reading when the Gregorian date is given up', () => {
    const dated: PartialDate = {
      precision: 'EXACT',
      circa: false,
      year: 1968,
      month: 2,
      day: 1,
      lunarText: 'mồng 3 Tết Mậu Thân',
    };

    expect(withPrecision(dated, 'UNKNOWN', 2026)).toEqual({
      precision: 'UNKNOWN',
      circa: false,
      lunarText: 'mồng 3 Tết Mậu Thân',
    });
  });

  it('clears the field when there is no reading to keep', () => {
    expect(
      withPrecision({ precision: 'YEAR', circa: false, year: 1901 }, 'UNKNOWN', 2026),
    ).toBeUndefined();
    expect(withPrecision(undefined, 'UNKNOWN', 2026)).toBeUndefined();
  });

  it('gives a lunar-only date a year when one is chosen, and keeps the reading', () => {
    const lunarOnly: PartialDate = { precision: 'UNKNOWN', circa: false, lunarText: ANNIVERSARY };

    expect(withPrecision(lunarOnly, 'YEAR', 2026)).toEqual({
      precision: 'YEAR',
      circa: false,
      year: 2026,
      lunarText: ANNIVERSARY,
    });
  });

  it('carries only the parts the new precision has', () => {
    const exact: PartialDate = { precision: 'EXACT', circa: true, year: 1968, month: 2, day: 1 };

    expect(withPrecision(exact, 'YEAR', 2026)).toEqual({
      precision: 'YEAR',
      circa: true,
      year: 1968,
    });
    expect(withPrecision(exact, 'MONTH', 2026)).toEqual({
      precision: 'MONTH',
      circa: true,
      year: 1968,
      month: 2,
    });
    expect(withPrecision(exact, 'RANGE', 2026)).toEqual({
      precision: 'RANGE',
      circa: true,
      year: 1968,
      rangeEndYear: 1968,
    });
  });

  it('starts a new date from this year, the first of the month', () => {
    expect(withPrecision(undefined, 'EXACT', 2026)).toEqual({
      precision: 'EXACT',
      circa: false,
      year: 2026,
      month: 1,
      day: 1,
    });
  });
});

describe('withField', () => {
  it('applies one changed part and drops a cleared one', () => {
    const month: PartialDate = { precision: 'MONTH', circa: false, year: 1943, month: 7 };

    expect(withField(month, { year: 1944 })).toEqual({ ...month, year: 1944 });
    expect(withField(month, { month: undefined })).toEqual({
      precision: 'MONTH',
      circa: false,
      year: 1943,
    });
  });

  it('never sends a key holding undefined', () => {
    const next = withField({ precision: 'YEAR', circa: false, year: 1901 }, { year: undefined });

    expect(next).toEqual({ precision: 'YEAR', circa: false });
    expect(Object.keys(next ?? {})).not.toContain('year');
  });

  it('does not lose a lunar-only date when another control is touched', () => {
    const lunarOnly: PartialDate = { precision: 'UNKNOWN', circa: false, lunarText: ANNIVERSARY };

    expect(withField(lunarOnly, { circa: false })).toEqual(lunarOnly);
  });
});
