/**
 * Pre-commit gate (doc 07 §9): ESLint, Prettier and a full type-check on what is being committed.
 *
 * `tsc --noEmit` is deliberately run over the whole project rather than the staged files: types
 * cross file boundaries, so checking a subset would miss exactly the breakage a commit introduces
 * elsewhere. It is a few seconds, and it is the difference between finding a type error now and
 * finding it in CI.
 */
export default {
  '*.{ts,tsx}': [
    'eslint --max-warnings=0 --no-warn-ignored',
    'prettier --write',
    () => 'tsc --noEmit',
  ],
  '*.{js,mjs,json,md,html}': ['prettier --write'],
};
