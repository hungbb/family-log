/**
 * The members feature's public surface (doc 07 §2).
 *
 * `MyJoinRequests` is exported because it renders on `/me`, which belongs to the account feature:
 * somebody waiting on a request is not a member of anything yet, so there is no clan-scoped page
 * that could hold it.
 */
export { MembersPage } from './pages/MembersPage';
export { MyJoinRequests } from './components/MyJoinRequests';
// The claim feature's assign dialog picks from the member list (F6.6), so the query is part of
// this feature's surface rather than something a sibling reaches into.
export { useMembers } from './hooks/useMembers';
