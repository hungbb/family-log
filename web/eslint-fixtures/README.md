# ESLint constraint fixtures

Each file here **deliberately violates** one of the draft's hard constraints. They are excluded
from `npm run lint` (see the `ignores` entry in `eslint.config.js`) and from `tsconfig.json`, so
they never break the normal build.

`npm run lint:constraints` lints them with `--no-ignore` and asserts that each file still trips the
rule it was written to trip. If someone loosens a rule — or deletes it while resolving a merge
conflict — that script fails, loudly, with the name of the constraint that stopped being enforced.

A guardrail nobody tests is a guardrail that quietly stops working.
