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

- [x] FR-N1…N6 complete end to end. (FR-N6 for the avatar waits on Phase 7's upload; see §9.)
- [x] `person.linked_user_id` is written by exactly one class (build-verified by `LinkWriterTest`).
- [x] Race conditions on approval provably resolve to a single winner (`LinkConcurrencyIT`).
- [x] Claim approval never grants edit rights (doc 04 §6) — asserted by test.
- [x] The self-service field whitelist is explicit, enumerated, and tested for both allow and deny.

Ticked in the verification addendum at the end of §9, once the integration suite and the demo had
actually been run.

## 8. Notes & Risks

- **Being in the tree ≠ being able to edit it.** The most likely design mistake in this phase is quietly granting `TREE_EDIT` on approval because it feels helpful. Keep them separate; an admin can always grant a subtree scope afterwards.
- **The self-service exception is a real hole if it drifts.** Keep the whitelist in one constant, test both directions, and require an ADR to extend it.
- **Duplicate-person risk.** Members whose node does not exist yet will be tempted to create one. Route that through a claim-adjacent flow ("request to be added") rather than open creation, or the tree accumulates duplicates. If that flow is deferred, say so explicitly in the UI copy.

## 9. Phase Report

> Appended on completion, per doc 09 §5.

### What shipped

**Backend.** All of B6.1–B6.11, as a new `linking` bounded context plus a small `notification` one.

- **B6.2 — `PersonUserLinker` is the only writer of `linked_user_id`, and that is a build failure
  rather than a grep.** `LinkWriterTest` (ArchUnit) asserts two things: only `PersonUserLinker` calls
  the `PersonLinkPort` write methods, and only `lineage.application.PersonLinkGateway` calls the
  repository ones. The linker is pure domain with no `PermissionResolver` and no grant service in
  reach — it *cannot* grant `TREE_EDIT`, which is §8's named risk made structural rather than
  remembered.
- **The Linking↔Lineage seam.** Doc 06 §2 puts the linker in Linking; the ArchUnit rule from Phase 0
  forbids any context importing another's `domain` or `infrastructure`. So Lineage publishes
  `PersonLinkGateway` (an application service), Linking declares `PersonLinkPort` / `ClanMemberPort`
  in its own domain, and two adapters in `linking.infrastructure` join them. The gateway's write
  methods are deliberately **not** `@Transactional`: they join the caller's transaction, so the
  compare-and-set, the claim decision and the audit row commit together or not at all.
- **B6.11 — the guard is the row, not the read.** `linkUserIfUnlinked` is a native
  `UPDATE … WHERE linked_user_id IS NULL AND deleted_at IS NULL` returning its affected-row count;
  the read before it exists only to produce a good error message. The partial unique index from V11
  and the per-clan uniqueness of `linked_user_id` catch the other side of the race. No in-memory
  double can prove this, which is why `LinkConcurrencyIT` fires two approvals through a
  `CyclicBarrier` on a real Postgres and asserts `containsExactlyInAnyOrder(200, 409)` plus exactly
  one linked row and one `APPROVED` claim.
- **B6.7 — the whitelist is a type and a two-column statement.** `updateSelfServiceFields` writes
  `avatar_media_id` and `notes_*` and nothing else, and its `WHERE linked_user_id = :userId` *is* the
  authorization — a caller who is not the linked person updates zero rows. `SelfProfileServiceTest`
  tests both directions. Extending it means editing a SQL statement with a comment above it saying
  an ADR is required, which is the closest thing to a lock a whitelist can have.
- **B6.8 — propagation lands on `alias`, never on `full_name`.** An account's display name is what
  somebody calls themselves this year; the recorded name is what the book says. The flags live in a
  side table (`person_profile_propagation`) because they are Linking's data about a Lineage row.
- **B6.9/B6.10** — `LinkingNotificationListener` routes `CLAIM_SUBMITTED` through
  `PermissionQueryService.holdersCovering(...)`, so an approver is notified only about claims they
  could actually decide. It stays silent on cancellations, on the link that a claim approval already
  notified about, and on self-unlinks. Audit gained `CLAIM_SUBMITTED`, `CLAIM_DECIDED`,
  `PERSON_LINKED`, `PERSON_UNLINKED`, `PROFILE_PROPAGATION_UPDATED`.

**Frontend.** All of F6.1–F6.11.

- `ClaimsPage` (two tabs), `UnlinkedNodeBrowser` (F6.2), `ClaimDialog` (F6.3), `MyClaims` on `/me`
  (F6.4), `ClaimInbox` (F6.5), `AssignMemberDialog` (F6.6), the avatar `LinkRing` and "This is you"
  in both the canvas and the drawer (F6.7), unlink with two different confirmations depending on
  whose link it is (F6.8), `SelfProfileForm` (F6.9), `NotificationBell` (F6.10), and the full en/vi
  vocabulary (F6.11).
- The bell lives in `components/layout`, not in a feature: the shell renders it and the shell may not
  import a feature (doc 07 §2), and its list spans every clan anyway.
- The badge polls a dedicated `unread-count` endpoint every 60 s and not in a background tab; the
  list is fetched when the bell opens.

**Migrations.** `V11__create_node_claim_requests.sql`, `V11_1__add_profile_propagation_flags.sql`,
`V13__create_notifications.sql`.

### Measured

| | |
|---|---|
| Backend unit tests | **607 pass** (531 before this phase) |
| Frontend | `eslint --max-warnings=0`, constraint script, `tsc --noEmit`, `vite build` all clean |
| Claims chunk | 377 KB raw / 110 KB gzipped, lazily loaded per route |

**Integration tests were written but not executed in the authoring environment** (since run; see
the verification addendum below) — it has no Docker
daemon and every `*IT` needs Testcontainers. `NodeClaimingIT`, `LinkConcurrencyIT` and
`LinkingSchemaIT` cover §5's integration list and must be run (`./mvnw verify`) on a machine with
Docker. Until that happens, §7's "race conditions provably resolve to a single winner" is
**argued but not demonstrated**, and the exit criteria are not met. The same applies to §5's manual
frontend checks and §6's demo script, neither of which was walked through against a running stack.

### Deviations from the design docs

1. **`V11_1` rather than `V11b`.** Flyway cannot parse a letter suffix in a version; `V3_1` set the
   precedent in this repo. The contents are §4's, unchanged.
2. **The propagation flags are a side table, not columns on `persons`.** §4 allowed either. A side
   table keeps a Linking concern out of the Lineage aggregate, and keeps `persons` from growing two
   columns that are null for all but a handful of rows.
3. **Three endpoints beyond doc 05.** `GET /clans/{id}/persons/linked-to-me` (204 when there is
   none — what the F6.1 banner asks), `GET|PATCH /clans/{id}/persons/{personId}/self-profile`
   (B6.7's own service method needed its own route rather than a special case inside
   `PATCH /persons/{id}`), and `GET /me/notifications/unread-count`. Doc 05 §4.10 and §4.2 have been
   updated to match.
4. **No `EXPIRED` claim status.** The state machine is exactly §2's four states. Expiry would need a
   scheduler nothing else in v1 has.

### Notes for the next phase

- **Avatars are still initials.** `SelfProfileInput.avatarMediaId` and the `propagate_avatar` flag
  are wired end to end, but there is no upload yet, so the self-service form offers notes and the two
  toggles and nothing to pick a photograph with. Phase 7 adds the picker; no backend change is
  needed for it.
- **"Request to be added" is not built.** §8's duplicate-person risk is real and the flow it asks for
  was not in §2 or §3. The browser's empty state says so in both languages ("ask an editor — you may
  not have been added yet") rather than pretending the search simply failed. If the flow is still
  deferred at M5, that copy is the only thing standing between a member and a duplicate node.
- **The claim inbox filters in memory after a paged read**, exactly as `JoinRequestService` does. At
  a few hundred pending claims that is fine; at a few thousand the page would need a scope-aware
  query. Nothing in v1 gets near it.

### Verification addendum (2026-11-28)

The integration suite and the demo were run against a real stack: PostgreSQL 16 under Docker, the
API on the `dev` profile, the web client on Vite, and the staging seed (`deploy/staging/seed.py`,
119 people over six generations). The browser steps were driven with Playwright, with screenshots at
each step.

**Integration tests.** `NodeClaimingIT`, `LinkConcurrencyIT` and `LinkingSchemaIT` pass, 28 tests,
on first run. The full `./mvnw verify` passes with the coverage gates: 741 unit tests and 260
integration tests after the fixes below. `LinkConcurrencyIT` passed on each of four runs, including
after the approval path changed.

**Demo script (§6).** All six steps work as written. The walkthrough also covered §5's three manual
frontend checks: the browser leaves out linked nodes, an already-linked member is told why they
cannot claim another, and an approver sees only claims in their scope. It also checked FR-N6 on the
running API: with the alias flag on, a display-name change lands on the node's alias, `full_name` is
untouched, and the linked user's own `PATCH` of `fullName` is a 403.

**Found and fixed.**

1. **The inbox total counted claims the approver could not see.** `ClaimService.inbox` filtered by
   scope *after* cutting the page, so `totalElements` was every claim in the clan. The badge on the
   inbox tab told the Chi Giáp editor "2" over a list of one. The other claim was a Chi Ất claim,
   which is a wrong number and a small leak of another chi's business. Pages could also come back
   short or empty before the last one. The filter now runs before the page is cut, over at most
   2,000 claims. `ClaimServiceTest` and `NodeClaimingIT` assert the total.
2. **A link left the claims it answered pending.** Approval declined other claims on the *same
   node*, but nothing declined the linked member's own claims on *other* nodes, and direct
   assignment declined neither. Those claims stayed in the inbox as decisions that could only end in
   `409`, and on the member's `/me` as "Waiting" for ever. Both routes publish `PersonLinked`. A
   handler in `LinkLifecycleListener` now calls `ClaimService.closeClaimsSettledBy`, inside the
   linking transaction, and that declines both sets with a reason and an audit row. Approval asks
   whether somebody else holds the node *before* it asks whether the claim is still pending. B6.3's
   `409 NODE_ALREADY_LINKED` is therefore still the answer for a node taken in the meantime, rather
   than "already rejected".
3. **Unlinking yourself fired a 403.** The self-service form is still mounted for a moment after the
   unlink, and the blanket invalidation refetched its flags as somebody no longer linked. The user
   never saw it, but it was a failed request on every self-unlink, the same kind of bug as #46.
   `invalidateAfterUnlink` marks those flags stale without refetching them. A Vitest case fails
   without the fix.
4. **Copy.** The claims page told an already-linked member to "ask an editor to unlink you first".
   FR-N5 lets them do it themselves, and the drawer offers it. Both languages now say so.

**Observed, not changed.**

- **Long-dead ancestors can be claimed.** Nothing in FR-N1 or I-K1 restricts a claim to the living,
  and the editor's review is the gate. A filter or a warning on `DECEASED` nodes in the browser
  would be a product decision, not a fix.
- **Decision reasons the server writes are English.** "You have been linked to …" and "Somebody else
  has been linked to this person." are stored on the claim and shown as-is, so a Vietnamese member
  reads English there. That was already true of the reason approval wrote before. Fixing it means
  storing a reason code and translating it in the client, which belongs to Phase 7's i18n pass.
- **The join-request inbox has the same total bug as fix 1** (`JoinRequestService.inbox`, Phase 2).
  It was left alone here to keep this change to Phase 6. The same fix applies.

