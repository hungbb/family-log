import { matchRoutes, resolvePath } from 'react-router';
import { describe, expect, it } from 'vitest';

import { clanHomePath, personIdIn, treePath, withPerson } from './paths';

/** The clan subtree of `routes.tsx`, reduced to its paths. */
const clanRoutes = [
  {
    path: '/clans/:clanId',
    children: [
      { index: true },
      { path: 'tree' },
      { path: 'search' },
      { path: 'members' },
      { path: '*' },
    ],
  },
];

/** Whether a URL is a real page of a clan rather than something the catch-all picked up. */
function isAPage(pathname: string): boolean {
  const matched = matchRoutes(clanRoutes, pathname);
  return matched !== null && matched.at(-1)?.route.path !== '*';
}

describe('clanHomePath', () => {
  it('is the tree of that clan', () => {
    expect(clanHomePath('e3223fd9-126a-4260-bd7d-b72d10d4afde')).toBe(
      '/clans/e3223fd9-126a-4260-bd7d-b72d10d4afde/tree',
    );
  });

  it('is a real page, so following it ends the redirecting', () => {
    expect(isAPage(clanHomePath('x'))).toBe(true);
  });

  it('resolves to the same place from any unknown URL under the clan', () => {
    for (const lost of ['/clans/x/nope', '/clans/x/persons/123', '/clans/x/tree/extra']) {
      expect(isAPage(lost), `${lost} should be unmatched`).toBe(false);
      expect(resolvePath(clanHomePath('x'), lost).pathname).toBe('/clans/x/tree');
    }
  });

  it('unlike the relative "tree" it replaces, which never arrives', () => {
    // What `<Navigate to="tree">` on the catch-all did: each hop is still not a page.
    let location = '/clans/x/nope';
    for (let hop = 0; hop < 3; hop++) {
      location = resolvePath('tree', location).pathname;
      expect(isAPage(location), location).toBe(false);
    }
    expect(location).toBe('/clans/x/nope/tree/tree/tree');
  });

  it('keeps an odd id inside its own path segment', () => {
    expect(clanHomePath('a/b?c')).toBe('/clans/a%2Fb%3Fc/tree');
  });
});

describe('treePath', () => {
  it('names one person in the tree of one clan', () => {
    expect(treePath('c1', 'p1')).toBe('/clans/c1/tree?person=p1');
  });

  it('is the plain tree when it names nobody', () => {
    expect(treePath('c1')).toBe('/clans/c1/tree');
    expect(treePath('c1', '')).toBe('/clans/c1/tree');
  });

  it('is still the tree route with a person on it', () => {
    expect(isAPage(resolvePath(treePath('c1', 'p1')).pathname)).toBe(true);
  });

  it('escapes an id that would otherwise end the query or start another parameter', () => {
    expect(treePath('c1', 'a&b=c')).toBe('/clans/c1/tree?person=a%26b%3Dc');
    expect(
      personIdIn(new URLSearchParams(new URL(treePath('c1', 'a&b=c'), 'http://x').search)),
    ).toBe('a&b=c');
  });
});

describe('personIdIn', () => {
  it('reads the person a deep link names', () => {
    expect(personIdIn(new URLSearchParams('person=p1'))).toBe('p1');
  });

  it('is null when the URL names nobody, so the drawer stays shut', () => {
    expect(personIdIn(new URLSearchParams(''))).toBeNull();
    expect(personIdIn(new URLSearchParams('mode=list'))).toBeNull();
  });

  // A hand-edited URL is the ordinary way these arrive: somebody deletes the id to "close" the
  // drawer. An empty id must not become a request for `/persons/`.
  it('treats a blank or whitespace id as nobody', () => {
    expect(personIdIn(new URLSearchParams('person='))).toBeNull();
    expect(personIdIn(new URLSearchParams('person=%20%20'))).toBeNull();
  });
});

describe('withPerson', () => {
  it('sets the open person and clears them again', () => {
    const opened = withPerson(new URLSearchParams(''), 'p1');
    expect(opened.toString()).toBe('person=p1');
    expect(withPerson(opened, null).toString()).toBe('');
  });

  it('replaces the person already named rather than adding a second one', () => {
    expect(withPerson(new URLSearchParams('person=p1'), 'p2').toString()).toBe('person=p2');
  });

  it('leaves every other parameter alone', () => {
    const next = withPerson(new URLSearchParams('mode=list&q=an'), 'p1');
    expect(next.get('mode')).toBe('list');
    expect(next.get('q')).toBe('an');
    expect(withPerson(next, null).toString()).toBe('mode=list&q=an');
  });

  // `useSearchParams` hands out an instance the current render is still reading; mutating it in
  // place would change what is on screen without a re-render.
  it('does not touch the query it was given', () => {
    const current = new URLSearchParams('person=p1');
    withPerson(current, 'p2');
    expect(current.get('person')).toBe('p1');
  });

  it('round-trips through personIdIn', () => {
    expect(personIdIn(withPerson(new URLSearchParams(''), 'p1'))).toBe('p1');
    expect(personIdIn(withPerson(new URLSearchParams('person=p1'), null))).toBeNull();
  });
});
