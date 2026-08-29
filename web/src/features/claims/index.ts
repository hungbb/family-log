/**
 * The claims feature's public surface (doc 07 §2).
 *
 * Most of it is exported, and that is the shape of the feature rather than a leak: claiming a node
 * touches four screens somebody else owns — the tree drawer, the profile page, the shell banner and
 * the account page — because "which account is this person" is a question those screens are already
 * showing the answer to.
 */
export { ClaimsPage } from './pages/ClaimsPage';
export { MyClaims } from './components/MyClaims';
export { FindYourselfBanner } from './components/FindYourselfBanner';
export { AssignMemberDialog } from './components/AssignMemberDialog';
export { SelfProfileForm } from './components/SelfProfileForm';
export { useMyLinkedNode, useUnlinkUser } from './hooks/useClaims';
