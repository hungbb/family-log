import { type PersonSummary, type Union } from '@/api/endpoints/persons.ts';
import { type Tree } from '@/api/endpoints/tree.ts';

/**
 * The *đời* of somebody who married into the clan.
 *
 * The server stamps a generation only along the line of descent, so a wife who married in has none
 * of her own — and the canvas, the search box and the list all said "đời not known" for her. A *gia
 * phả* records her at her husband's *đời*, and that is what this reads: the partner of her
 * `PRIMARY` union first, then any other partner who has one. Somebody with a generation of their own
 * keeps it; somebody with no partner who has one stays unknown, which is the honest answer.
 */
export function generationThroughMarriage(
  person: PersonSummary,
  partners: { union: Pick<Union, 'unionType'>; partner: PersonSummary }[],
): number | undefined {
  if (person.generation !== undefined) return person.generation;
  const ordered = [...partners].sort(
    (left, right) =>
      Number(left.union.unionType !== 'PRIMARY') - Number(right.union.unionType !== 'PRIMARY'),
  );
  return ordered.find((link) => link.partner.generation !== undefined)?.partner.generation;
}

/**
 * The tree payload with every married-in spouse given their partner's *đời*.
 *
 * Applied as the query's `select`, so the canvas, the toolbar search and the list view all read the
 * same number. Returns the payload untouched when nobody needed one.
 */
export function withMarriedInGenerations(tree: Tree): Tree {
  const byId = new Map(tree.persons.map((person) => [person.id, person]));
  const partnersOf = new Map<string, { union: Union; partner: PersonSummary }[]>();
  for (const union of tree.unions) {
    const a = byId.get(union.partnerAId);
    const b = byId.get(union.partnerBId);
    if (a === undefined || b === undefined) continue;
    partnersOf.set(a.id, [...(partnersOf.get(a.id) ?? []), { union, partner: b }]);
    partnersOf.set(b.id, [...(partnersOf.get(b.id) ?? []), { union, partner: a }]);
  }

  const persons = tree.persons.map((person) => {
    if (person.generation !== undefined) return person;
    const generation = generationThroughMarriage(person, partnersOf.get(person.id) ?? []);
    return generation === undefined ? person : { ...person, generation };
  });
  return persons.some((person, index) => person !== tree.persons[index])
    ? { ...tree, persons }
    : tree;
}
