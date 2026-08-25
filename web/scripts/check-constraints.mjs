#!/usr/bin/env node
/**
 * Asserts that the draft's hard constraints are still enforced.
 *
 * Every file in `eslint-fixtures/` breaks exactly one rule and declares which one in an
 * `// EXPECT: <rule-id>` comment on its first line. This script lints them and checks that each
 * expected rule actually fired.
 *
 * Why bother? Because the constraints in `.docs/draft.md` §3.2 are the kind of thing that erodes
 * quietly: a rule gets disabled to unblock a merge, a plugin upgrade renames an option, a config
 * refactor drops a block. Any of those turns a "non-negotiable" into a comment. This turns it back
 * into a failing build.
 *
 * There is no test runner in v1 (doc: Phase 0, F0.8), so this is a plain script — run it with
 * `npm run lint:constraints`, and in `npm run verify` alongside lint, typecheck and build.
 */
import { spawnSync } from 'node:child_process';
import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const fixturesDir = join(projectRoot, 'eslint-fixtures');

const EXPECT_PATTERN = /^\s*\/\/\s*EXPECT:\s*(\S+)\s*$/m;

/** Reads the `// EXPECT: <rule-id>` declaration from each fixture. */
function readExpectations() {
  const files = readdirSync(fixturesDir).filter((name) => /\.tsx?$/.test(name));

  if (files.length === 0) {
    fail('No fixtures found in eslint-fixtures/. The constraints are no longer being verified.');
  }

  return files.map((name) => {
    const path = join(fixturesDir, name);
    const match = EXPECT_PATTERN.exec(readFileSync(path, 'utf8'));
    if (!match) {
      fail(`${name} has no "// EXPECT: <rule-id>" line, so nothing is being asserted about it.`);
    }
    return { name, path, expectedRule: match[1] };
  });
}

function runEslint() {
  // Invoke ESLint's entry point with the current Node binary rather than going through `npx`:
  // spawning a `.cmd` shim fails with EINVAL on Windows unless a shell is involved, and pulling in
  // a shell to run one command invites quoting bugs on exactly the platform this repo develops on.
  const eslintBin = join(projectRoot, 'node_modules', 'eslint', 'bin', 'eslint.js');

  // --no-ignore, because eslint.config.js deliberately ignores this directory for the normal run.
  const result = spawnSync(
    process.execPath,
    [eslintBin, '--no-ignore', '--format', 'json', 'eslint-fixtures'],
    { cwd: projectRoot, encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 },
  );

  if (result.error) {
    fail(`Could not run ESLint: ${result.error.message}`);
  }

  // ESLint exits 1 when it reports errors — which is precisely what we expect here. Anything
  // higher (2) means the config itself failed to load, and that is a real failure.
  if (result.status !== 0 && result.status !== 1) {
    fail(`ESLint could not run (exit ${result.status}).\n${result.stderr || result.stdout}`);
  }

  try {
    return JSON.parse(result.stdout);
  } catch {
    fail(`ESLint did not produce JSON output.\n${result.stdout}\n${result.stderr}`);
  }
}

function fail(message) {
  console.error(`\n✗ constraint check failed\n  ${message}\n`);
  process.exit(1);
}

const expectations = readExpectations();
const report = runEslint();

const rulesByFile = new Map(
  report.map((entry) => [
    resolve(entry.filePath),
    new Set(entry.messages.map((message) => message.ruleId).filter(Boolean)),
  ]),
);

const failures = [];
for (const { name, path, expectedRule } of expectations) {
  const fired = rulesByFile.get(resolve(path));
  if (!fired) {
    failures.push(`${name}: ESLint did not lint this file at all.`);
  } else if (!fired.has(expectedRule)) {
    const other = [...fired].join(', ') || 'nothing';
    failures.push(`${name}: expected "${expectedRule}" to fire, but got ${other}.`);
  }
}

if (failures.length > 0) {
  fail(
    `A hard constraint is no longer enforced:\n  - ${failures.join('\n  - ')}\n\n` +
      '  See .docs/draft.md §3.2 and web/eslint.config.js before changing this.',
  );
}

console.log(
  `✓ ${expectations.length} constraints enforced: ` +
    expectations.map((expectation) => expectation.expectedRule).join(', '),
);
