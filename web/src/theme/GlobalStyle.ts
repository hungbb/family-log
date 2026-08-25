import { createGlobalStyle } from 'styled-components';

/**
 * The one global stylesheet in the application.
 *
 * Everything else is a `styled` component. This exists for the things that genuinely have no
 * component to hang off: the box-sizing reset, the document background and type, focus visibility,
 * and the CSS custom properties that let a stylesheet reach a token without a JS import.
 *
 * Ant Design brings its own reset, so this deliberately does not re-normalise what it already
 * handles — two competing resets is how you get a margin nobody can find.
 */
export const GlobalStyle = createGlobalStyle`
  *,
  *::before,
  *::after {
    box-sizing: border-box;
  }

  html,
  body,
  #root {
    height: 100%;
  }

  body {
    margin: 0;
    background: ${({ theme }) => theme.color.surfaceAlt};
    color: ${({ theme }) => theme.color.ink};
    font-family: ${({ theme }) => theme.font.body};
    font-size: ${({ theme }) => theme.fontSize.md};
    line-height: ${({ theme }) => theme.lineHeight.normal};
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
    text-rendering: optimizeLegibility;
  }

  /* Headings get the serif face: names and generation labels are the content, and they should
     read as inscribed rather than as UI chrome (doc 07 §3.3). */
  h1, h2, h3 {
    font-family: ${({ theme }) => theme.font.serif};
    font-weight: ${({ theme }) => theme.fontWeight.semibold};
    line-height: ${({ theme }) => theme.lineHeight.tight};
    letter-spacing: -0.01em;
    margin: 0;
  }

  h4, h5, h6 {
    font-weight: ${({ theme }) => theme.fontWeight.semibold};
    margin: 0;
  }

  p {
    margin: 0;
  }

  /* Keyboard users must always be able to see where they are — the tree is navigated by arrow
     keys (doc 07 §7.4), so this is a functional requirement, not a nicety. */
  :focus-visible {
    outline: 2px solid ${({ theme }) => theme.color.primary};
    outline-offset: 2px;
    border-radius: ${({ theme }) => theme.radius.sm};
  }

  ::selection {
    background: ${({ theme }) => theme.color.primarySubtle};
    color: ${({ theme }) => theme.color.primaryActive};
  }

  /* A minimal scrollbar; the default Windows one is visually louder than anything on the page. */
  ::-webkit-scrollbar {
    width: 10px;
    height: 10px;
  }

  ::-webkit-scrollbar-thumb {
    background: ${({ theme }) => theme.color.borderStrong};
    border: 3px solid transparent;
    background-clip: padding-box;
    border-radius: ${({ theme }) => theme.radius.pill};
  }

  ::-webkit-scrollbar-thumb:hover {
    background: ${({ theme }) => theme.color.inkSubtle};
    border: 3px solid transparent;
    background-clip: padding-box;
  }

  ::-webkit-scrollbar-track {
    background: transparent;
  }

  @media (prefers-reduced-motion: reduce) {
    *,
    *::before,
    *::after {
      animation-duration: 0.01ms !important;
      animation-iteration-count: 1 !important;
      transition-duration: 0.01ms !important;
      scroll-behavior: auto !important;
    }
  }
`;
