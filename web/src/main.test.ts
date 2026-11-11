import { readFileSync } from 'node:fs';

import { describe, expect, it } from 'vitest';

const PATCH = '@ant-design/v5-patch-for-react-19';

/** The module specifiers `main.tsx` imports, in source order. */
function importsOf(source: string): string[] {
  return [...source.matchAll(/^import\s+(?:[^'"]+\s+from\s+)?['"]([^'"]+)['"];?$/gm)].map(
    (match) => match[1] ?? '',
  );
}

/**
 * Ant Design 5 on React 19 needs its compatibility shim loaded before anything renders.
 *
 * Without it every page load logged "Warning: [antd: compatible] antd v5 support React is 16 ~ 18"
 * as a console error, and AntD's static render path fell back to an API React 19 no longer has.
 * The shim is one side-effect import, which is exactly the kind of line an import sorter or a tidy-up
 * moves or removes — so its presence and its position are asserted here.
 */
describe('the application entry point', () => {
  const imports = importsOf(readFileSync(new URL('./main.tsx', import.meta.url), 'utf8'));

  it('loads the Ant Design React 19 shim', () => {
    expect(imports).toContain(PATCH);
  });

  it('loads it before React, the app, and therefore every AntD component', () => {
    expect(imports[0]).toBe(PATCH);
    expect(imports.indexOf(PATCH)).toBeLessThan(imports.indexOf('./App'));
  });

  it('is installed as a runtime dependency, not a dev one', () => {
    const manifest = JSON.parse(
      readFileSync(new URL('../package.json', import.meta.url), 'utf8'),
    ) as { dependencies: Record<string, string>; devDependencies: Record<string, string> };

    expect(Object.keys(manifest.dependencies)).toContain(PATCH);
    expect(Object.keys(manifest.devDependencies)).not.toContain(PATCH);
  });
});
