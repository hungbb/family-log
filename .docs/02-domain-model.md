# 02 — Domain Model (DDD)

> Companion to [01-product-requirements.md](01-product-requirements.md). Defines bounded contexts, aggregates, invariants, and domain events. The physical schema lives in [03-data-model.md](03-data-model.md).

---

## 1. Bounded Contexts

Gia Ký is one deployable service split into five modules with explicit boundaries. Cross-context access goes through published application services or domain events — **never** through another context's repositories or entities.

```
┌──────────────────┐      ┌────────────────────┐      ┌──────────────────┐
│  Identity        │      │  ClanOrg           │      │  Lineage         │
│  (User, Session) │      │  (Clan, Branch,    │      │  (Person, Union, │
│                  │      │   Membership,      │      │   Parentage)     │
│                  │      │   JoinRequest)     │      │                  │
└────────┬─────────┘      └─────────┬──────────┘      └────────┬─────────┘
         │ userId                   │ clanId / branchId        │ personId
         │                          │                          │
         └──────────────┬───────────┴──────────────┬───────────┘
                        ▼                          ▼
              ┌────────────────────┐    ┌─────────────────────┐
              │  Authorization     │    │  Membership Linking │
              │  (Grant, Scope,    │    │  (NodeClaim,        │
              │   PermissionCheck) │    │   PersonUserLink)   │
              └────────────────────┘    └─────────────────────┘
                        ▲                          │
                        └──── consults ────────────┘

              ┌──────────────────────────────────────────┐
              │  Supporting: Audit, Notification, Media  │
              └──────────────────────────────────────────┘
```

| Context | Responsibility | Owns |
|---------|----------------|------|
| **Identity** | Authentication, account lifecycle, sessions. | `User`, `RefreshToken`, `VerificationToken` |
| **ClanOrg** | Clan and branch structure, membership, join requests. | `Clan` (aggregate root), `Branch`, `ClanMembership`, `ClanJoinRequest` |
| **Lineage** | The family tree itself: nodes and relationships. | `Person` (aggregate root), `Union`, `Parentage` |
| **Authorization** | Grants and the permission-resolution engine. | `PermissionGrant`, `PermissionScope`, `AccessDecision` |
| **Linking** | Mapping user accounts onto person nodes. | `NodeClaimRequest`, and the `linkedUserId` invariant on `Person` |
| **Supporting** | Audit log, notifications, media storage. | `AuditEntry`, `Notification`, `MediaObject` |

### 1.1 Context relationships

- **Identity → all**: upstream. Other contexts hold only a `UserId` value object; they never load `User` entities.
- **ClanOrg → Lineage**: partnership. `Branch.branchRootPersonId` points into Lineage; Lineage stores `clanId` and `branchId` as value objects. Referential consistency is enforced by application services and by DB foreign keys, not by object graphs.
- **Authorization → ClanOrg + Lineage**: conformist consumer. Scope resolution needs branch paths and person ancestry, exposed via two read ports: `BranchHierarchyPort` and `LineageAncestryPort`.
- **Linking → Lineage**: Linking issues commands to Lineage (`linkUser`, `unlinkUser`) and never mutates `Person` rows directly.

## 2. Aggregates & Invariants

### 2.1 `User` (Identity)

```
User
 ├─ id: UserId (UUID)
 ├─ email: EmailAddress (unique, normalized)
 ├─ passwordHash: PasswordHash
 ├─ displayName: String
 ├─ avatarUrl: MediaRef?
 ├─ locale: Locale (default vi-VN)
 ├─ status: ACTIVE | PENDING_VERIFICATION | SUSPENDED | DEACTIVATED
 └─ audit: createdAt, updatedAt, lastLoginAt
```

**Invariants**
- I-U1 `email` is unique across active users, stored normalized.
- I-U2 A `SUSPENDED` or `DEACTIVATED` user can neither authenticate nor hold effective grants.
- I-U3 `passwordHash` never leaves the aggregate; no DTO may expose it.

### 2.2 `Clan` (ClanOrg — aggregate root)

```
Clan
 ├─ id: ClanId
 ├─ clanCode: ClanCode (immutable, unique, shareable)
 ├─ name, description, originPlace, coverImage
 ├─ visibility: PRIVATE (v1)
 ├─ rootBranchId: BranchId
 └─ branches: Branch[]      (entity within the aggregate)

Branch  (entity)
 ├─ id: BranchId
 ├─ clanId: ClanId
 ├─ parentBranchId: BranchId?
 ├─ path: MaterializedPath   e.g. "/root/chi-2/chi-2-1"
 ├─ branchCode: BranchCode (unique, shareable)
 ├─ name, description
 ├─ branchRootPersonId: PersonId?
 └─ archivedAt: Instant?
```

**Invariants**
- I-C1 `clanCode` is generated once and never changes.
- I-C2 Exactly one branch per clan has `parentBranchId == null` — the root branch.
- I-C3 The branch graph is a tree: no cycles, one parent per branch, all nodes reachable from the root branch.
- I-C4 `path` is always consistent with `parentBranchId`; re-parenting rewrites the whole descendant subtree's paths in one transaction.
- I-C5 `branchRootPersonId`, when set, references a non-deleted Person in the same clan.
- I-C6 A branch cannot be archived while it has non-archived children.
- I-C7 Branch depth ≤ 10 (guards path length and UI nesting).

### 2.3 `ClanMembership` (ClanOrg)

```
ClanMembership
 ├─ id, clanId, userId
 ├─ homeBranchId: BranchId?
 ├─ status: ACTIVE | LEFT | REMOVED
 ├─ joinedAt, leftAt
 └─ invitedBy / approvedBy: UserId?
```

**Invariants**
- I-M1 At most one `ACTIVE` membership per (clan, user).
- I-M2 Membership is a prerequisite for holding any grant in that clan; revoking membership deactivates the user's grants in that clan.
- I-M3 A clan always retains at least one active `CLAN_ADMIN` (enforced when removing members or revoking grants).

### 2.4 `ClanJoinRequest` (ClanOrg)

```
ClanJoinRequest
 ├─ id, clanId, targetBranchId?, requesterUserId
 ├─ message: String?
 ├─ status: PENDING | APPROVED | REJECTED | CANCELLED | EXPIRED
 ├─ decidedBy: UserId?, decidedAt, decisionReason
 └─ expiresAt (createdAt + 90d)
```

**Invariants**
- I-J1 At most one `PENDING` request per (requester, clan, targetBranch).
- I-J2 A request cannot be created by a user with an `ACTIVE` membership in that clan.
- I-J3 Only a decider holding `MEMBER_APPROVE` covering the target scope may decide the request.
- I-J4 State transitions are one-way: `PENDING → {APPROVED, REJECTED, CANCELLED, EXPIRED}`.

### 2.5 `Person` (Lineage — aggregate root)

```
Person
 ├─ id: PersonId
 ├─ clanId: ClanId              (tenancy boundary)
 ├─ branchId: BranchId          (organizational placement, derived-then-stored)
 ├─ name: PersonName { fullName, familyName?, givenName?, alias?, posthumousName? }
 ├─ gender: MALE | FEMALE | OTHER | UNKNOWN
 ├─ lifeStatus: ALIVE | DECEASED | UNKNOWN
 ├─ birth: PartialDate?         (value object)
 ├─ death: PartialDate?
 ├─ burialPlace: String?
 ├─ generation: Int?            (đời; 1 = founding ancestor)
 ├─ birthOrder: Int?            (thứ tự sinh, sibling ordering)
 ├─ avatarUrl: MediaRef?
 ├─ notes: RichText?            (sanitized HTML)
 ├─ linkedUserId: UserId?       (nullable — decoupled by default)
 ├─ isFoundingAncestor: Boolean
 ├─ version: Long               (optimistic locking)
 └─ deletedAt: Instant?         (soft delete)
```

**Invariants**
- I-P1 `fullName` is non-blank after trimming.
- I-P2 If both `birth` and `death` are known and comparable, `birth ≤ death`.
- I-P3 `lifeStatus == DECEASED` implies a death date may exist but is not required; `ALIVE` forbids a death date.
- I-P4 `linkedUserId` is unique per (clanId, linkedUserId) among non-deleted persons — one node per user per clan.
- I-P5 A linked user must have an `ACTIVE` membership in the person's clan.
- I-P6 At most one `isFoundingAncestor == true` person per clan.
- I-P7 Soft-deleted persons are excluded from every tree query and from all uniqueness checks except I-P4's index predicate.
- I-P8 `branchId` always references a branch in the same clan.

### 2.6 `Parentage` (Lineage)

```
Parentage
 ├─ id, clanId
 ├─ parentPersonId, childPersonId
 ├─ parentRole: FATHER | MOTHER
 ├─ nature: BIOLOGICAL | ADOPTED | STEP | FOSTER
 └─ unionId: UnionId?          (set when the child was attached via a union)
```

**Invariants**
- I-R1 `parentPersonId != childPersonId`.
- I-R2 Both persons belong to the same clan.
- I-R3 At most one edge per (child, parentRole) — one father and one mother.
- I-R4 Adding an edge must not create a cycle: the parent must not be a descendant of the child.
- I-R5 A child's `generation`, when both it and the parent's are known, satisfies `child.generation == parent.generation + 1`; recomputation cascades to descendants.

### 2.7 `Union` (Lineage)

```
Union
 ├─ id, clanId
 ├─ partnerAId, partnerBId
 ├─ type: PRIMARY | SECONDARY | SEQUENTIAL
 ├─ status: ACTIVE | ENDED | WIDOWED | UNKNOWN
 ├─ startDate: PartialDate?, endDate: PartialDate?
 └─ notes: String?
```

**Invariants**
- I-N1 `partnerAId != partnerBId`, both in the same clan.
- I-N2 At most one non-deleted union per unordered partner pair.
- I-N3 At most one `PRIMARY` union per person at a time (`ACTIVE` status).
- I-N4 Partners must not be in a direct ancestor/descendant relationship (guards obvious data entry errors; overridable with a `force` flag by `CLAN_ADMIN`).

### 2.8 `PermissionGrant` (Authorization)

```
PermissionGrant
 ├─ id, clanId, granteeUserId
 ├─ permission: CLAN_VIEW | TREE_EDIT | MEMBER_APPROVE
 │             | NODE_CLAIM_APPROVE | ROLE_MANAGE | CLAN_ADMIN
 ├─ scopeType: CLAN | BRANCH | SUBTREE
 ├─ scopeRefId: UUID?          (branchId or personId; null when scopeType = CLAN)
 ├─ grantedBy: UserId, grantedAt
 ├─ expiresAt: Instant?
 └─ revokedAt: Instant?, revokedBy: UserId?
```

**Invariants**
- I-G1 `scopeType == CLAN` ⇒ `scopeRefId == null`; otherwise `scopeRefId` is required and resolves within the same clan.
- I-G2 `CLAN_ADMIN` may only be granted at `CLAN` scope.
- I-G3 The grantee holds an `ACTIVE` membership in the clan.
- I-G4 A granter must hold `ROLE_MANAGE` (or `CLAN_ADMIN`) covering the target scope **and** already hold the permission being granted at an equal-or-wider scope (no escalation, FR-Z5).
- I-G5 Duplicate active grants for the same (user, permission, scope) tuple are idempotent — the second grant refreshes `expiresAt` rather than inserting a row.
- I-G6 Revocation is soft (`revokedAt`) to preserve the audit trail.

Full resolution semantics: [04-authorization-model.md](04-authorization-model.md).

### 2.9 `NodeClaimRequest` (Linking)

```
NodeClaimRequest
 ├─ id, clanId, personId, requesterUserId
 ├─ evidence: String?
 ├─ status: PENDING | APPROVED | REJECTED | CANCELLED
 └─ decidedBy, decidedAt, decisionReason
```

**Invariants**
- I-K1 The target person is non-deleted and has `linkedUserId == null` at both submission and approval time (re-checked on approval).
- I-K2 The requester has an `ACTIVE` membership in the person's clan.
- I-K3 At most one `PENDING` claim per (requester, person), and per (requester, clan) only one claim may be approved.
- I-K4 The decider holds `NODE_CLAIM_APPROVE` covering the person's scope, and cannot be the requester (no self-approval unless the decider holds `CLAN_ADMIN`).

## 3. Value Objects

| VO | Shape | Notes |
|----|-------|-------|
| `PartialDate` | `{ year?, month?, day?, precision: EXACT\|MONTH\|YEAR\|RANGE\|UNKNOWN, circa: bool, rangeEnd?, lunarText? }` | Immutable; comparison returns a three-valued result (`BEFORE`, `AFTER`, `INDETERMINATE`). |
| `PersonName` | `{ fullName, familyName?, givenName?, alias?, posthumousName? }` | `fullName` is the display source of truth; parts are optional and search-indexed. |
| `ClanCode` / `BranchCode` | `SURNAME-XXXX` uppercase, Crockford base32 suffix | Collision-checked at generation; never reissued. |
| `MaterializedPath` | `/seg/seg/seg`, segments are branch UUID short-forms | Used for `LIKE 'path%'` descendant queries. |
| `PermissionScope` | `{ type, refId }` | Comparable: `CLAN ⊃ BRANCH ⊃ SUBTREE` where applicable. |
| `RichText` | sanitized HTML + plain-text projection | Sanitization happens on write, never on read. |
| `MediaRef` | `{ storageKey, contentType, width, height, variants }` | Resolved to URLs by the media service. |

## 4. Domain Events

Events are published in-process (Spring `ApplicationEventPublisher`) after transaction commit, and consumed by Audit, Notification, and read-model projectors. Names are past-tense facts.

| Event | Published by | Consumers |
|-------|--------------|-----------|
| `UserRegistered`, `UserVerified` | Identity | Notification, Audit |
| `ClanCreated` | ClanOrg | Authorization (bootstrap `CLAN_ADMIN`), Audit |
| `BranchCreated`, `BranchMoved`, `BranchArchived` | ClanOrg | Authorization (scope cache invalidation), Audit |
| `JoinRequestSubmitted`, `JoinRequestDecided` | ClanOrg | Notification, Audit |
| `MembershipGranted`, `MembershipRevoked` | ClanOrg | Authorization (deactivate grants), Notification, Audit |
| `PersonCreated`, `PersonUpdated`, `PersonSoftDeleted` | Lineage | Ancestry projection, Audit, Search index |
| `ParentageAdded`, `ParentageRemoved` | Lineage | Ancestry (closure) projection, generation recompute, Audit |
| `UnionCreated`, `UnionUpdated`, `UnionRemoved` | Lineage | Audit |
| `PermissionGranted`, `PermissionRevoked` | Authorization | Notification, Audit |
| `NodeClaimSubmitted`, `NodeClaimDecided` | Linking | Notification, Audit |
| `PersonLinkedToUser`, `PersonUnlinkedFromUser` | Linking | Notification, Audit |

## 5. Domain Services & Ports

| Service | Context | Responsibility |
|---------|---------|----------------|
| `PermissionResolver` | Authorization | Answers `can(user, permission, target)`; the single decision point (see doc 04). |
| `ScopeExpander` | Authorization | Expands a grant scope into a matcher over branch paths and person ancestry. |
| `LineageGraphService` | Lineage | Cycle detection, generation recomputation, ancestry/descendant queries. |
| `AncestryProjection` | Lineage | Maintains the `person_ancestry` closure table on parentage changes. |
| `BranchPathService` | ClanOrg | Path generation and subtree path rewrites on re-parenting. |
| `CodeGenerator` | ClanOrg | Collision-free `ClanCode` / `BranchCode` generation. |
| `PersonUserLinker` | Linking | The only writer of `Person.linkedUserId`; enforces I-P4, I-P5, I-K1. |

**Ports (outbound)**: `MediaStorage`, `NotificationSender`, `AuditWriter`, `Clock`, `IdGenerator`. All are interfaces in the domain layer with adapters in infrastructure, so the domain stays framework-free and unit-testable.

## 6. Consistency & Transactions

- One transaction per aggregate mutation; cross-aggregate work (e.g. approve join → create membership → bootstrap grants) is orchestrated in an application service inside a single transaction because all data is in one database.
- **Optimistic locking** on `Person`, `Clan`, and `Branch` via `@Version`.
- The `person_ancestry` projection is updated **inside** the same transaction as the parentage change, so authorization can never read a stale ancestry.
- Long-running rewrites (branch re-parent of a large subtree) are bounded by the depth limit (I-C7) and executed as a single statement using recursive SQL.

## 7. Anti-Corruption Rules

1. No context may import another's JPA entities. Cross-context data crosses as DTOs or IDs.
2. Controllers never touch repositories; the call chain is `Controller → ApplicationService → DomainService/Repository`.
3. The domain layer has no Spring, Jakarta Persistence, or Jackson annotations — those live in the infrastructure mapping layer. (See [06-backend-architecture.md](06-backend-architecture.md) for the pragmatic exception policy.)
4. Every repository method that reads tenant data takes a `ClanId` argument; a repository method without one must be justified in review.
