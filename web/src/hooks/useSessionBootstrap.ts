import { useEffect } from 'react';

import { hasStoredSession, renewSession, useSessionStore } from '../stores/useSessionStore';

/**
 * Resolves the session once, before the application renders anything that depends on it (task F1.8).
 *
 * The store starts in `loading` rather than `anonymous` on purpose. If it started anonymous, every
 * reload of a signed-in session would paint the login screen for the length of one network round
 * trip and then replace it — the flash that makes a web application feel like a set of pages rather
 * than an application.
 *
 * @returns whether the session has been resolved either way
 */
export function useSessionBootstrap(): boolean {
  const status = useSessionStore((state) => state.status);
  const resolvedAnonymous = useSessionStore((state) => state.resolvedAnonymous);

  useEffect(() => {
    if (status !== 'loading') {
      return;
    }
    if (!hasStoredSession()) {
      resolvedAnonymous();
      return;
    }
    // `renewSession` moves the store to `authenticated` or `anonymous` itself, so there is nothing
    // to do with the result here. StrictMode runs this effect twice in development; the second run
    // sees a status that is no longer `loading` and does nothing.
    void renewSession();
  }, [status, resolvedAnonymous]);

  return status !== 'loading';
}
