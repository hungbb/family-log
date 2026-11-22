import { describe, expect, it } from 'vitest';

import { type PartialDate } from '@/api/endpoints/persons.ts';

import { deathFields } from './deathFields';

const DIED: PartialDate = { precision: 'YEAR', circa: false, year: 1972 };

const FILLED = {
  death: DIED,
  burialPlace: 'Nghĩa trang làng Đông Ngạc',
  posthumousName: 'Phúc Thiện',
};

describe('deathFields', () => {
  it('sends what the fields hold for somebody who has died', () => {
    expect(deathFields({ lifeStatus: 'DECEASED', ...FILLED })).toEqual(FILLED);
  });

  it('leaves out the fields left blank for somebody who has died', () => {
    expect(deathFields({ lifeStatus: 'DECEASED', burialPlace: 'Đông Ngạc' })).toEqual({
      burialPlace: 'Đông Ngạc',
    });
  });

  it('clears all three for somebody living, even when the hidden fields still hold values', () => {
    // The server refuses a living person with a date of death, and a PATCH that omitted the date
    // would keep the old one.
    expect(deathFields({ lifeStatus: 'ALIVE', ...FILLED })).toEqual({
      death: { precision: 'UNKNOWN', circa: false },
      burialPlace: '',
      posthumousName: '',
    });
  });

  it('sends nothing when the status is unknown, so an edit leaves the record alone', () => {
    expect(deathFields({ lifeStatus: 'UNKNOWN', ...FILLED })).toEqual({});
    expect(deathFields(FILLED)).toEqual({});
  });
});
