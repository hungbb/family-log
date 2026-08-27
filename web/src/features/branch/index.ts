/**
 * The branch feature's public surface (doc 07 §2).
 *
 * `useBranches` is exported because the member screens need the clan's branches — an approver
 * places somebody into one, and the member list filters by one. Reaching into
 * `branch/hooks/useBranches` directly would work and would be the first crack in the boundary.
 */
export { BranchesPage } from './pages/BranchesPage';
export { useBranches } from './hooks/useBranches';
