/**
 * The lineage feature's public surface (doc 07 §2).
 *
 * `PersonPicker` is exported because Phase 3's grant dialog needs it: a `SUBTREE` scope names a
 * person, and picking that person is the same search this feature already owns. Reaching into
 * `tree/components/PersonPicker` directly would work and would be the first crack in the boundary.
 */
export { TreePage } from './pages/TreePage';
export { PersonSearchPage } from './pages/PersonSearchPage';
export { PersonPicker } from './components/PersonPicker';
export { PersonDrawer } from './components/PersonDrawer';
export { useTree, usePersonSearch } from './hooks/useTree';
