import { create } from 'zustand';

/**
 * Which clan the shell is currently showing (FR-C8).
 *
 * Persisted, because a person who works in one family's records should not have to pick it again on
 * every reload — and because the alternative, defaulting to whichever clan sorts first, silently
 * shows the wrong family to anyone in two.
 *
 * **The stored id is a preference, never an authority.** It is validated against the caller's
 * memberships on boot (`useActiveClan`), and every request that uses it is re-checked server-side;
 * a stale id from a clan the person has since left resolves to a 404 and is cleared, which is
 * exactly what should happen.
 *
 * No clan *data* lives here. That belongs to TanStack Query (doc 07 §6); this holds one id.
 */

/** Namespaced: `localStorage` is shared per origin. */
const ACTIVE_CLAN_KEY = 'giaky.activeClanId';

interface ActiveClanState {
  clanId: string | null;
  /** Records a deliberate switch, and remembers it for the next visit. */
  setActiveClan: (clanId: string | null) => void;
}

/**
 * `localStorage` throws rather than returning null in a few real situations — private windows, and
 * any browser configured to block site data. A remembered clan is a convenience, so both accessors
 * degrade to "nothing remembered" instead of taking the application down.
 */
function readStoredClanId(): string | null {
  try {
    return window.localStorage.getItem(ACTIVE_CLAN_KEY);
  } catch {
    return null;
  }
}

function writeStoredClanId(clanId: string | null): void {
  try {
    if (clanId === null) {
      window.localStorage.removeItem(ACTIVE_CLAN_KEY);
    } else {
      window.localStorage.setItem(ACTIVE_CLAN_KEY, clanId);
    }
  } catch {
    // The choice still holds for this tab; it just will not survive a reload.
  }
}

export const useActiveClanStore = create<ActiveClanState>((set) => ({
  clanId: readStoredClanId(),

  setActiveClan: (clanId) => {
    writeStoredClanId(clanId);
    set({ clanId });
  },
}));
