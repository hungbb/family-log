# Phase 6 — Node Claiming & User Linking

**Goal:** connect living users to their place in the tree — by request-and-approve, or by direct assignment from an authorized editor.

**Estimate:** ~1.5 weeks · **Depends on:** [Phase 5](phase-5-tree-visualizer.md) · **Next:** [Phase 7](phase-7-media-content.md)

**Requirements:** FR-N1 … FR-N6, goal G7

---

## 1. Scope

**In scope** — the `NodeClaimRequest` workflow (submit, cancel, approve, reject), routing to `NODE_CLAIM_APPROVE` holders by scope, direct assignment by editors, unlinking, the unlinked-node browser, in-app notifications for claim events, and the "find me in the tree" experience.

**Out of scope** — automatic matching or suggestion of candidate nodes (post-v1), profile-field propagation beyond the opt-in toggle in FR-N6, email notification (Phase 8).

## 2. Backend Tasks

| # | Task | Detail |
|---|------|--------|
| B6.1 | `linking/domain`: `NodeClaimRequest` with invariants I-K1…I-K4 | State machine `PENDING → APPROVED \| REJECTED \| CANCELLED` |
| B6.2 | **`PersonUserLinker`** — the only writer of `person.linked_user_id` | Enforces I-P4 (one node per user per clan), I-P5 (active membership), I-K1 (node still unlinked at approval) |
| B6.3 | `ClaimService`: submit, cancel, list-inbox (scope-filtered), approve, reject | Approval re-checks the node is still unlinked and returns `409 NODE_ALREADY_LINKED` if it was taken |
| B6.4 | Direct assignment: `POST /persons/{id}/link-user` | Requires `NODE_CLAIM_APPROVE` covering the node; the target must be an active clan member (FR-N4) |
| B6.5 | Unlink: `DELETE /persons/{id}/link-user` | Allowed for approvers in scope **or** the linked user themselves (FR-N5) |
| B6.6 | `GET /persons/unlinked` | Candidate browser with search, generation and branch filters |
| B6.7 | Self-service profile exception (doc 04 §6) | A linked user may edit a whitelisted subset of their own node (avatar, contact, notes) without `TREE_EDIT` — its own service method, explicitly enumerated fields, no resolver special-casing |
| B6.8 | Opt-in profile propagation (FR-N6) | Per-field flags, default off; a change to the user profile updates the node only for enabled fields |
| B6.9 | Notifications: claim submitted (to approvers), claim decided (to requester), node linked/unlinked (to the affected user) | |
| B6.10 | Audit: `CLAIM_SUBMITTED`, `CLAIM_DECIDED`, `PERSON_LINKED`, `PERSON_UNLINKED` | |
| B6.11 | Concurrency guard | Two approvals racing on the same node: the second returns `409`, verified with a concurrent integration test |

## 3. Frontend Tasks

| # | Task | Detail |
|---|------|--------|
| F6.1 | "Find yourself in the tree" onboarding banner for members with no linked node | Appears after joining a clan |
| F6.2 | Unlinked-node browser | Search by name, filter by generation/branch, with tree context preview for each candidate |
| F6.3 | Claim submission dialog with an evidence field | Explains that an editor will review it |
| F6.4 | "My claims" section on `/me` with status and decision reason | |
| F6.5 | Claim inbox for approvers | Shows the requester, the node with its tree context (parents and children), evidence, and approve/reject with reason |
| F6.6 | Direct-assign action on unlinked nodes for authorized editors | Member picker restricted to active clan members; a clear confirmation step |
| F6.7 | Linked-node indicators in the tree and detail drawer | Avatar ring plus "This is you" on the caller's own node |
| F6.8 | Unlink action with confirmation, available to approvers and to the linked user | |
| F6.9 | Self-service profile editing on one's own node | Only the whitelisted fields are editable; the rest are visibly read-only with an explanation |
| F6.10 | Notification list and unread badge (in-app, polled every 60 s) | |
| F6.11 | i18n for the whole claim vocabulary | Wording matters: "nhận vị trí trong gia phả", not a literal translation of "claim" |

## 4. Migrations

| Version | Contents |
|---------|----------|
| `V11__create_node_claim_requests.sql` | `node_claim_requests` + the partial unique index for I-K3 and the inbox index |
| `V13__create_notifications.sql` | `notifications` + the unread index |
| `V11b__add_profile_propagation_flags.sql` | Per-field opt-in flags on `persons` (or a small side table) for FR-N6 |

## 5. Tests

**Unit** — claim state machine, all illegal transitions rejected; `PersonUserLinker` invariants in isolation.

**Integration** —
- Claim → approve → `linked_user_id` set; the requester's `/me` shows the linked node.
- A second claim by the same user in the same clan → `409 USER_ALREADY_LINKED_IN_CLAN`.
- A claim on an already-linked node → `409 NODE_ALREADY_LINKED`.
- An approver **outside** the node's scope → `403`; an approver inside → `200`.
- Self-approval refused unless the approver holds `CLAN_ADMIN` (I-K4).
- Direct assignment of a non-member → `422`.
- Unlink by the linked user themselves → `204`.
- Two approvers approving concurrently: exactly one succeeds.
- Self-service edit succeeds for whitelisted fields and returns `403` for `fullName` or relationships.

**Frontend (manual)** — the unlinked browser excludes linked nodes; the claim dialog blocks submission when the user already has a linked node in the clan, with an explanatory message; approvers see only in-scope claims.

**Manual walkthrough** — user joins → browses unlinked nodes → claims → editor approves → user sees "This is you" on the tree and edits their own avatar placeholder without holding `TREE_EDIT`.

## 6. Demo Script

1. New member joins the clan; the banner invites them to find themselves in the tree.
2. They search their own name, see two candidate nodes with tree context, and claim the right one with a short note.
3. The *Chi Hai* editor's inbox shows the claim with the node's parents and children for verification, and approves it.
4. The member reloads the tree: their node shows "This is you", and they edit their own notes without any `TREE_EDIT` grant.
5. An editor directly assigns another member to an unlinked node; that member is notified.
6. The member unlinks themselves; the node returns to the unlinked pool.

## 7. Exit Criteria

- [ ] FR-N1…N6 complete end to end.
- [ ] `person.linked_user_id` is written by exactly one class (grep-verified).
- [ ] Race conditions on approval provably resolve to a single winner.
- [ ] Claim approval never grants edit rights (doc 04 §6) — asserted by test.
- [ ] The self-service field whitelist is explicit, enumerated, and tested for both allow and deny.

## 8. Notes & Risks

- **Being in the tree ≠ being able to edit it.** The most likely design mistake in this phase is quietly granting `TREE_EDIT` on approval because it feels helpful. Keep them separate; an admin can always grant a subtree scope afterwards.
- **The self-service exception is a real hole if it drifts.** Keep the whitelist in one constant, test both directions, and require an ADR to extend it.
- **Duplicate-person risk.** Members whose node does not exist yet will be tempted to create one. Route that through a claim-adjacent flow ("request to be added") rather than open creation, or the tree accumulates duplicates. If that flow is deferred, say so explicitly in the UI copy.
