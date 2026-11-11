/**
 * Absolute paths the router redirects to.
 *
 * Absolute on purpose. A relative `<Navigate to="tree">` is resolved against wherever it is
 * rendered, and on a catch-all (`*`) route that is the *whole* unmatched URL — so the fallback for
 * `/clans/x/nope` navigated to `/clans/x/nope/tree`, which is also unmatched, which navigated to
 * `/clans/x/nope/tree/tree`, and so on until the browser gave up. A path built from the clan id
 * resolves to the same place from anywhere.
 */

/** The page a clan opens on: its tree (doc 07 §6). */
export function clanHomePath(clanId: string): string {
  return `/clans/${encodeURIComponent(clanId)}/tree`;
}
