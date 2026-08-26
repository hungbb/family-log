import { useCallback } from 'react';

import { logout as logoutRequest, type TokenPair } from '../api/endpoints/auth';
import { peekStoredRefreshToken, useSessionStore } from '../stores/useSessionStore';

/**
 * The session, as a component sees it.
 *
 * A thin read over `useSessionStore` so components do not each reach into the store and pick their
 * own slice — and so signing out is one call that always does both halves: tell the server, then
 * forget locally.
 */
export function useAuth() {
  const status = useSessionStore((state) => state.status);
  const user = useSessionStore((state) => state.user);
  const signedIn = useSessionStore((state) => state.signedIn);
  const signedOut = useSessionStore((state) => state.signedOut);

  const startSession = useCallback(
    (tokens: TokenPair) => {
      signedIn(tokens);
    },
    [signedIn],
  );

  const endSession = useCallback(async () => {
    const refreshToken = peekStoredRefreshToken();
    // Clear locally first. If the network call fails the person is still signed out here, which is
    // what they asked for; the server-side revocation is best-effort and the token expires anyway.
    signedOut();
    if (refreshToken !== null) {
      try {
        await logoutRequest(refreshToken);
      } catch {
        // Already signed out locally. Nothing useful to tell anyone.
      }
    }
  }, [signedOut]);

  return {
    status,
    user,
    isAuthenticated: status === 'authenticated',
    isResolving: status === 'loading',
    startSession,
    endSession,
  };
}
