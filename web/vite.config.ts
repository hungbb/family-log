import { fileURLToPath, URL } from 'node:url';

import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  // @vitejs/plugin-react v6 transforms with oxc and no longer exposes a Babel pipeline, so
  // `babel-plugin-styled-components` cannot be wired in here. styled-components works without it;
  // what we give up is readable generated class names in devtools. If that becomes a real
  // obstacle while building the tree visualizer, the fix is `@vitejs/plugin-react-swc` plus
  // `@swc/plugin-styled-components` — not a second styling library.
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
    // Doc 07 §10 budgets the initial bundle at 250 KB gzipped. Warn well before that so a
    // careless import shows up in the build log rather than in a Lighthouse run.
    chunkSizeWarningLimit: 700,
  },
});
