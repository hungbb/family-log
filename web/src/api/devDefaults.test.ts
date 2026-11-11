import { readFileSync } from 'node:fs';

import { describe, expect, it } from 'vitest';

/** A committed file, read as text: the point is what a fresh clone contains, not what is loaded. */
function committed(pathFromWeb: string): string {
  return readFileSync(new URL(`../../${pathFromWeb}`, import.meta.url), 'utf8');
}

/**
 * `git clone && npm install && npm run dev` has to produce a working application (README §3).
 *
 * It did not. `VITE_API_BASE_URL` had no default anywhere — `.env.example` is a template, and the
 * `.env.local` it tells you to create is git-ignored — so the client fell back to the empty origin
 * and every request went to the Vite dev server: `POST http://localhost:5173/api/v1/auth/register`
 * → 404, and a registration form that says "something went wrong". The API ships its `dev`
 * defaults in the repository; the web did not.
 *
 * These read the files rather than `import.meta.env`, because a developer's own `.env.local` would
 * make the second kind of test pass on their machine and nowhere else.
 */
describe('development defaults', () => {
  it('name the local API, so a fresh clone reaches it', () => {
    const origin = /^VITE_API_BASE_URL=(.*)$/m.exec(committed('.env.development'))?.[1];

    expect(origin).toBe('http://localhost:8080');
  });

  it('agree with the template somebody copies to override them', () => {
    const template = /^VITE_API_BASE_URL=(.*)$/m.exec(committed('.env.example'))?.[1];
    const development = /^VITE_API_BASE_URL=(.*)$/m.exec(committed('.env.development'))?.[1];

    expect(development).toBe(template);
  });

  it('are not ignored by git, unlike every other .env file', () => {
    const rules = committed('../.gitignore')
      .split('\n')
      .map((line) => line.trim());

    // The blanket rule has to stay — `.env.local` is where real overrides (and mistakes) live.
    expect(rules).toContain('.env.*');
    expect(rules.indexOf('!.env.development')).toBeGreaterThan(rules.indexOf('.env.*'));
  });
});
