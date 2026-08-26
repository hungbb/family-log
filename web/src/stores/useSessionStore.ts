import { create } from 'zustand';

import { configureAuth } from '../api/client';
import { refreshSession, type TokenPair, type UserSummary } from '../api/endpoints/auth';

/**
 * The session: who is signed in, and the two tokens that keep them so.
 *
 * **Where the tokens live, and why.** The access token is held in memory only — a variable in a
 * closure is not reachable from `localStorage`, so a script that manages to run on the page cannot
 * simply read it, and it dies with the tab. The refresh token is in `localStorage`, which is the
 * trade ADR 0003 records: the API and the web client are not same-site, so a `Secure; HttpOnly;
 * SameSite=Strict` cookie is not available to us, and the alternative — a session that does not
 * survive a page reload — is not a product anyone would use. Rotation with reuse detection on the
 * server is what limits the damage if one is stolen.
 *
 * The store holds no server data beyond the signed-in account (doc 07 §6). The profile page reads
 * `/me` through TanStack Query like everything else; `user` here is what the shell needs to render
 * an account menu without a request.
 */

/** Where the refresh token is kept. Namespaced, because `localStorage` is shared per origin. */
const REFRESH_TOKEN_KEY = 'giaky.refreshToken';

export type SessionStatus =
  /** The bootstrap has not finished. Render a splash rather than a login flash. */
  'loading' | 'authenticated' | 'anonymous';

interface SessionState {
  status: SessionStatus;
  accessToken: string | null;
  user: UserSummary | null;

  /** Records a successful sign-in or refresh. */
  signedIn: (tokens: TokenPair) => void;
  /** Clears everything, in memory and in storage. */
  signedOut: () => void;
  /** Marks the bootstrap finished with nobody signed in. */
  resolvedAnonymous: () => void;
  /** Keeps the shell's copy of the account in step after a profile edit. */
  userUpdated: (user: UserSummary) => void;
}

/**
 * `localStorage` throws rather than returning null in a few real situations — Safari's private
 * mode historically, and any browser configured to block site data. A session that cannot be
 * persisted is a worse experience than one that cannot start at all, so both accessors degrade to
 * "no stored token" instead of taking the application down.
 */
function readStoredRefreshToken(): string | null {
  try {
    return window.localStorage.getItem(REFRESH_TOKEN_KEY);
  } catch {
    return null;
  }
}

function writeStoredRefreshToken(token: string | null): void {
  try {
    if (token === null) {
      window.localStorage.removeItem(REFRESH_TOKEN_KEY);
    } else {
      window.localStorage.setItem(REFRESH_TOKEN_KEY, token);
    }
  } catch {
    // Nothing to do: the session still works for this tab, it just will not survive a reload.
  }
}

export const useSessionStore = create<SessionState>((set) => ({
  status: 'loading',
  accessToken: null,
  user: null,

  signedIn: (tokens) => {
    writeStoredRefreshToken(tokens.refreshToken);
    set({ status: 'authenticated', accessToken: tokens.accessToken, user: tokens.user });
  },

  signedOut: () => {
    writeStoredRefreshToken(null);
    set({ status: 'anonymous', accessToken: null, user: null });
  },

  resolvedAnonymous: () => {
    set({ status: 'anonymous', accessToken: null, user: null });
  },

  userUpdated: (user) => {
    set({ user });
  },
}));

/** Whether a stored refresh token exists — the only reason to attempt a bootstrap refresh. */
export function hasStoredSession(): boolean {
  return readStoredRefreshToken() !== null;
}

/**
 * The stored refresh token.
 *
 * Exported for exactly one caller: signing out, which has to tell the server which session to end
 * before it forgets the token. Nothing else has a reason to read it, and the narrow export is the
 * reminder.
 */
export function peekStoredRefreshToken(): string | null {
  return readStoredRefreshToken();
}

/**
 * The renewal currently in flight, if any.
 *
 * The client has a single-flight guard of its own (doc 07 §4.2), and this is not a duplicate of it:
 * that one coalesces the requests that failed together, while this one coalesces *callers* — the
 * bootstrap effect, which StrictMode runs twice in development, and the client, which may already
 * be refreshing. Two renewals would present the same refresh token twice, and the second
 * presentation is precisely what the server treats as theft and answers by revoking the chain. The
 * client would sign itself out.
 */
let renewalInFlight: Promise<string | null> | null = null;

/**
 * Exchanges the stored refresh token for a new pair.
 *
 * Used by the bootstrap on page load and by the client when a request comes back with an expired
 * token. Returns the new access token, or null when there is no session to renew — the caller
 * decides what that means, because "no session on page load" and "session died mid-flight" deserve
 * different treatment.
 */
export function renewSession(): Promise<string | null> {
  renewalInFlight ??= performRenewal().finally(() => {
    renewalInFlight = null;
  });
  return renewalInFlight;
}

async function performRenewal(): Promise<string | null> {
  const stored = readStoredRefreshToken();
  if (stored === null) {
    return null;
  }
  try {
    const tokens = await refreshSession(stored);
    useSessionStore.getState().signedIn(tokens);
    return tokens.accessToken;
  } catch {
    // The token was revoked, expired, or its chain was killed by a reuse detection. Whatever the
    // reason, this browser no longer has a session, and keeping the dead token would mean retrying
    // with it on every page load.
    useSessionStore.getState().signedOut();
    return null;
  }
}

/**
 * Hands the client its way in to the session.
 *
 * At module scope, so it happens on first import and no request can be made before the bearer
 * token is reachable. The client imports nothing from here, which is what keeps the cycle out.
 */
configureAuth({
  getAccessToken: () => useSessionStore.getState().accessToken,
  refresh: renewSession,
  onSessionEnded: () => {
    useSessionStore.getState().signedOut();
  },
});
