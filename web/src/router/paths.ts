/**
 * Absolute paths the router redirects to, and the query that names a person inside the tree.
 *
 * Absolute on purpose. A relative `<Navigate to="tree">` is resolved against wherever it is
 * rendered, and on a catch-all (`*`) route that is the *whole* unmatched URL — so the fallback for
 * `/clans/x/nope` navigated to `/clans/x/nope/tree`, which is also unmatched, which navigated to
 * `/clans/x/nope/tree/tree`, and so on until the browser gave up. A path built from the clan id
 * resolves to the same place from anywhere.
 */

/**
 * The query parameter that names the person whose drawer is open (FR-T5, #48).
 *
 * A query parameter rather than a path segment (`/tree/persons/{id}`) because the drawer is not a
 * page: it is the tree with one card open over it, and a nested route would have to re-render the
 * canvas underneath it on every open. The tree route stays one route, and the person is a
 * parameter of the view — which is also what makes it composable with the filters a tree URL may
 * grow later.
 */
export const PERSON_PARAM = 'person';

/** The page a clan opens on: its tree (doc 07 §6). */
export function clanHomePath(clanId: string): string {
  return treePath(clanId);
}

/**
 * The tree of a clan, or the deep link to one person in it.
 *
 * `/clans/{clanId}/tree?person={personId}` is what a search result links to and what the address
 * bar reads while a drawer is open (FR-T5, #48): it survives a reload, it can be pasted to a
 * relative, and because opening a person pushes it, Back closes the drawer rather than leaving the
 * tree.
 */
export function treePath(clanId: string, personId?: string): string {
  const path = `/clans/${encodeURIComponent(clanId)}/tree`;
  return personId === undefined || personId === ''
    ? path
    : `${path}?${PERSON_PARAM}=${encodeURIComponent(personId)}`;
}

/**
 * The person a tree URL names, or null when it names none.
 *
 * A blank `?person=` is "none", not a person whose id is the empty string: it is what deleting the
 * id by hand leaves behind, and an empty id would send the drawer to ask the API for `/persons/`.
 */
export function personIdIn(params: URLSearchParams): string | null {
  const personId = params.get(PERSON_PARAM)?.trim() ?? '';
  return personId === '' ? null : personId;
}

/**
 * The same query with the open person set, or cleared when it is null.
 *
 * Every other parameter is carried over, so opening a card cannot drop whatever else the URL was
 * holding. A fresh `URLSearchParams` rather than a mutated one, because the instance handed out by
 * `useSearchParams` belongs to the router and is read by the render that is still on screen.
 */
export function withPerson(params: URLSearchParams, personId: string | null): URLSearchParams {
  const next = new URLSearchParams(params);
  if (personId === null || personId === '') {
    next.delete(PERSON_PARAM);
  } else {
    next.set(PERSON_PARAM, personId);
  }
  return next;
}
