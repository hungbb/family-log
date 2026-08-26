import js from '@eslint/js';
import importPlugin from 'eslint-plugin-import';
import i18next from 'eslint-plugin-i18next';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import prettier from 'eslint-config-prettier';
import globals from 'globals';
import tseslint from 'typescript-eslint';

/**
 * ESLint flat config.
 *
 * Beyond the usual React/TypeScript hygiene, this file is where the draft's non-negotiable
 * constraints stop being a document and start being a build failure (doc 07 §9):
 *
 *   - no Axios, no `ky`, no HTTP library of any kind — `fetch` and the wrapper, or nothing;
 *   - no Tailwind or any utility-CSS framework;
 *   - no `.css` imports outside the one global stylesheet;
 *   - no raw `fetch` outside `api/client.ts`;
 *   - no `style` prop on DOM elements — styling lives in styled-components.
 *
 * `eslint-fixtures/` holds files that deliberately break these rules, and
 * `npm run lint:constraints` asserts that each one is still caught. A guardrail nobody tests is a
 * guardrail that quietly stops working.
 */

/** Packages that would route HTTP around `api/client.ts`, or styling around styled-components. */
const FORBIDDEN_PACKAGES = [
  {
    name: 'axios',
    message: 'No Axios (draft §3.2). All HTTP goes through the fetch wrapper in src/api/client.ts.',
  },
  {
    name: 'ky',
    message: 'No third-party HTTP client. Use the fetch wrapper in src/api/client.ts.',
  },
  {
    name: 'superagent',
    message: 'No third-party HTTP client. Use the fetch wrapper in src/api/client.ts.',
  },
  {
    name: 'got',
    message: 'No third-party HTTP client. Use the fetch wrapper in src/api/client.ts.',
  },
  {
    name: 'node-fetch',
    message: 'Use the platform fetch through src/api/client.ts.',
  },
  {
    name: 'tailwindcss',
    message: 'NEVER Tailwind (draft §3.2). All custom styling lives in styled-components.',
  },
  {
    name: '@tailwindcss/vite',
    message: 'NEVER Tailwind (draft §3.2). All custom styling lives in styled-components.',
  },
  {
    name: 'clsx',
    message: 'Utility-class composition implies a utility-CSS framework. Use styled-components.',
  },
  {
    name: 'classnames',
    message: 'Utility-class composition implies a utility-CSS framework. Use styled-components.',
  },
  {
    name: 'emotion',
    message: 'One CSS-in-JS library only: styled-components.',
  },
  {
    name: '@emotion/react',
    message: 'One CSS-in-JS library only: styled-components.',
  },
  {
    name: '@emotion/styled',
    message: 'One CSS-in-JS library only: styled-components.',
  },
];

/**
 * The shared layers are consumed by features, never the reverse (doc 07 §2).
 *
 * A `components/` file that imports a feature is no longer shared — it has quietly become part of
 * that feature, and the next person to reuse it drags the feature along with it.
 */
const SHARED_LAYER_BOUNDARY = {
  group: ['**/features/**', '@/features/**'],
  message:
    'components/, hooks/, lib/, stores/ and theme/ are app-agnostic; the dependency runs from features/ to them, never back.',
};

export default tseslint.config(
  {
    ignores: ['dist/**', 'node_modules/**', 'coverage/**', 'eslint-fixtures/**'],
  },

  js.configs.recommended,

  // Type-aware linting is scoped to the application source. The config files below are plain
  // ESM that TypeScript never compiles, and a typed rule applied to them fails to load rather
  // than reporting anything useful.
  ...tseslint.configs.strictTypeChecked.map((config) => ({
    ...config,
    files: ['**/*.{ts,tsx}'],
  })),
  ...tseslint.configs.stylisticTypeChecked.map((config) => ({
    ...config,
    files: ['**/*.{ts,tsx}'],
  })),

  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2023,
      globals: globals.browser,
      parserOptions: {
        projectService: {
          // The constraint fixtures are deliberately outside tsconfig.json — they must never
          // reach the build — but `lint:constraints` still has to lint them. This lets the
          // parser fall back to a default project for exactly those files.
          allowDefaultProject: ['eslint-fixtures/*.ts', 'eslint-fixtures/*.tsx'],
        },
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: {
      react,
      'react-hooks': reactHooks,
      'jsx-a11y': jsxA11y,
      import: importPlugin,
      i18next,
    },
    settings: {
      react: { version: 'detect' },
      'import/resolver': {
        typescript: { alwaysTryTypes: true, project: './tsconfig.json' },
      },
    },
    rules: {
      ...react.configs.flat.recommended.rules,
      ...react.configs.flat['jsx-runtime'].rules,
      ...reactHooks.configs.recommended.rules,
      ...jsxA11y.flatConfigs.recommended.rules,

      // --- the draft's hard constraints ---------------------------------------------------

      'no-restricted-imports': [
        'error',
        {
          paths: FORBIDDEN_PACKAGES,
          patterns: [
            {
              group: ['tailwindcss/*', '@tailwindcss/*'],
              message:
                'NEVER Tailwind (draft §3.2). All custom styling lives in styled-components.',
            },
            {
              group: ['@emotion/*'],
              message: 'One CSS-in-JS library only: styled-components.',
            },
          ],
        },
      ],

      'no-restricted-syntax': [
        'error',
        {
          // `.css`, `.scss`, `.less` — the global stylesheet is createGlobalStyle, not a file.
          selector: 'ImportDeclaration[source.value=/\\.(css|scss|sass|less|styl)$/]',
          message:
            'No stylesheet imports. Custom CSS lives in styled-components; the one global sheet is theme/GlobalStyle.ts.',
        },
        {
          // The `@apply` smell, in case a stylesheet ever sneaks in through a template literal.
          selector: 'Literal[value=/@apply\\s/]',
          message: 'No Tailwind directives (draft §3.2).',
        },
      ],

      // Every request goes through api/client.ts, which owns error mapping, the correlation id,
      // and (from Phase 1) auth and the single-flight refresh. An override below exempts the
      // client itself.
      'no-restricted-globals': [
        'error',
        {
          name: 'fetch',
          message: 'Use the wrapper in src/api/client.ts — it is the only place that calls fetch.',
        },
      ],
      'no-restricted-properties': [
        'error',
        {
          object: 'window',
          property: 'fetch',
          message: 'Use the wrapper in src/api/client.ts — it is the only place that calls fetch.',
        },
        {
          object: 'globalThis',
          property: 'fetch',
          message: 'Use the wrapper in src/api/client.ts — it is the only place that calls fetch.',
        },
      ],

      // All styling in styled-components. The single sanctioned exception is the zoom/pan
      // transform in TreeCanvas (doc 07 §7.3), which carries an inline disable and a rationale.
      'react/forbid-dom-props': ['error', { forbid: ['style'] }],

      // --- i18n (NFR-8) ---------------------------------------------------------------------

      'i18next/no-literal-string': [
        'warn',
        {
          mode: 'jsx-text-only',
          'should-validate-template': true,
        },
      ],

      // --- structure -------------------------------------------------------------------------

      'import/no-cycle': ['error', { maxDepth: 6, ignoreExternal: true }],
      'import/no-self-import': 'error',
      'import/no-useless-path-segments': 'error',
      'import/order': [
        'error',
        {
          // No separate 'type' group: a type import belongs beside the value import from the
          // same module, not exiled to the bottom of the block.
          groups: ['builtin', 'external', 'internal', 'parent', 'sibling', 'index'],
          'newlines-between': 'always',
          alphabetize: { order: 'asc', caseInsensitive: true },
        },
      ],

      // --- TypeScript --------------------------------------------------------------------------

      '@typescript-eslint/consistent-type-imports': [
        'error',
        { prefer: 'type-imports', fixStyle: 'inline-type-imports' },
      ],
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
      '@typescript-eslint/restrict-template-expressions': [
        'error',
        { allowNumber: true, allowBoolean: true },
      ],

      // --- React --------------------------------------------------------------------------------

      // Props are typed interfaces; React.FC implies children a component may not accept.
      'react/prop-types': 'off',
      'react/jsx-no-useless-fragment': 'error',
      'react/self-closing-comp': 'error',
      'react-hooks/exhaustive-deps': 'error',

      // --- general ------------------------------------------------------------------------------

      'no-console': ['error', { allow: ['warn', 'error'] }],
      eqeqeq: ['error', 'always', { null: 'ignore' }],
      curly: ['error', 'multi-line'],
    },
  },

  // The one module allowed to call fetch — it is the wrapper everything else must go through.
  {
    files: ['src/api/client.ts'],
    rules: {
      'no-restricted-globals': 'off',
      'no-restricted-properties': 'off',
    },
  },

  // The shared layers, which may not import a feature.
  //
  // `stores/` joins them in Phase 1: the session has to be readable by the app shell, and
  // `components/` may not import from `features/` — so a session store inside a feature would
  // force the boundary to be broken the first time the top bar needed to know who is signed in.
  {
    files: ['src/components/**', 'src/hooks/**', 'src/lib/**', 'src/stores/**', 'src/theme/**'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          paths: FORBIDDEN_PACKAGES,
          patterns: [SHARED_LAYER_BOUNDARY],
        },
      ],
    },
  },

  // Config files run in Node and are not part of the app's type-checked source graph.
  {
    files: ['*.config.{js,ts}', 'scripts/**/*.{js,mjs}'],
    languageOptions: {
      globals: globals.node,
    },
    rules: {
      'no-console': 'off',
      'i18next/no-literal-string': 'off',
    },
  },

  // Prettier last: it turns off every stylistic rule the formatter already owns.
  prettier,
);
