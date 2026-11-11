/**
 * Where a person was heading when they were sent to sign in.
 *
 * `RequireAuth` puts the path in the navigation state before redirecting to `/login`. Two places
 * have to read it back and agree: the login form, which navigates once the session starts, and
 * `RequireAnonymous`, which re-renders the instant the session exists and redirects a signed-in
 * visitor away from `/login`. When only the form knew about the path, the guard's redirect to `/`
 * landed last and every deep link, and every expired session, ended on the clan's default page.
 */

/** The fallback: the index route, which sends a member to their active clan. */
export const HOME_PATH = '/';

/**
 * The stashed path, or null when there is none worth trusting.
 *
 * Narrowed rather than cast: navigation state is whatever the previous route put there, and an
 * unchecked `as` would turn a stale entry into an open redirect.
 */
export function readReturnPath(state: unknown): string | null {
  if (typeof state !== 'object' || state === null || !('from' in state)) {
    return null;
  }
  const { from } = state;
  if (typeof from !== 'string' || !from.startsWith('/')) {
    return null;
  }
  // Only same-origin paths. "//evil.example" is a protocol-relative URL, and browsers read
  // "/\evil.example" the same way.
  if (from.startsWith('//') || from.startsWith('/\\')) {
    return null;
  }
  return from;
}

/** Where to send somebody who is now signed in: back where they were going, or home. */
export function returnPathOrHome(state: unknown): string {
  return readReturnPath(state) ?? HOME_PATH;
}
