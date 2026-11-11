// Must stay the first import. Ant Design 5 predates React 19: its static render path (the one
// behind `message`, `notification`, `Modal.confirm` and the click wave) still looks for
// `ReactDOM.render`, which React 19 removed, and it says so on every page load —
// "Warning: [antd: compatible] antd v5 support React is 16 ~ 18". This is Ant Design's own
// compatibility shim (https://u.ant.design/v5-for-19): it hands AntD a `createRoot`-based renderer,
// and it has to run before any AntD component does.
import '@ant-design/v5-patch-for-react-19';

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
