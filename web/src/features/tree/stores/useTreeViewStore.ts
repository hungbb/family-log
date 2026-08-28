import { create } from 'zustand';

import { type Orientation } from '../model/layout';

/**
 * What the canvas is showing right now (F5.8).
 *
 * **View state, not data.** The tree itself belongs to TanStack Query (doc 07 §6); this holds where
 * somebody has panned to, what they have collapsed, and which node they are on. Keeping it in a
 * store rather than in `TreeCanvas` is what lets the toolbar, the minimap and the keyboard handler
 * all drive the same viewport without threading a dozen callbacks through the canvas.
 *
 * **Reset when the clan changes.** A viewport is a position in one family's tree and means nothing
 * in another's; a collapsed set is worse, because the ids do not exist there at all. `openClan`
 * clears everything unless it is the clan already open.
 *
 * Deliberately not persisted. Where somebody was looking three days ago is not a preference, and
 * restoring a scroll position into a tree that has since been edited lands them somewhere arbitrary.
 */

export type ViewMode = 'canvas' | 'list';

interface TreeViewState {
  clanId: string | null;
  mode: ViewMode;
  orientation: Orientation;
  /** Canvas units per screen pixel. Clamped by {@link ZOOM}. */
  scale: number;
  /** Screen-space offset of the canvas origin. */
  translate: { x: number; y: number };
  /** Slot keys whose descendants are hidden. */
  collapsed: Set<string>;
  /** The slot the keyboard is on, and the one a click selects. */
  focusedSlotKey: string | null;
  /** The person whose drawer is open, if any. */
  openPersonId: string | null;
  /** Branch to light up; everything else dims. Null for no highlighting. */
  highlightBranchId: string | null;

  openClan: (clanId: string) => void;
  setMode: (mode: ViewMode) => void;
  setOrientation: (orientation: Orientation) => void;
  setViewport: (viewport: { scale: number; translate: { x: number; y: number } }) => void;
  zoomBy: (factor: number, origin?: { x: number; y: number }) => void;
  toggleCollapsed: (slotKey: string) => void;
  expandAll: () => void;
  focusSlot: (slotKey: string | null) => void;
  openPerson: (personId: string | null) => void;
  highlightBranch: (branchId: string | null) => void;
}

/** Far enough out to see a five-thousand-person clan, far enough in to read a lunar date. */
export const ZOOM = { min: 0.08, max: 2.5, step: 1.25 } as const;

const INITIAL = {
  mode: 'canvas' as ViewMode,
  orientation: 'vertical' as Orientation,
  scale: 1,
  translate: { x: 0, y: 0 },
  collapsed: new Set<string>(),
  focusedSlotKey: null,
  openPersonId: null,
  highlightBranchId: null,
};

export const useTreeViewStore = create<TreeViewState>((set) => ({
  clanId: null,
  ...INITIAL,

  openClan: (clanId) => {
    set((state) => (state.clanId === clanId ? state : { clanId, ...INITIAL }));
  },

  setMode: (mode) => {
    set({ mode });
  },

  setOrientation: (orientation) => {
    // The old translate was a position in the other orientation's coordinate space, so keeping it
    // would drop the viewer somewhere arbitrary. The canvas re-fits after this.
    set({ orientation, translate: { x: 0, y: 0 } });
  },

  setViewport: ({ scale, translate }) => {
    set({ scale: clampScale(scale), translate });
  },

  /**
   * Zooms about a point in screen space — the pointer under a wheel, or the centre for a button.
   *
   * The translate correction is what keeps the thing under the cursor under the cursor. Without it,
   * zooming walks the tree off the screen and every zoom has to be followed by a pan.
   */
  zoomBy: (factor, origin) => {
    set((state) => {
      const scale = clampScale(state.scale * factor);
      if (origin === undefined) return { scale };
      const ratio = scale / state.scale;
      return {
        scale,
        translate: {
          x: origin.x - (origin.x - state.translate.x) * ratio,
          y: origin.y - (origin.y - state.translate.y) * ratio,
        },
      };
    });
  },

  toggleCollapsed: (slotKey) => {
    set((state) => {
      const collapsed = new Set(state.collapsed);
      if (!collapsed.delete(slotKey)) collapsed.add(slotKey);
      return { collapsed };
    });
  },

  expandAll: () => {
    set({ collapsed: new Set<string>() });
  },

  focusSlot: (focusedSlotKey) => {
    set({ focusedSlotKey });
  },

  openPerson: (openPersonId) => {
    set({ openPersonId });
  },

  highlightBranch: (highlightBranchId) => {
    set({ highlightBranchId });
  },
}));

function clampScale(scale: number): number {
  return Math.min(ZOOM.max, Math.max(ZOOM.min, scale));
}
