/**
 * How the shell responds to the width it is given.
 *
 * Not the responsive pass — Phase 8 (R8.5) owns making every page work down to 360 px. This is the
 * one decision without which a phone cannot use the application at all: whether the 240 px sidebar
 * starts open. On a 390 px screen it left 150 px for everything else; every page behind it was
 * unreadable, and the only fix was a toggle that somebody had to find first.
 */

/** Below this the sidebar starts as its 64 px icon rail. AntD's `md` breakpoint. */
export const NARROW_VIEWPORT_WIDTH = 768;

/** Whether the sidebar should start collapsed for a viewport this wide. */
export function sidebarStartsCollapsed(viewportWidth: number): boolean {
  return viewportWidth < NARROW_VIEWPORT_WIDTH;
}
