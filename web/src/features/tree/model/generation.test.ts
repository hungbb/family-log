import { describe, expect, it } from 'vitest';

import { type PersonSummary, type Union } from '@/api/endpoints/persons.ts';
import { type Tree } from '@/api/endpoints/tree.ts';

import { generationThroughMarriage, withMarriedInGenerations } from './generation';

function person(id: string, generation?: number): PersonSummary {
  return {
    id,
    fullName: id,
    ...(generation === undefined ? {} : { generation }),
  } as PersonSummary;
}

function union(id: string, a: string, b: string, unionType: Union['unionType'] = 'PRIMARY'): Union {
  return { id, partnerAId: a, partnerBId: b, unionType } as Union;
}

function tree(persons: PersonSummary[], unions: Union[]): Tree {
  return { persons, unions, parentages: [], branches: [], rootPersonIds: [] } as unknown as Tree;
}

describe('generationThroughMarriage', () => {
  it('keeps a generation the person already has', () => {
    expect(
      generationThroughMarriage(person('wife', 3), [
        { union: { unionType: 'PRIMARY' }, partner: person('husband', 1) },
      ]),
    ).toBe(3);
  });

  it('prefers the primary union over a later one', () => {
    expect(
      generationThroughMarriage(person('wife'), [
        { union: { unionType: 'SECONDARY' }, partner: person('second', 4) },
        { union: { unionType: 'PRIMARY' }, partner: person('first', 2) },
      ]),
    ).toBe(2);
  });

  it('stays unknown when no partner has one', () => {
    expect(generationThroughMarriage(person('wife'), [])).toBeUndefined();
  });
});

describe('withMarriedInGenerations', () => {
  it("gives a married-in wife her husband's đời", () => {
    const result = withMarriedInGenerations(
      tree([person('founder', 1), person('wife')], [union('u1', 'founder', 'wife')]),
    );

    expect(result.persons.find((p) => p.id === 'wife')?.generation).toBe(1);
  });

  it('returns the same payload when nothing changes', () => {
    const input = tree([person('founder', 1)], []);

    expect(withMarriedInGenerations(input)).toBe(input);
  });
});
