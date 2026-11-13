import { create } from 'zustand';

import { type TreeScheme } from '@/theme/tokens.ts';

import { type Orientation } from '../model/layout';

/**
 * What the canvas is showing right now (F5.8).
 *
 * **View state, not data.** The tree itself belongs to TanStack Query (doc 07 §6); this holds where
 * somebody has panned to, what they have collapsed, and which node they are on. Keeping it in a
 * store rather than in `TreeCanvas` is what lets the toolbar, the minimap and the keyboard handler
 * all drive the same viewport without threading a dozen callbacks through the canvas.
 *
 * **Not the open person.** Which drawer is open is in the URL instead (`?person=`, see
 * `router/paths.ts` and #48), because that one piece of view state has to be shareable, survive a
 * reload and answer to the Back button. A copy here would be a second source of truth for it.
 *
 * **Reset when the clan changes.** A viewport is a position in one family's tree and means nothing
 * in another's; a collapsed set is worse, because the ids do not exist there at all. `openClan`
 * clears everything unless it is the clan already open.
 *
 * Deliberately not persisted. Where somebody was looking three days ago is not a preference, and
 * restoring a scroll position into a tree that has since been edited lands them somewhere arbitrary.
 * The two exceptions are preferences rather than positions — the canvas's light or dark scheme and
 * whether the legend is open (#61) — and they survive both a reload and a change of clan.
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
  /** Branch to light up; everything else dims. Null for no highlighting. */
  highlightBranchId: string | null;
  /** The toolbar and canvas fill the whole window. */
  fullscreen: boolean;
  /**
   * The "show N generations" choice the collapsed set was last built from, or null for all.
   * Collapsing or expanding by hand afterwards adjusts the set without clearing this — the limit
   * is the starting point somebody chose, not a rule the canvas keeps enforcing.
   */
  generationLimit: number | null;
  /** The canvas's own colour scheme (#61). Independent of the app chrome, which is light-only. */
  scheme: TreeScheme;
  /** Whether the on-canvas legend is expanded. */
  legendOpen: boolean;

  openClan: (clanId: string) => void;
  setMode: (mode: ViewMode) => void;
  setOrientation: (orientation: Orientation) => void;
  setViewport: (viewport: { scale: number; translate: { x: number; y: number } }) => void;
  zoomBy: (factor: number, origin?: { x: number; y: number }) => void;
  toggleCollapsed: (slotKey: string) => void;
  expandAll: () => void;
  /** Shows `levels` generations by replacing the collapsed set; null clears the limit. */
  limitGenerations: (levels: number | null, collapsed: Set<string>) => void;
  focusSlot: (slotKey: string | null) => void;
  highlightBranch: (branchId: string | null) => void;
  setFullscreen: (fullscreen: boolean) => void;
  setScheme: (scheme: TreeScheme) => void;
  setLegendOpen: (open: boolean) => void;
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
  highlightBranchId: null,
  fullscreen: false,
  generationLimit: null,
};

const SCHEME_KEY = 'giaky.treeScheme';
const LEGEND_KEY = 'giaky.treeLegend';

/**
 * Reads a stored preference. Storage can be missing or throw (a private window, blocked site data,
 * a test environment), and a preference that cannot be read is simply the default.
 */
function readPreference(key: string): string | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writePreference(key: string, value: string): void {
  try {
    if (typeof localStorage !== 'undefined') localStorage.setItem(key, value);
  } catch {
    // Not remembered across reloads; the choice still applies now.
  }
}

/** The stored scheme, else the system's, else light. */
function initialScheme(): TreeScheme {
  const stored = readPreference(SCHEME_KEY);
  if (stored === 'light' || stored === 'dark') return stored;
  const prefersDark =
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-color-scheme: dark)').matches;
  return prefersDark ? 'dark' : 'light';
}

export const useTreeViewStore = create<TreeViewState>((set) => ({
  clanId: null,
  ...INITIAL,
  scheme: initialScheme(),
  // Closed until asked for: it would otherwise cover part of the tree on every first visit.
  legendOpen: readPreference(LEGEND_KEY) === 'open',

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
    set({ collapsed: new Set<string>(), generationLimit: null });
  },

  limitGenerations: (generationLimit, collapsed) => {
    set({ generationLimit, collapsed });
  },

  focusSlot: (focusedSlotKey) => {
    set({ focusedSlotKey });
  },

  highlightBranch: (highlightBranchId) => {
    set({ highlightBranchId });
  },

  setFullscreen: (fullscreen) => {
    set({ fullscreen });
  },

  setScheme: (scheme) => {
    writePreference(SCHEME_KEY, scheme);
    set({ scheme });
  },

  setLegendOpen: (legendOpen) => {
    writePreference(LEGEND_KEY, legendOpen ? 'open' : 'closed');
    set({ legendOpen });
  },
}));

function clampScale(scale: number): number {
  return Math.min(ZOOM.max, Math.max(ZOOM.min, scale));
}
