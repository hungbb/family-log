# ADR 0008 — No account is linked to someone recorded as deceased

- **Status:** Accepted
- **Date:** 2026-11-28
- **Deciders:** project maintainers
- **Affects:** 01-product-requirements.md FR-N1, FR-N4; 02-domain-model.md §2.7 (I-K1); 05-api-specification.md §4.10; phase-6-claiming-linking.md

## Context

Phase 6 connects *living* users to their place in the tree (goal G7). FR-N1 says a member may claim an **unlinked** node, and FR-N4 lets an editor assign a member to one. Neither says anything about whether the person in that node is alive.

The Phase 6 walkthrough against the staging demo clan showed what that means in practice. The "find yourself" browser listed the founding ancestor, who died in 1885, with a "This is me" button. Of the 119 people in that clan, 97 are recorded as deceased. So most of what the browser offered a member could never be them. The editor's review would catch a mistaken claim, but only after the member had waited for an answer that could only be no.

Every part of linking also assumes the linked person is alive and the same person as the account. The self-service edit of doc 04 §6, the "This is you" ring, and profile propagation (FR-N6) all do. None of them makes sense on the entry for somebody who has died.

## Decision

A node whose `lifeStatus` is `DECEASED` cannot be linked to an account, by either route.

- `PersonUserLinker.link`, the single writer of `linked_user_id`, refuses it with `422 VALIDATION_FAILED`. That covers claim approval and direct assignment alike.
- `ClaimService.submit` asks the same question, so a claim on such a node is refused when it is made, not when it is answered.
- `GET /persons/unlinked` leaves deceased people out. The drawer does not offer "Link a member" on them.

`UNKNOWN` is treated as possibly living and stays claimable: old records often do not say. A link made while the person was alive is left alone if they are later recorded as deceased. The rule guards the way in, not the record.

## Alternatives Considered

| Option | Pros | Cons | Why not |
|--------|------|------|---------|
| Leave it to the editor's review (the original design) | No new rule | The browser is mostly people who cannot be the member; claims that can only be refused | The walkthrough showed it in the demo clan |
| Hide deceased people in the browser only | Small, client-only | The API still accepts the claim; a stale or scripted client gets round it | A rule only in the UI is not a rule |
| Warn but allow | Keeps every option open | Nobody can say what a link to a dead person *means*: no use of it is defined | No use case to keep it open for |
| Also refuse `UNKNOWN` | Stricter | Old books rarely say who is alive; living members recorded as `UNKNOWN` could not find themselves | Shuts out the people the feature is for |

## Consequences

**Positive** — The browser shows only people a member could be. A claim that can only be refused cannot be made. Linking means what its other features assume it means.

**Negative** — A living member wrongly recorded as deceased cannot claim their entry until an editor corrects the status. The error message says so.

**Follow-up** — Doc 05 §4.10 records the refusal. Tests cover each route: `PersonUserLinkerTest`, `ClaimServiceTest`, `PersonLinkServiceTest` and `NodeClaimingIT`.
