import { describe, expect, it } from 'vitest';

import { NARROW_VIEWPORT_WIDTH, sidebarStartsCollapsed } from './viewport';

describe('sidebarStartsCollapsed', () => {
  it('starts collapsed on a phone, where the open sidebar would leave no room for the page', () => {
    // NFR-10's floor, and the common phone widths above it.
    for (const width of [360, 375, 390, 414, 430]) {
      expect(sidebarStartsCollapsed(width), `${String(width)} px`).toBe(true);
    }
  });

  it('starts open where there is room for both', () => {
    for (const width of [768, 1024, 1280, 1440, 1920]) {
      expect(sidebarStartsCollapsed(width), `${String(width)} px`).toBe(false);
    }
  });

  it('changes over exactly at the breakpoint', () => {
    expect(sidebarStartsCollapsed(NARROW_VIEWPORT_WIDTH - 1)).toBe(true);
    expect(sidebarStartsCollapsed(NARROW_VIEWPORT_WIDTH)).toBe(false);
  });

  it('leaves a phone at least 296 px of page once the rail is collapsed', () => {
    const RAIL = 64;

    expect(360 - RAIL).toBeGreaterThanOrEqual(296);
  });
});
