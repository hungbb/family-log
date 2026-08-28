| GET | `/clans/{clanId}/my-permissions` | `CLAN_VIEW` | Grants + resolved scopes, with the granter and the expiry — the permissions <i>screen</i>, not UI gating. Arrives with the grant model in Phase 3; see [ADR 0004](adr/0004-clan-permissions-on-the-clan-resource.md) || GET | `/clans` | authenticated | Clans the caller is an active member of, each with `myPermissions[]` — the clan switcher gates on it (ADR 0004) |
| GET | `/clans/{clanId}` | `CLAN_VIEW` | Detail incl. `myPermissions[]` for UI gating |# 05 — REST API Specification

> Contract between `api` (Spring Boot) and `web` (React). Documented at runtime via OpenAPI 3 (springdoc) at `/swagger-ui.html`; this file is the design source of truth.

---

## 1. Conventions

| Aspect | Rule |
|--------|------|
| Base path | `/api/v1` |
| Format | JSON only, UTF-8. Request/response bodies are `camelCase`. |
| Auth | `Authorization: Bearer <accessToken>` on everything except `/auth/**` and `/health`. |
| IDs | UUID strings. |
| Dates | Instants are ISO-8601 UTC (`2026-08-24T10:15:30Z`). Partial dates use the object form (§3.1). |
| Tenancy | Clan-scoped resources are nested under `/clans/{clanId}/…`. The clan in the path is always authoritative. |
| Pagination | `?page=0&size=20&sort=field,asc`. Responses use the envelope in §3.2. |
| Idempotency | `POST` endpoints that create workflow requests accept an optional `Idempotency-Key` header. |
| Concurrency | `PUT`/`PATCH` on versioned resources require `version` in the body; a mismatch returns `409`. |
| Correlation | Every response carries `X-Correlation-Id`; clients echo it back when reporting errors. |
| Validation | Jakarta Bean Validation; failures return `422` with field-level detail. |

## 2. Error Model

RFC 7807 Problem Details plus two extensions (`code`, `errors`):

```json
{
  "type": "https://giaky.app/errors/permission-denied",
  "title": "Permission denied",
  "status": 403,
  "detail": "You do not hold TREE_EDIT over this node.",
  "instance": "/api/v1/clans/8f0.../persons/2b1...",
  "code": "PERMISSION_DENIED",
  "correlationId": "7c1e...",
  "errors": [ { "field": "birthYear", "message": "must be <= current year" } ]
}
```

| HTTP | `code` values |
|------|---------------|
| 400 | `MALFORMED_REQUEST`, `INVALID_CLAN_CODE` |
| 401 | `AUTH_REQUIRED`, `TOKEN_EXPIRED`, `TOKEN_INVALID` |
| 403 | `PERMISSION_DENIED`, `OUT_OF_SCOPE`, `PERMISSION_ESCALATION_BLOCKED`, `LAST_ADMIN_PROTECTED` |
| 404 | `CLAN_NOT_FOUND`, `PERSON_NOT_FOUND`, `BRANCH_NOT_FOUND`, `REQUEST_NOT_FOUND` |
| 409 | `VERSION_CONFLICT`, `EMAIL_ALREADY_REGISTERED`, `DUPLICATE_PENDING_REQUEST`, `NODE_ALREADY_LINKED`, `USER_ALREADY_LINKED_IN_CLAN`, `MEMBERSHIP_EXISTS`, `GRANT_ALREADY_EXISTS` |
| 422 | `VALIDATION_FAILED`, `CYCLE_DETECTED`, `INVALID_RELATIONSHIP`, `DATE_RANGE_INVALID`, `SUBTREE_SCOPE_UNAVAILABLE` |
| 429 | `RATE_LIMITED` |
| 500 | `INTERNAL_ERROR` (never leaks internals; correlation id only) |

## 3. Shared Structures

### 3.1 `PartialDate`

```json
{ "year": 1943, "month": 7, "day": null,
  "precision": "MONTH", "circa": false, "lunarText": "tháng 6 năm Quý Mùi" }
```
`precision` ∈ `EXACT | MONTH | YEAR | RANGE | UNKNOWN`. `RANGE` adds `rangeEndYear`.

### 3.2 Page envelope

```json
{ "content": [ ], "page": 0, "size": 20, "totalElements": 137, "totalPages": 7 }
```

### 3.3 `PersonSummary` / `PersonDetail`

`PersonSummary` (tree nodes, search results): `id, fullName, alias, gender, lifeStatus, birth, death, generation, birthOrder, avatarUrl, branchId, linkedUserId, canEdit`.

`PersonDetail` adds: `familyName, givenName, posthumousName, burialPlace, notesHtml, isFoundingAncestor, parents[], unions[], children[], siblings[], linkedUser, version, createdAt, updatedAt`.

`canEdit` is computed per requesting user by the batch resolver (doc 04 §4.1).

## 4. Endpoints

### 4.1 Authentication — `/auth`

| Method | Path | Body / Params | Returns | Notes |
|--------|------|---------------|---------|-------|
| POST | `/auth/register` | `email, password, displayName` | `201` `{ userId, status }` | Rate-limited per IP |
| POST | `/auth/login` | `email, password` | `200` `{ accessToken, refreshToken, expiresIn, user }` | Generic error on failure — never reveals whether the email exists |
| POST | `/auth/refresh` | `refreshToken` | `200` new token pair | Rotates the refresh token; reuse of a rotated token revokes the whole chain |
| POST | `/auth/logout` | `refreshToken` | `204` | |
| POST | `/auth/logout-all` | — | `204` | Revokes every session for the user |
| POST | `/auth/verify-email` | `token` | `204` | |
| POST | `/auth/forgot-password` | `email` | `202` | Always `202`, regardless of existence |
| POST | `/auth/reset-password` | `token, newPassword` | `204` | Revokes all sessions |

### 4.2 Current user — `/me`

| Method | Path | Returns |
|--------|------|---------|
| GET | `/me` | Profile + `clans[]` (id, name, clanCode, role summary, linkedPersonId) |
| PATCH | `/me` | Updates `displayName`, `locale`, `avatarMediaId` |
| GET | `/me/sessions` | Active refresh-token sessions: `id, userAgent, ipAddress, issuedAt, expiresAt`. Never a token or a hash. Ended in bulk with `POST /auth/logout-all` |
| POST | `/me/avatar` | multipart upload → `MediaRef` |
| GET | `/me/notifications` | Paged notifications; `?unreadOnly=true` |
| POST | `/me/notifications/{id}/read` | `204` |
| GET | `/me/join-requests` | The user's own outbound join requests |
| GET | `/me/claims` | The user's own node claims |

### 4.3 Clans — `/clans`

| Method | Path | Permission | Notes |
|--------|------|-----------|-------|
| POST | `/clans` | authenticated | Creates clan + root branch + bootstrap `CLAN_ADMIN` |
| GET | `/clans` | authenticated | Clans the caller is an active member of, each with `myPermissions[]` — the clan switcher gates on it ([ADR 0004](adr/0004-clan-permissions-on-the-clan-resource.md)) |
| GET | `/clans/{clanId}` | `CLAN_VIEW` | Detail incl. `myPermissions[]` for UI gating |
| PATCH | `/clans/{clanId}` | `CLAN_ADMIN` | Name, description, origin, cover. `clanCode` immutable |
| DELETE | `/clans/{clanId}` | `CLAN_ADMIN` | Soft delete, 30-day recovery |
| GET | `/clans/lookup?code={clanCode}` | authenticated | Minimal public card (name, member count) used before joining |
| GET | `/clans/{clanId}/my-permissions` | `CLAN_VIEW` | Grants + resolved scopes, with the granter and the expiry — the permissions *screen*, not UI gating. Arrives with the grant model in Phase 3 ([ADR 0004](adr/0004-clan-permissions-on-the-clan-resource.md)) |

### 4.4 Branches — `/clans/{clanId}/branches`

| Method | Path | Permission | Notes |
|--------|------|-----------|-------|
| GET | `/branches` | `CLAN_VIEW` | Full branch tree with `path`, `depth`, member counts |
| POST | `/branches` | `CLAN_ADMIN` or `ROLE_MANAGE` on parent | `name, parentBranchId, branchRootPersonId?, description` |
| GET | `/branches/{branchId}` | `CLAN_VIEW` | Detail + root person summary |
| PATCH | `/branches/{branchId}` | `CLAN_ADMIN` | Rename, description, root person |
| POST | `/branches/{branchId}/move` | `CLAN_ADMIN` | `{ newParentBranchId }`; rewrites descendant paths |
| POST | `/branches/{branchId}/archive` | `CLAN_ADMIN` | Blocked while non-archived children exist |

### 4.5 Membership & join requests

| Method | Path | Permission | Notes |
|--------|------|-----------|-------|
| POST | `/join-requests` | authenticated | `{ clanCode \| branchCode, message? }` → `201` |
| DELETE | `/join-requests/{id}` | requester | Cancels own pending request |
| GET | `/clans/{clanId}/join-requests` | `MEMBER_APPROVE` | Inbox, filtered to the approver's scope; `?status=PENDING` |
| POST | `/clans/{clanId}/join-requests/{id}/approve` | `MEMBER_APPROVE` covering scope | `{ homeBranchId? }` → membership + `CLAN_VIEW` grant |
| POST | `/clans/{clanId}/join-requests/{id}/reject` | `MEMBER_APPROVE` | `{ reason }` |
| GET | `/clans/{clanId}/members` | `CLAN_VIEW` | Paged; `?search=&branchId=` ; includes `linkedPersonId` |
| DELETE | `/clans/{clanId}/members/{userId}` | `MEMBER_APPROVE` + not last admin | Sets `REMOVED`, deactivates grants |
| POST | `/clans/{clanId}/members/leave` | member | Self-leave |

### 4.6 Permissions — `/clans/{clanId}/permissions`

| Method | Path | Permission | Notes |
|--------|------|-----------|-------|
| GET | `/permissions` | `ROLE_MANAGE` or `CLAN_ADMIN` | All active grants; `?userId=&permission=` |
| POST | `/permissions` | `ROLE_MANAGE` covering scope | `{ granteeUserId, permission, scopeType, scopeRefId?, expiresAt? }`. Escalation → `403 PERMISSION_ESCALATION_BLOCKED` |
| DELETE | `/permissions/{grantId}` | `ROLE_MANAGE` covering scope | Soft revoke; last-admin protection |
| GET | `/permissions/grantable` | `ROLE_MANAGE` | What the caller may grant and at which scopes — the UI builds its form from this |

### 4.7 Persons — `/clans/{clanId}/persons`

| Method | Path | Permission | Notes |
|--------|------|-----------|-------|
| POST | `/persons` | `TREE_EDIT` covering target placement | Body may include an initial `parent` or `unionId` so placement and scope are decided atomically |
| GET | `/persons/{personId}` | `CLAN_VIEW` | `PersonDetail` |
| PUT | `/persons/{personId}` | `TREE_EDIT` on person | Requires `version` |
| PATCH | `/persons/{personId}` | `TREE_EDIT` on person | Partial update, requires `version` |
| DELETE | `/persons/{personId}` | `TREE_EDIT` on person | Soft delete; `422` if it would orphan children outside the actor's scope |
| POST | `/persons/{personId}/restore` | `CLAN_ADMIN` | Within the 30-day window |
| POST | `/persons/{personId}/avatar` | `TREE_EDIT` or self-linked | multipart |
| POST | `/persons/{personId}/move-branch` | `TREE_EDIT` on person **and** destination branch | `{ branchId }` |
| GET | `/persons` | `CLAN_VIEW` | Search: `?q=&generation=&lifeStatus=&branchId=&linked=true|false`, paged, diacritic-insensitive |

**Create person — request**
```json
{
  "fullName": "Nguyễn Văn An",
  "gender": "MALE",
  "lifeStatus": "DECEASED",
  "birth": { "year": 1901, "precision": "YEAR", "circa": true },
  "death": { "year": 1968, "month": 3, "precision": "MONTH" },
  "birthOrder": 2,
  "notesHtml": "<p>Di cư vào Nam năm 1954.</p>",
  "placement": { "type": "CHILD_OF_UNION", "unionId": "…" }
}
```
`placement.type` ∈ `CHILD_OF_UNION | CHILD_OF_PARENT | SPOUSE_OF | BRANCH_ROOT | UNPLACED`. Placement drives both `branchId` inheritance and the authorization target — a node is never created scope-less inside a branch the actor cannot reach.

### 4.8 Relationships

| Method | Path | Permission | Notes |
|--------|------|-----------|-------|
| POST | `/clans/{clanId}/parentages` | `TREE_EDIT` on **both** persons | `{ parentPersonId, childPersonId, parentRole, nature }`; `422 CYCLE_DETECTED` on a cycle |
| DELETE | `/clans/{clanId}/parentages/{id}` | `TREE_EDIT` on both endpoints | |
| POST | `/clans/{clanId}/unions` | `TREE_EDIT` on both partners | `{ partnerAId, partnerBId, unionType, status, startDate?, endDate? }` |
| PATCH | `/clans/{clanId}/unions/{id}` | `TREE_EDIT` on both partners | |
| DELETE | `/clans/{clanId}/unions/{id}` | `TREE_EDIT` on both partners | `422` if children still reference the union |
| POST | `/clans/{clanId}/unions/{id}/children` | `TREE_EDIT` on child + both partners | Attaches an existing person as a child of the union |

### 4.9 Tree read — `/clans/{clanId}/tree`

| Method | Path | Params | Notes |
|--------|------|--------|-------|
| GET | `/tree` | `rootPersonId?`, `depth?` (default full), `includeDeleted=false` | The **master view** (FR-T2): all branches aggregated |
| GET | `/tree/ancestors/{personId}` | `depth?` | Ancestor chain(s) for breadcrumbs |
| GET | `/tree/descendants/{personId}` | `depth?` | Subtree |
| GET | `/tree/stats` | — | Counts by generation, living/deceased, branch, unlinked-node count |

**Tree response** — a flat, normalized payload the client assembles (avoids deep nesting and repeated subjects):
```json
{
  "clanId": "…",
  "rootPersonIds": ["…"],
  "persons": [ { "…PersonSummary…" } ],
  "parentages": [ { "id": "…", "parentPersonId": "…", "childPersonId": "…", "parentRole": "FATHER", "nature": "BIOLOGICAL", "unionId": "…" } ],
  "unions": [ { "id": "…", "partnerAId": "…", "partnerBId": "…", "unionType": "PRIMARY", "status": "ENDED" } ],
  "branches": [ { "id": "…", "name": "Chi 2", "path": "/…/…/", "rootPersonId": "…" } ],
  "meta": { "personCount": 412, "maxGeneration": 17, "generatedAt": "…" }
}
```
Responses are `ETag`-tagged; clients send `If-None-Match` and get `304` when the tree is unchanged.

### 4.10 Node claiming — `/clans/{clanId}/claims`

| Method | Path | Permission | Notes |
|--------|------|-----------|-------|
| POST | `/claims` | active member | `{ personId, evidence? }`; `409 NODE_ALREADY_LINKED` / `USER_ALREADY_LINKED_IN_CLAN` |
| DELETE | `/claims/{id}` | requester | Cancel own pending claim |
| GET | `/claims` | `NODE_CLAIM_APPROVE` | Inbox filtered to the approver's scope |
| POST | `/claims/{id}/approve` | `NODE_CLAIM_APPROVE` covering the node | Re-checks the node is still unlinked |
| POST | `/claims/{id}/reject` | `NODE_CLAIM_APPROVE` | `{ reason }` |
| POST | `/persons/{personId}/link-user` | `NODE_CLAIM_APPROVE` covering node | Direct assignment: `{ userId }` (FR-N4) |
| DELETE | `/persons/{personId}/link-user` | `NODE_CLAIM_APPROVE` or the linked user | Unlink (FR-N5) |
| GET | `/persons/unlinked` | `CLAN_VIEW` | Candidate nodes for claiming; `?q=` |

### 4.11 Audit & operations

| Method | Path | Permission |
|--------|------|-----------|
| GET | `/clans/{clanId}/audit` | `CLAN_ADMIN` — paged, `?action=&targetId=&from=&to=` |
| GET | `/actuator/health` | public (liveness/readiness) |
| GET | `/actuator/prometheus` | internal network only |

## 5. Security Details

- **Access token**: JWT, 15 min TTL, claims `sub`, `iat`, `exp`, `jti`, `email`, `tokenVersion`. Deliberately **no** clan or permission claims — permissions change often and must never be cached in a bearer token.
- **Refresh token**: opaque 256-bit random, stored hashed, 30-day TTL, rotated on every use with reuse detection.
- **Signing**: HS256 with an externalized secret in v1; the key is loaded through a `JwtKeyProvider` abstraction so a move to RS256/JWKS needs no call-site changes.
- **Rate limits**: `/auth/login` 10/min/IP and 5/min/account; `/auth/register` and `/auth/forgot-password` 5/min/IP; general API 300/min/user.
- **CORS**: explicit origin allow-list per environment; credentials via the `Authorization` header only.
- **Headers**: `X-Content-Type-Options`, `X-Frame-Options: DENY`, HSTS in production, strict `Content-Security-Policy` on served assets.
- **Uploads**: content-type sniffed server-side (not trusted from the client), re-encoded, EXIF stripped, size-capped at 5 MB.
- **Token storage (client)**: access token in memory; refresh token in a `Secure`, `HttpOnly`, `SameSite=Strict` cookie when the API and web share a site, otherwise `localStorage` with the risk documented — decided in Phase 1.

## 6. Versioning & Compatibility

- The path carries the major version (`/api/v1`). Breaking changes require `/v2`; both run in parallel for at least one release.
- Additive changes (new optional fields, new endpoints) ship inside `v1`; clients must ignore unknown fields.
- Enum values may be **added** within a major version; clients must handle unknown enum values gracefully (render as "unknown", never crash).
- The OpenAPI document is exported from the build and diffed against the previous release; a breaking diff is treated as a breaking change (wired to fail the build once the CI/CD workstream lands).
