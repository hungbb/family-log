# 01 — Product Requirements (PRD)

> Project: **Gia Ký** — a digital *gia phả* (family tree / lineage) platform for Vietnamese families and clans.
> Source: [draft.md](draft.md). This document is the authoritative expansion of that draft.

---

## 1. Problem Statement

Vietnamese clans (*gia tộc*) maintain lineage records (*gia phả*) that are traditionally paper-bound, held by a single custodian (*trưởng tộc*), and hard to keep current. As families branch into sub-clans (*chi / nhánh*) across regions and generations, three problems appear:

1. **Single point of maintenance** — only one person can realistically update the book, so the record decays.
2. **No safe delegation** — handing edit rights to a branch head risks accidental edits to the root ancestors or to sibling branches.
3. **No link between the historical record and living people** — descendants cannot find "where am I in this tree" or contribute their own sub-lineage.

Gia Ký solves these by making the tree collaborative, with **branch-scoped permissions** and a **claim workflow** that maps living user accounts onto historical tree nodes.

## 2. Goals &amp; Non-Goals

### 2.1 Goals (v1)

- **G1.** Let an authorized user build an arbitrary lineage tree of `Person` nodes **without** requiring each node to have a user account.
- **G2.** Support hierarchical clans: a Root Clan containing Sub-Clans / Branches, each anchored at a `branchRootPersonId`.
- **G3.** Allow one user account to belong to **multiple** clans and branches simultaneously.
- **G4.** Provide fine-grained, **scopable** permissions (clan-wide, branch-scoped, or subtree-scoped) granted to *multiple* users — never a single hard-coded patriarch role.
- **G5.** Guarantee **branch editing isolation**: a branch editor can only mutate nodes inside their own subtree.
- **G6.** Provide an **integrated master view**: the Root Clan tree renders all branch subtrees as one seamless tree.
- **G7.** Support a **node claiming** workflow (user requests a node → authorized editor approves) and **direct assignment** by editors.
- **G8.** Ship a REST API (OpenAPI documented, JWT-secured) plus a React/TypeScript web client.

### 2.2 Non-Goals (v1, explicitly deferred)

- **N1.** Native mobile applications (the web client is responsive; native is post-v1).
- **N2.** GEDCOM import/export (tracked as a Phase 8 stretch item).
- **N3.** DNA/genetic matching, ancestry hints, or matching against external archives.
- **N4.** Real-time multi-cursor collaborative editing (the model is request/approve + optimistic locking, not CRDT).
- **N5.** Public/anonymous browsing of clan trees — v1 is authenticated-only, private-by-default.
- **N6.** Payments, subscriptions, or tenant billing.

## 3. Personas


| #   | Persona                | Vietnamese            | Needs                                                                                  |
| --- | ---------------------- | --------------------- | -------------------------------------------------------------------------------------- |
| P1  | **Clan Administrator** | Trưởng tộc / quản trị | Create the clan, define branches, grant permissions, approve joins.                    |
| P2  | **Branch Editor**      | Trưởng chi / nhánh    | Build and maintain the subtree under their branch root; approve claims in scope.       |
| P3  | **Clan Member**        | Thành viên            | Join by `clanCode`, browse the tree, claim their own node, keep their profile current. |
| P4  | **Guest / Viewer**     | Khách được mời        | Read-only access to a clan tree they were granted `CLAN_VIEW` on.                      |
| P5  | **Platform Operator**  | Vận hành hệ thống     | Health, backups, abuse handling, audit trail. (Minimal tooling in v1.)                 |


## 4. Ubiquitous Language (Glossary)


| Term           | Vietnamese            | Definition                                                                                                                              |
| -------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| **Clan**       | Gia tộc               | Top-level lineage group; the security and tenancy boundary. Has an immutable public `clanCode`.                                         |
| **Branch**     | Chi / Nhánh           | A named sub-group inside a clan, anchored at a `branchRootPerson`. Branches nest.                                                       |
| **Person**     | Nhân vật trong phả    | A node in the tree: living or deceased, with or without a linked user account.                                                          |
| **Union**      | Hôn phối              | A partnership between two persons (primary, secondary, or sequential marriage).                                                         |
| **Parentage**  | Quan hệ cha/mẹ – con  | A directed edge from a parent Person to a child Person, tagged with role and nature.                                                    |
| **Generation** | Đời                   | Integer depth of a Person relative to the clan's founding ancestor (đời 1).                                                             |
| **Membership** | Tư cách thành viên    | A user's accepted association with a clan (and optionally a home branch).                                                               |
| **Claim**      | Nhận vị trí trong phả | A user's request to be mapped onto an unlinked Person node.                                                                             |
| **Grant**      | Cấp quyền             | A (user, permission, scope) tuple stored in the authorization model.                                                                    |
| **Scope**      | Phạm vi               | Where a grant applies: the whole `CLAN`, one `BRANCH` (and its descendant branches), or one `SUBTREE` (a person and their descendants). |


## 5. Functional Requirements

Requirement IDs are referenced by the phase plans in [08-implementation-roadmap.md](08-implementation-roadmap.md).

### 5.1 Identity &amp; Account (FR-A)

- **FR-A1** A visitor can register with email + password; email is unique and normalized (trimmed, lowercased).
- **FR-A2** Passwords are stored with BCrypt (cost ≥ 12), minimum 8 characters, checked against a common-password deny list.
- **FR-A3** Login returns a short-lived JWT access token and a long-lived, revocable refresh token.
- **FR-A4** A user can view and edit their own account profile (display name, avatar, locale, contact).
- **FR-A5** A user can log out (revoking the presented refresh token) and revoke all active sessions.
- **FR-A6** Email verification and password reset are token-based with expiry (24 h / 1 h respectively).

### 5.2 Clan &amp; Branch (FR-C)

- **FR-C1** Any authenticated user can create a clan; the creator receives `CLAN_ADMIN` scoped to that clan.
- **FR-C2** A clan has: name, `clanCode` (immutable, shareable, e.g. `NGUYEN-7XQK`), description, origin place, founding-ancestor note, visibility (`PRIVATE` in v1), cover image.
- **FR-C3** A clan owns a branch tree; every clan has a root branch created with it.
- **FR-C4** A branch has: name, `branchCode`, optional `branchRootPersonId`, `parentBranchId`, description, and a materialized `path` for descendant queries.
- **FR-C5** A branch's `branchRootPerson` must be a Person in the same clan.
- **FR-C6** Creating a sub-branch requires `CLAN_ADMIN` on the clan, or `ROLE_MANAGE` covering the parent branch.
- **FR-C7** A branch may be renamed, re-parented, or archived. Re-parenting recomputes `path` for all descendants.
- **FR-C8** A user may hold memberships in unlimited clans; the UI exposes a clan switcher.

### 5.3 Join Requests (FR-J)

- **FR-J1** A user submits a join request using a `clanCode` (whole clan) or `branchCode` (specific branch), with an optional message.
- **FR-J2** The request is routed to every user holding `MEMBER_APPROVE` covering that scope.
- **FR-J3** An approver may approve (creating a `ClanMembership`), reject (with reason), or leave pending. Requests expire after 90 days.
- **FR-J4** Duplicate pending requests from one user to one scope are rejected at the API level.
- **FR-J5** A member can leave a clan; a `CLAN_ADMIN` can remove a member. The last `CLAN_ADMIN` can neither be removed nor demoted.

### 5.4 Person Nodes &amp; Relationships (FR-P)

- **FR-P1** A Person node can be created with **no** user account attached (decoupled tree building).
- **FR-P2** Person attributes: `fullName` (required), `familyName`, `givenName`, `alias` (tên thường gọi), `posthumousName` (tên húy), `gender` (`MALE|FEMALE`, required), `status` (`ALIVE|DECEASED|UNKNOWN`), birth/death dates, `avatarUrl`, rich-text `notes`, `burialPlace`, `generation`, `isFoundingAncestor`.
- **FR-P3** Dates support **partial precision**: exact date, month+year, year only, or an approximate range with a `circa` flag. Lunar (*âm lịch*) dates are captured as an optional parallel field.
- **FR-P4** A Person may have at most one father-role and one mother-role parent edge, each tagged `BIOLOGICAL|ADOPTED|STEP|FOSTER`.
- **FR-P5** The parentage graph must remain acyclic; the API rejects any edge that would create a cycle.
- **FR-P6** A Union links two persons with `type` (`PRIMARY|SECONDARY|SEQUENTIAL`), optional start/end dates, and `status` (`ACTIVE|ENDED|WIDOWED|UNKNOWN`). A person may hold multiple unions.
- **FR-P7** Children may be attached to a Union (both parents at once) or to individual parents.
- **FR-P8** Deletion is **soft** — a deleted Person keeps a `deletedAt` and is hidden from the tree. Hard delete is admin-only and blocked while the node has live edges.
- **FR-P9** Sibling display order follows `birthOrder` (thứ tự sinh), falling back to birth date, then creation order.
- **FR-P10** Every mutation records `createdBy` / `updatedBy` and appends to the audit log.
- **FR-P11** Concurrent edits are guarded by a `version` column (optimistic locking); a conflicting write returns `409`.

### 5.5 Tree Query &amp; Visualization (FR-T)

- **FR-T1** Fetch a clan's whole tree, or a subtree rooted at any person, bounded by a `depth` parameter.
- **FR-T2** The **master view** aggregates all branch subtrees into one seamless tree — branches are a permission/organizational overlay, never a data partition.
- **FR-T3** Nodes carry a `branchId` badge and a `canEdit` flag computed for the requesting user, so the UI can grey out out-of-scope nodes.
- **FR-T4** The client supports pan/zoom, collapse/expand, focus-on-person, and ancestor/descendant highlighting.
- **FR-T5** Search persons in a clan by name, alias, generation, and life status; results deep-link into the tree.
- **FR-T6** A person detail panel shows attributes, parents, unions, children, siblings, and the linked user (if any).

### 5.6 Authorization (FR-Z)

- **FR-Z1** Permission types: `CLAN_VIEW`, `TREE_EDIT`, `MEMBER_APPROVE`, `NODE_CLAIM_APPROVE`, `ROLE_MANAGE`, `CLAN_ADMIN`.
- **FR-Z2** Every grant carries a scope: `CLAN`, `BRANCH` (covers descendant branches), or `SUBTREE` (a person and their descendants).
- **FR-Z3** Multiple users may hold the same permission over the same or overlapping scopes; grants are additive (union), with no deny rules in v1.
- **FR-Z4** `TREE_EDIT` scoped to Branch C authorizes mutation of nodes inside C's subtree only — never sibling branches, never ancestors above the branch root.
- **FR-Z5** `ROLE_MANAGE` allows granting only permissions the granter already holds, at a scope equal to or narrower than their own (no privilege escalation).
- **FR-Z6** `CLAN_ADMIN` implies every other permission at clan scope.
- **FR-Z7** Grants may carry an optional `expiresAt`.
- **FR-Z8** Every authorization decision on a mutation path is logged together with the grant that permitted it.

### 5.7 Node Claiming (FR-N)

- **FR-N1** A clan member may submit a claim on an **unlinked** Person node in a clan they belong to, with evidence text.
- **FR-N2** Claims route to holders of `NODE_CLAIM_APPROVE` covering that node's scope.
- **FR-N3** Approving a claim sets `person.linkedUserId`. A user links to at most one Person **per clan**; a Person links to at most one user.
- **FR-N4** An authorized editor may **directly assign** an existing clan member to an unlinked node in their scope, skipping the request flow.
- **FR-N5** A link may be revoked by an authorized editor or by the linked user; revocation is audited.
- **FR-N6** Once linked, profile changes may optionally propagate to the Person node (opt-in per field, default off).

### 5.8 Media &amp; Content (FR-M)

- **FR-M1** Avatar and clan-cover uploads: JPEG/PNG/WebP, ≤ 5 MB, server-side re-encode, EXIF stripped, thumbnails generated.
- **FR-M2** Notes are rich text stored as sanitized HTML (server-side allow-list sanitization; no scripts, no remote iframes).
- **FR-M3** Uploaded images are stored on the backend server's local filesystem, behind a `MediaStorage` port. No third-party object storage in v1; an S3-compatible adapter is deferred post-v1, and the port keeps that a one-adapter swap.

### 5.9 Auditing &amp; Notifications (FR-X)

- **FR-X1** An append-only audit log records actor, action, target, clan, before/after snapshot, timestamp, IP, and the authorizing grant.
- **FR-X2** In-app notifications for: join request received/decided, claim received/decided, permission granted/revoked, node linked.
- **FR-X3** Email notification is opt-in per event category (v1 may ship in-app only; email is a Phase 8 item).

## 6. Non-Functional Requirements


| ID     | Area            | Requirement                                                                                                                    |
| ------ | --------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| NFR-1  | Performance     | Tree read for a 2,000-node clan &lt; 500 ms p95; person mutation &lt; 300 ms p95.                                              |
| NFR-2  | Scale target    | 10k clans, 200k persons, 50k users on a single Postgres primary. Largest single clan ≤ 20k persons.                            |
| NFR-3  | Security        | JWT-secured API, BCrypt passwords, OWASP Top-10 review before release, rate limiting on auth endpoints.                        |
| NFR-4  | Isolation       | Every query is clan-scoped; a cross-clan read/write is a defect class with mandatory regression tests.                         |
| NFR-5  | Availability    | Single-region deployment, daily backups, documented and tested restore procedure, RTO ≤ 4 h.                                   |
| NFR-6  | Observability   | Structured JSON logs with correlation IDs, Spring Actuator health/metrics, Prometheus scrape endpoint.                         |
| NFR-7  | Accessibility   | WCAG 2.1 AA for forms and navigation; the tree canvas offers an accessible list/table alternative.                             |
| NFR-8  | i18n            | Vietnamese is the default locale, English a full second locale. No hard-coded user-facing strings.                             |
| NFR-9  | Data protection | Living persons' birth dates and contact fields are visible only to clan members; soft-deleted data is recoverable for 30 days. |
| NFR-10 | Compatibility   | Latest two versions of Chrome, Edge, Firefox, Safari. Responsive from 360 px.                                                  |


## 7. Key Product Decisions


| #   | Decision                                            | Rationale                                                                                             |
| --- | --------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| D1  | Branches are an **overlay**, not a data partition.  | Satisfies FR-T2 (seamless master view) while still enabling FR-Z4 isolation through scoped grants.    |
| D2  | No `role` enum on membership; **permissions only**. | The draft explicitly rejects a single-patriarch model. Named roles can return later as grant bundles. |
| D3  | Person is decoupled from User by default.           | Historical ancestors have no accounts; linking is an optional, approved mapping.                      |
| D4  | Grants are additive, with no deny rules.            | Keeps resolution total, cheap, and explainable in the UI.                                             |
| D5  | Soft delete throughout the tree.                    | Lineage data is heritage data; accidental loss is unacceptable.                                       |
| D6  | Clan is the tenancy boundary for every query.       | Makes cross-tenant leakage a single, testable invariant.                                              |


## 8. Acceptance Scenarios (end-to-end)

1. **Build a lineage from scratch** — Admin creates a clan → adds the founding ancestor (đời 1) → adds three generations of descendants with unions → the master view renders the full tree.
2. **Delegate a branch** — Admin creates Branch C anchored at Uncle C (đời 14) → grants Uncle C `TREE_EDIT` scoped to Branch C → Uncle C edits nodes beneath himself successfully, and gets `403` when touching Uncle D's subtree or the đời-13 ancestor.
3. **Join and claim** — A member joins with a `clanCode` → an approver accepts → the member searches their own name → submits a claim → a branch editor approves → the node shows the member's account and avatar.
4. **Direct assignment** — A branch editor selects an unlinked node, picks an existing clan member, assigns directly; the member is notified.
5. **Multi-clan** — One user belongs to their paternal and maternal clans, switches clans in the UI, and sees distinct trees and distinct permissions in each.
6. **Escalation blocked** — A `ROLE_MANAGE` holder scoped to Branch C tries to grant `CLAN_ADMIN`, then a clan-wide `TREE_EDIT`; both are rejected with `403`.

## 9. Open Questions


| #   | Question                                                                     | Owner   | Default if unanswered                                      |
| --- | ---------------------------------------------------------------------------- | ------- | ---------------------------------------------------------- |
| Q1  | Should clan trees ever be publicly shareable via a read-only link?           | Product | No — private-only in v1.                                   |
| Q2  | Are lunar-calendar dates required at launch or in Phase 8?                   | Product | Field captured from Phase 4; converters deferred.          |
| Q3  | Is email delivery in scope for v1 (verification, notifications)?             | Product | In-app notifications in v1; email in Phase 8.              |
| Q4  | Do we need GEDCOM import to migrate existing digital records?                | Product | Deferred to post-v1.                                       |
| Q5  | Can one Person appear in two clans (e.g. a bride recorded by both families)? | Product | No — one Person row per clan; cross-clan linking deferred. |


