import { Navigate, useParams } from 'react-router';

import { clanHomePath } from './paths';

/**
 * Sends a clan URL that is not a page — the bare `/clans/{id}`, or anything unrecognised below it —
 * to that clan's tree.
 *
 * By absolute path, built from the clan id. The relative `<Navigate to="tree">` this replaces was
 * fine on the index route and an infinite loop on the catch-all; see `paths.ts`.
 */
export function ClanHomeRedirect() {
  const { clanId = '' } = useParams();
  return <Navigate to={clanHomePath(clanId)} replace />;
}
