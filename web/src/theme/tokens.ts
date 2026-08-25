/**
 * The single source of design tokens.
 *
 * Both Ant Design (through `antdTheme.ts`) and styled-components (through `ThemeProvider`) read
 * from here, so the two can never drift. Nothing in the app hard-codes a colour, a radius or a
 * pixel gap — ESLint has a rule about it, and a review will catch the rest.
 *
 * **Direction: modern minimalist.** Near-white surfaces, a narrow neutral ramp, hairline borders
 * and one accent used sparingly. The accent is a muted clay rather than a saturated red: it keeps
 * the heritage warmth the product wants without the lacquered, folk-art register — and it is
 * nobody's idea of stock Ant Design blue.
 *
 * Dark mode is out of scope for v1, but every value below is a role rather than a literal, so a
 * second token set can be swapped in through `ThemeProvider` without touching a component.
 */
export const tokens = {
  color: {
    /** The one accent. Primary actions, links, focus rings — and little else. */
    primary: '#A6503F',
    primaryHover: '#8E4234',
    primaryActive: '#78372B',
    /** Tinted backgrounds for selected rows and subtle accent surfaces. */
    primarySubtle: '#F7EEEB',
    primaryBorder: '#E4CCC5',

    /** Text. `ink` is deliberately not pure black — pure black on white is harsh at small sizes. */
    ink: '#17191C',
    inkMuted: '#5F646C',
    inkSubtle: '#8F959E',
    inkInverse: '#FFFFFF',

    /** Surfaces, lightest first. */
    surface: '#FFFFFF',
    surfaceAlt: '#FAFAF9',
    surfaceSunken: '#F2F2F0',
    surfaceInverse: '#17191C',

    /** Hairlines. `border` is for structure, `borderStrong` for anything interactive. */
    border: '#E7E7E4',
    borderStrong: '#D2D2CE',

    /** Status. Desaturated to sit alongside the neutral palette rather than shout over it. */
    success: '#2F7D5C',
    successSubtle: '#EDF6F1',
    warning: '#A97721',
    warningSubtle: '#FBF4E6',
    danger: '#B4392C',
    dangerSubtle: '#FBEEEC',
    info: '#3D6389',
    infoSubtle: '#EDF2F7',

    /** Lineage semantics — the tree's gender rails and the muted treatment for the deceased. */
    male: '#3D6389',
    female: '#8A4A66',
    unknownGender: '#8F959E',
    deceased: '#8F959E',
  },

  /** A 4px rhythm. `space(3)` reads better than `12px` and cannot drift. */
  space: (n: number) => `${n * 4}px`,

  radius: {
    sm: '6px',
    md: '10px',
    lg: '16px',
    pill: '999px',
  },

  font: {
    /** Be Vietnam Pro carries full Vietnamese diacritics at every weight; the stack degrades well. */
    body: "'Be Vietnam Pro', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif",
    /** For names, generation labels and anything that should feel inscribed rather than typed. */
    serif: "'Noto Serif', Georgia, 'Times New Roman', serif",
    mono: "'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace",
  },

  /** A tight scale — six sizes is enough for an application, and more invites inconsistency. */
  fontSize: {
    xs: '12px',
    sm: '13px',
    md: '14px',
    lg: '16px',
    xl: '20px',
    xxl: '28px',
  },

  lineHeight: {
    tight: 1.25,
    normal: 1.55,
    relaxed: 1.75,
  },

  fontWeight: {
    regular: 400,
    medium: 500,
    semibold: 600,
  },

  /** Shadows stay barely-there; separation comes from hairlines and whitespace. */
  shadow: {
    none: 'none',
    card: '0 1px 2px rgba(23, 25, 28, 0.04), 0 1px 3px rgba(23, 25, 28, 0.06)',
    raised: '0 2px 8px rgba(23, 25, 28, 0.07)',
    float: '0 8px 28px rgba(23, 25, 28, 0.12)',
    focus: '0 0 0 3px rgba(166, 80, 63, 0.18)',
  },

  motion: {
    fast: '120ms cubic-bezier(0.2, 0, 0, 1)',
    normal: '200ms cubic-bezier(0.2, 0, 0, 1)',
  },

  z: {
    canvas: 1,
    sticky: 10,
    drawer: 100,
    modal: 1000,
    toast: 1100,
  },

  layout: {
    sidebarWidth: '240px',
    sidebarCollapsedWidth: '64px',
    topBarHeight: '56px',
    contentMaxWidth: '1200px',
  },
} as const;

export type Tokens = typeof tokens;
