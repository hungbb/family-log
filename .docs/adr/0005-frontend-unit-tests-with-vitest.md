# ADR 0005 — Frontend unit tests with Vitest

- **Status:** Proposed
- **Date:** 2026-11-11
- **Deciders:** hungbb
- **Affects:** [0001-defer-containerisation-and-ci.md](0001-defer-containerisation-and-ci.md) (the frontend-test-runner part only); [07-frontend-architecture.md](../07-frontend-architecture.md) §1; [09-engineering-conventions.md](../09-engineering-conventions.md) §4.3; README "Known gaps"

## Context

ADR 0001 deferred the frontend test runner out of Phase 0 because there was nothing to test: one
page, one fetch wrapper. It also said when that would stop being true — "the units worth testing
(`buildGraph`, date and name formatting, permission gates) all arrive in Phases 4–5", and "adding the
runner alongside its first real subject costs the same and skips the empty-suite phase".

Those units now exist, and the end-to-end pass of
[#20](https://github.com/hungbb/family-log/issues/20) found defects in exactly that layer: a date
formatter that drops lunar-only dates, form schemas that surface a raw Zod message, branch lookups
that ignore nested branches, a login redirect that forgets where the visitor was going. The issue
asks for each fix to come with "a regression test that fails before the fix and passes after — unit,
integration, or a web test as appropriate". For the web half there was no way to write one:
`npm run verify` is lint, the constraint fixtures, `tsc` and the build, and none of those can express
"this function returns the wrong string".

The same pass found `npm run verify` red on `main` (three `import/order` errors), which is the other
half of the argument: a convention nobody is forced to run decays, and the cheapest thing that can
be added to the convention is a suite that runs in under a second.

## Decision

Add **Vitest** as a dev dependency and run it inside `npm run verify`. Unit tests only, in the plain
`node` environment: files named `*.test.ts` next to the module they cover, importing `describe`,
`it` and `expect` explicitly (no globals).

Testing Library, MSW and Playwright stay deferred, as ADR 0001 left them.

## Alternatives Considered

| Option | Pros | Cons | Why not |
|--------|------|------|---------|
| Keep fixing without tests | No new dependency | The issue's own acceptance criterion cannot be met; the next regression in a formatter is found by a person, again | It is the status quo that produced the findings |
| Plain Node scripts with `node:assert`, like `scripts/check-constraints.mjs` | Zero dependencies | The sources are TypeScript with `@/` aliases and JSON imports, so each script would need Vite's SSR loader or a second resolver — a home-made runner with no watch mode, no reporter and nobody else's documentation | Rebuilding a test runner to avoid installing one |
| `node --test` with type stripping | Built in | Does not resolve `@/` aliases or `verbatimModuleSyntax` type-only imports without a custom loader; needs Node ≥ 22.18 unflagged while `engines` says ≥ 22.12 | Fragile on exactly the imports this codebase uses everywhere |
| Vitest **and** Testing Library + jsdom now | Component behaviour testable | Three more dev dependencies and a DOM environment for a first batch of tests that are all pure functions | Add them with the first test that needs a DOM, for the reason ADR 0001 gave for the runner itself |
| Jest | Familiar | A second transform pipeline and module resolver beside Vite's, to be kept in agreement with it by hand | Vitest reads `vite.config.ts`; the alias, the JSON imports and the TS settings are already right |

## Consequences

**Positive** — a web defect can now ship with a test that fails before the fix. The suite reuses the
Vite config, so `@/` imports and the locale JSON resolve exactly as they do in the application, and
tests translate with the real `vi`/`en` resources (`src/test/translator.ts`) rather than a stub. The
first tests cover the date and lifespan formatters and assert that the two locale files define the
same messages with the same placeholders — a check that used to be a manual diff. `npm run verify`
grows by well under a second.

**Negative** — one more dev dependency tree (15 packages; two existing transitive dev packages moved
a patch/minor version). `verify` has a fifth step that can fail. Component behaviour — a redirect, a
form that refuses to submit — is still only reachable by extracting the decision into a pure
function, which is a reasonable discipline and also a limit.

**Follow-up**

- `07-frontend-architecture.md` §1, `09-engineering-conventions.md` §4.3 and the README are updated
  in the same change to say what is and is not automated.
- Testing Library + jsdom arrive with the first component that genuinely needs rendering to test;
  MSW and Playwright with CI, as ADR 0001 planned.
- `buildGraph` and the tree layout are the largest untested pure modules and the obvious next
  subjects.
