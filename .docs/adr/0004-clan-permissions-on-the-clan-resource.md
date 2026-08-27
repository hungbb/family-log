# ADR 0004 — Return `myPermissions[]` on the clan resource instead of a separate endpoint

- **Status:** Accepted
- **Date:** 2026-08-27
- **Deciders:** hungbb
- **Affects:** [05-api-specification.md](../05-api-specification.md) §4.3;
  [phases/phase-2-clan-branch.md](../phases/phase-2-clan-branch.md) §2 (B2.10)

## Context

Doc 05 §4.3 lists two ways for a client to learn what it may do in a clan:

- `GET /clans/{clanId}` — "Detail incl. `myPermissions[]` for UI gating";
- `GET /clans/{clanId}/my-permissions` — "Grants + resolved scopes; drives UI affordances".

Phase 2 needs the first and does not yet have anything to put in the second. There are no grants
until Phase 3, so "resolved scopes" has no meaning: the interim rule of the phase document is a
single boolean — did this account create the clan — and a dedicated endpoint returning a
two-element array derived from that boolean is a contract shaped around data that does not exist.

The frontend need is real and it is not the detail view. The clan switcher gates on permissions for
every clan the caller belongs to (whether to show the settings link, whether the branch tree is
editable), and it renders from `GET /clans`, which returns a list. A separate per-clan endpoint
would mean one request per clan in the switcher, on every page load, to compute something the list
query already knows.

## Decision

`myPermissions[]` is a field on both clan representations — `ClanResponse` (detail) and
`ClanSummaryResponse` (the switcher list). `GET /clans/{clanId}/my-permissions` is not implemented
in Phase 2 and arrives with the grant model in Phase 3, where it can answer the question doc 05 §4.3
actually asks of it: which grants, at which scopes, and from whom.

The values are the permission names of doc 02 §2.8 (`CLAN_VIEW`, `TREE_EDIT`, `MEMBER_APPROVE`,
`NODE_CLAIM_APPROVE`, `ROLE_MANAGE`, `CLAN_ADMIN`), so the *shape* is already what Phase 3 will
produce. Only the source changes.

## Alternatives Considered

| Option | Pros | Cons | Why not |
|--------|------|------|---------|
| Ship `/my-permissions` now, returning the interim list | Matches doc 05 §4.3 literally | The switcher pays one request per clan for a field the list already computed; the endpoint's documented payload (grants and scopes) cannot be honoured, so it would ship as a different endpoint under the same name | An endpoint that does not do what its documentation says is worse than one that does not exist yet |
| Field on the detail response only | Smaller change | The switcher renders from the list and would have to fetch each clan separately to gate a menu item | Defeats the purpose |
| No permissions in the API; the client infers from `createdBy` | Nothing to build | Puts the authorization rule in the client, in a form Phase 3 would have to find and remove — the exact mistake `InterimClanAccessCheck` exists to prevent | The rule stays on the server |

## Consequences

**Positive** — the switcher and every clan-scoped screen gate on data they already have. The client
code that reads `myPermissions[]` is the code Phase 3 will keep unchanged: it treats the array as
opaque strings and re-checks nothing, so swapping the interim source for resolved grants is
invisible to it. Unknown values are ignored rather than crashing, per doc 05 §6.

**Negative** — the field is computed on every clan read, including the list. It is a membership
lookup the request has already performed, so the cost is an array allocation; if that ever shows up
in a profile, the answer is a cache in `InterimClanAccessCheck`'s replacement, not a second
endpoint.

The list is also coarse: until Phase 3 it is either "everything" (the founder) or `CLAN_VIEW`
(everyone else). A client that gates a fine-grained affordance on it will be wrong in Phase 3's
favour — it will hide something a scoped grant would have allowed — never in the other direction.

**Follow-up** — doc 05 §4.3 updated to say `myPermissions[]` appears on the clan resource and on the
list. `GET /clans/{clanId}/my-permissions` stays in the specification and is Phase 3's to build.
