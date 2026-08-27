/**
 * The members feature's public surface (doc 07 §2).
 *
 * `MyJoinRequests` is exported because it renders on `/me`, which belongs to the account feature:
 * somebody waiting on a request is not a member of anything yet, so there is no clan-scoped page
 * that could hold it.
 */
export { MembersPage } from './pages/MembersPage';
export { MyJoinRequests } from './components/MyJoinRequests';
