# 04 — Authorization Model

> The heart of Gia Ký. Implements FR-Z1–FR-Z8 and the branch-isolation guarantee (G5) from [01-product-requirements.md](01-product-requirements.md).

---

## 1. Principles

1. **Permissions, not roles.** There is no "patriarch" flag. Every capability is an explicit grant, and any number of users may hold it.
2. **Every grant is scoped.** A grant answers *what* (permission) and *where* (clan / branch / subtree).
3. **Additive, no deny.** Effective access is the union of a user's grants. There are no negative rules, so resolution is total, order-independent, and explainable in the UI ("you can edit this because you hold TREE_EDIT on Chi 2").
4. **One decision point.** All checks route through `PermissionResolver`. No controller, service, or query builds its own ad-hoc check.
5. **Deny by default.** Absence of a matching grant is denial. A target the resolver cannot classify is denial, never a fallthrough allow.
6. **Membership gates everything.** Grants are only effective while the user holds an `ACTIVE` membership in the clan.

## 2. Permission Catalogue

| Permission | Grants the ability to | Valid scopes | Implied by |
|------------|----------------------|--------------|-----------|
| `CLAN_VIEW` | Read the clan tree, person details, member list. | CLAN, BRANCH, SUBTREE | every other permission |
| `TREE_EDIT` | Create / update / soft-delete persons, parentages, unions. | CLAN, BRANCH, SUBTREE | `CLAN_ADMIN` |
| `MEMBER_APPROVE` | Decide join requests; remove members. | CLAN, BRANCH | `CLAN_ADMIN` |
| `NODE_CLAIM_APPROVE` | Decide node claims; directly assign a member to a node. | CLAN, BRANCH, SUBTREE | `CLAN_ADMIN` |
| `ROLE_MANAGE` | Grant / revoke permissions to others within scope. | CLAN, BRANCH, SUBTREE | `CLAN_ADMIN` |
| `CLAN_ADMIN` | Clan settings, branch structure, hard delete, everything above. | CLAN only | — |

**Implication rule.** `CLAN_ADMIN@CLAN` implies every permission at clan scope (FR-Z6). Any permission implies `CLAN_VIEW` at the same scope — you can always see what you can act on. Implications are expanded during resolution, never stored as extra rows.

## 3. Scope Semantics

| Scope | `scope_ref_id` | Covers |
|-------|----------------|--------|
| `CLAN` | `null` | Every person, branch, member, and request in the clan. |
| `BRANCH` | `branchId` | That branch **and all descendant branches** (via `branches.path` prefix), and every person placed in them. |
| `SUBTREE` | `personId` | That person **and all their descendants** (via `person_ancestry`). Excludes ancestors, spouses' own ancestors, and sibling lines. |

### 3.1 Isolation guarantee (FR-Z4)

Given Uncle C at đời 14 with `TREE_EDIT` scoped to Branch C:

| Target | Decision | Why |
|--------|----------|-----|
| A child of Uncle C, placed in Branch C | ✅ allow | branch path prefix matches |
| A new person Uncle C adds under his own son | ✅ allow | inherits Branch C placement |
| Uncle D's son (Branch D) | ❌ deny | different path prefix |
| The đời-13 common ancestor (root branch) | ❌ deny | ancestors are above the scope |
| Uncle C **himself** | ⚠️ configurable | default **allow** for `SUBTREE` (self-row at depth 0), and **allow** for `BRANCH` when he is placed in Branch C |
| Clan settings | ❌ deny | requires `CLAN_ADMIN` |

> **Design note.** A branch editor editing the branch-root person can rewrite the link that attaches their branch to the rest of the tree. The parentage edge *above* a branch root is therefore treated as belonging to the parent scope: re-parenting a branch-root person requires permission on **both** the person and the new parent. See §5.3.

## 4. Resolution Algorithm

```
can(user, permission, target) -> Decision
```

**Inputs.** `userId`, requested `permission`, and a `Target` — one of `ClanTarget(clanId)`, `BranchTarget(clanId, branchId)`, `PersonTarget(clanId, personId, branchId)`, or `RelationshipTarget(clanId, personIds…)`.

**Steps.**

1. **Resolve tenancy.** Determine the target's `clanId`. A target outside the clan the request addresses is an immediate deny (NFR-4).
2. **Check membership.** Load the `ACTIVE` membership for (user, clan). No membership → deny. (Platform operators are out of band and not modelled as members in v1.)
3. **Load grants.** One indexed query returns all non-revoked, unexpired grants for (user, clan). Cached for the request duration.
4. **Expand implications.** `CLAN_ADMIN@CLAN` → all permissions at CLAN scope; any permission → `CLAN_VIEW` at the same scope.
5. **Match.** For each grant whose permission equals the requested one, test scope coverage:
   - `CLAN` → always covers.
   - `BRANCH` → covers if `target.branchPath` starts with `grantBranch.path`.
   - `SUBTREE` → covers if `person_ancestry` contains `(ancestor = grant.scopeRefId, descendant = target.personId)`, self-row included.
   - For a `BranchTarget` under a `SUBTREE` grant: covers only if the branch's root person is in the grant's descendant set.
   - For a `RelationshipTarget`: **all** involved persons must be covered (see §5.2) — each by *some* grant, not necessarily the same one. Access is the union of the grants (§1.3), so an editor of two branches may relate a person in one to a person in the other.
6. **Decide.** First covering grant → allow, recording `authorizingGrantId` for the audit log (FR-Z8). No match → deny.

**Complexity.** One membership lookup + one grants query (both indexed, both cacheable per request) + at most one ancestry lookup per `SUBTREE` grant. Typical users hold 1–3 grants per clan.

### 4.1 Batch resolution (`canEdit` flags, FR-T3)

Rendering a tree needs a per-node `canEdit`. Computing `can()` per node is N queries — unacceptable. Instead:

1. Load the user's grants for the clan once.
2. `CLAN`-scoped `TREE_EDIT` → every node is editable; done.
3. Otherwise build an **editable set**:
   - Branch grants → collect branch ids matching each `path` prefix, then mark persons in those branches.
   - Subtree grants → one `person_ancestry` query per grant returns the descendant id set.
4. Union the sets, and mark nodes during serialization.

This is O(number of grants) queries, independent of tree size.

## 5. Enforcement Points

### 5.1 Layering

```
Controller  →  @RequiresPermission (declarative pre-check on the coarse target)
    ↓
ApplicationService  →  permissionResolver.require(user, PERM, target)   ← authoritative check
    ↓
Repository  →  every query carries clanId (tenancy filter, defense in depth)
```

The annotation is a fast fail for obvious cases; the **service-level check is the contract**. Any mutation path lacking an explicit `require(...)` call is a review blocker.

### 5.2 Multi-target operations

Some operations touch more than one node and require permission on **all** of them:

| Operation | Requires `TREE_EDIT` on |
|-----------|------------------------|
| Add parentage (P → C) | both P and C |
| Remove parentage | both endpoints |
| Create union (A, B) | both A and B |
| Move a person to another branch | the person, plus the destination branch |
| Attach a child to a union | the child and both partners |
| Soft-delete a person | that person; blocked if any child edge would be orphaned outside the actor's scope |

This closes the obvious escape hatch: a branch editor cannot reach outside their scope by *linking* to a foreign node.

### 5.3 Branch-root re-parenting

Changing the parent of a branch-root person restructures the clan. It requires `TREE_EDIT` covering the **new parent** (which, by §5.1, means the ancestor scope) — a branch editor alone cannot re-attach their branch elsewhere.

### 5.4 Grant management (no escalation, FR-Z5)

To grant `(permission P, scope S)` to user U, the actor must satisfy **all** of:

1. Hold `ROLE_MANAGE` covering `S` (or `CLAN_ADMIN`).
2. Hold `P` themselves at a scope that **covers** `S`.
3. U holds an `ACTIVE` membership in the clan.
4. If `P == CLAN_ADMIN`, the actor holds `CLAN_ADMIN` (clan scope only, I-G2).

**Scope-covering relation** (`S₁ covers S₂`):
- `CLAN` covers everything in the clan.
- `BRANCH(b₁)` covers `BRANCH(b₂)` if `b₂.path` starts with `b₁.path`; covers `SUBTREE(p)` if `p` is placed in `b₁` or a descendant branch.
- `SUBTREE(p₁)` covers `SUBTREE(p₂)` if `p₂ ∈ descendants(p₁)`; covers `BRANCH(b)` only if `b`'s root person is in `descendants(p₁)`.

Revocation requires `ROLE_MANAGE` covering the grant's scope. Self-revocation of one's own last `CLAN_ADMIN` is blocked (I-M3).

### 5.5 Read filtering

`CLAN_VIEW` in v1 is effectively clan-wide for reads: a member who can see the clan sees the whole tree, because a *gia phả* is meaningless in fragments (FR-T2). Branch/subtree scoping constrains **writes**. Two exceptions:

- Living persons' sensitive fields (contact, exact birth date) require clan membership — never exposed to non-members.
- If a future requirement demands read partitioning, the same resolver already supports `CLAN_VIEW` at branch scope; only the query filters change.

## 6. Bootstrap & Special Cases

| Situation | Rule |
|-----------|------|
| Clan creation | The creator gets `CLAN_ADMIN@CLAN`, issued by the system (`granted_by = creator`, flagged in audit as bootstrap). |
| Last admin | The system refuses to revoke, expire, or membership-remove the final active `CLAN_ADMIN`. |
| Join approval | Approval creates a membership and grants `CLAN_VIEW` at the requested scope (clan-wide, or the target branch). |
| Node claim approval | Approval does **not** grant edit rights. Being *in* the tree and being able to *edit* it are separate concerns. |
| Self-service profile | A user linked to a person may edit a whitelisted subset of their own node (avatar, contact, notes) without `TREE_EDIT` — a narrow, explicitly enumerated exception implemented as its own service method, not a resolver special case. |
| Expired grants | Filtered at query time; a nightly job marks them revoked for hygiene. |
| Suspended user | Membership stays, but authentication fails, so no decision is ever reached. |

## 7. API Error Semantics

| Case | Status | Body |
|------|--------|------|
| Not authenticated | `401` | `AUTH_REQUIRED` |
| Authenticated, no membership in clan | `404` | `CLAN_NOT_FOUND` — existence of a private clan is not disclosed |
| Member, insufficient permission | `403` | `PERMISSION_DENIED` + `requiredPermission`, `targetScope` |
| Escalation attempt | `403` | `PERMISSION_ESCALATION_BLOCKED` |
| Target outside actor's scope | `403` | `OUT_OF_SCOPE` + the branch/subtree the actor does hold |

Deliberate choice: **404 for non-members, 403 for members.** Non-members must not be able to probe which clan codes exist; members already know the clan exists, so a precise 403 is the more helpful answer.

## 8. Testing Requirements

Authorization is the highest-risk area; these tests are mandatory before Phase 3 is considered done.

**Unit — `PermissionResolver`** (pure, no DB, ports stubbed):
- Every (permission × scopeType × targetType) combination, allow and deny.
- Implication expansion (`CLAN_ADMIN` → all; any → `CLAN_VIEW`).
- Expired, revoked, and membership-less grants are ineffective.
- Multiple overlapping grants → union behaviour.

**Integration — the isolation matrix** (real Postgres via Testcontainers), fixture: clan with root ancestor đời 13, branches C and D:
| Actor | Target | Expected |
|-------|--------|----------|
| Editor C | node in C | 200 |
| Editor C | node in D | 403 |
| Editor C | đời-13 ancestor | 403 |
| Editor C | new child under a C node | 201 |
| Editor C | parentage linking a C node to a D node | 403 |
| Editor C | grant `TREE_EDIT@CLAN` to self | 403 |
| Editor C | grant `TREE_EDIT@Branch C` to another member | 200 (holds `ROLE_MANAGE@C`) |
| Admin | anything in clan | 200 |
| Non-member | anything | 404 |

**Property test.** For random trees and random grant sets, assert: *a user may mutate a node only if some grant's scope contains it* — verified independently of the resolver's own logic.

**Regression rule.** Every authorization bug gets a test at the exact API layer where it escaped, before the fix.
