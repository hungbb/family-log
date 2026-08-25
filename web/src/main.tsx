import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { App } from './App';

const container = document.getElementById('root');

if (!container) {
  // index.html is ours, so this can only mean the template was edited. Failing loudly beats a
  // blank page with nothing in the console.
  throw new Error('No #root element found in index.html');
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
