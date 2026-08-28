/**
 * The permissions feature's public surface (doc 07 §2).
 *
 * The hooks are not here: `usePermissions` and its siblings live in `src/hooks/`, because gating
 * happens everywhere — the sidebar, the members page, the branch tree, a shared `PermissionGate` in
 * `components/` — and a hook that only the permissions feature could import would force every one of
 * those to ask the question a different way.
 */
export { PermissionsPage } from './pages/PermissionsPage';
export { MemberPermissionsDrawer } from './components/MemberPermissionsDrawer';
export { GrantDialog } from './components/GrantDialog';
export { describeGrant, describeGrants } from './model/describe';
