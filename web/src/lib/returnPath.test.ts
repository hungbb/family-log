import { describe, expect, it } from 'vitest';

import { HOME_PATH, readReturnPath, returnPathOrHome } from './returnPath';

describe('readReturnPath', () => {
  it('returns the path RequireAuth stashed, query string and all', () => {
    const state = { from: '/clans/e3223fd9/persons?q=nguyen&page=2' };

    expect(readReturnPath(state)).toBe('/clans/e3223fd9/persons?q=nguyen&page=2');
  });

  it('is null when nothing was stashed', () => {
    expect(readReturnPath(null)).toBeNull();
    expect(readReturnPath(undefined)).toBeNull();
    expect(readReturnPath({})).toBeNull();
    expect(readReturnPath('/clans/x/tree')).toBeNull();
  });

  it('refuses anything that is not a same-origin path', () => {
    const hostile: unknown[] = [
      'https://evil.example/login',
      '//evil.example',
      '/\\evil.example',
      'javascript:alert(1)',
      'clans/x/tree',
      '',
      42,
      null,
      { pathname: '/clans/x/tree' },
    ];

    for (const from of hostile) {
      expect(readReturnPath({ from }), JSON.stringify(from)).toBeNull();
    }
  });
});

describe('returnPathOrHome', () => {
  it('sends a visitor who followed a deep link back to it after signing in', () => {
    expect(returnPathOrHome({ from: '/clans/e3223fd9/claims' })).toBe('/clans/e3223fd9/claims');
  });

  it('falls back to home when they came to the login page directly', () => {
    expect(returnPathOrHome(null)).toBe(HOME_PATH);
    expect(returnPathOrHome({ from: '//evil.example' })).toBe(HOME_PATH);
  });
});
