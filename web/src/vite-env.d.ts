/// <reference types="vite/client" />

/**
 * The environment the app reads at build time. Vite only exposes variables prefixed `VITE_`, and
 * only the ones declared here are typed — an undeclared `import.meta.env.VITE_TYPO` is a compile
 * error rather than `undefined` at runtime.
 *
 * See `web/.env.example`.
 */
interface ImportMetaEnv {
  /** Origin of the API, without a trailing slash, e.g. `http://localhost:8080`. */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
