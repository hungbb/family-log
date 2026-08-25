// EXPECT: no-restricted-syntax
// The only global stylesheet is theme/GlobalStyle.ts, built with createGlobalStyle.
import './some-stylesheet.css';

export const styled = false;
