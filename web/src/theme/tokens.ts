/** The tree canvas's light scheme — see `tokens.tree`. */
const treeLight = {
  surface: '#FAFAF9',
  rail: '#D2D2CE',
  railLabel: '#5F646C',
  railLabelBg: '#F2F2F0',
  edge: '#767C85',
  union: '#4E545C',
  unionSecondary: '#767C85',
  stackPanel: '#F2F2F0',
  stackBorder: '#DCDCD8',
  card: '#FFFFFF',
  cardBorder: '#E1E0DC',
  cardBorderHover: '#B9B8B3',
  cardShadow: 'rgba(23, 25, 28, 0.06)',
  ink: '#17191C',
  inkMuted: '#5F646C',
  inkSubtle: '#737982',
  /* A person's card is coloured by who they are: blue or pink while alive, gray once dead. */
  maleCard: '#E3EDF8',
  maleBorder: '#AFC6E0',
  maleAvatar: '#C7DBEF',
  maleInitials: '#2E4F70',
  femaleCard: '#F8E6EE',
  femaleBorder: '#DDB4C6',
  femaleAvatar: '#EECFDC',
  femaleInitials: '#74374F',
  deceasedCard: '#D3D6DA',
  deceasedBorder: '#A9AEB5',
  deceasedAvatar: '#B8BDC3',
  deceasedInitials: '#2C3035',
  deceasedInk: '#17191C',
  deceasedMeta: '#43474D',
  deceasedPill: '#BFC3C9',
  accent: '#A6503F',
  accentSubtle: '#F7EEEB',
  accentBorder: '#E4CCC5',
  accentInk: '#FFFFFF',
  linked: '#2F7D5C',
  pill: 'rgba(23, 25, 28, 0.06)',
  control: '#FFFFFF',
  controlBorder: '#B9B8B3',
  minimapNode: '#BDBDB8',
  minimapViewport: 'rgba(166, 80, 63, 0.14)',
};

/** The tree canvas's dark scheme. `satisfies` holds it to exactly the light scheme's roles. */
const treeDark = {
  surface: '#121518',
  rail: '#2F353D',
  railLabel: '#AEB5BF',
  railLabelBg: '#1D2126',
  edge: '#8B95A2',
  union: '#C3CAD3',
  unionSecondary: '#8B95A2',
  stackPanel: '#171B1F',
  stackBorder: '#2A3038',
  card: '#1C2026',
  cardBorder: '#2F353D',
  cardBorderHover: '#4C5562',
  cardShadow: 'rgba(0, 0, 0, 0.35)',
  ink: '#ECEEF1',
  inkMuted: '#AEB5BF',
  inkSubtle: '#8A939F',
  maleCard: '#1A2A3C',
  maleBorder: '#2F4C6B',
  maleAvatar: '#2A4463',
  maleInitials: '#BFD6EE',
  femaleCard: '#33202B',
  femaleBorder: '#5C3549',
  femaleAvatar: '#4E2E3F',
  femaleInitials: '#EBC4D6',
  deceasedCard: '#363A41',
  deceasedBorder: '#4D535C',
  deceasedAvatar: '#4A5058',
  deceasedInitials: '#E3E6EA',
  deceasedInk: '#E3E6EA',
  deceasedMeta: '#BCC2CA',
  deceasedPill: '#4A5058',
  accent: '#E08A76',
  accentSubtle: '#3A2622',
  accentBorder: '#6B3D33',
  accentInk: '#121518',
  linked: '#5FB892',
  pill: 'rgba(255, 255, 255, 0.08)',
  control: '#1C2026',
  controlBorder: '#4C5562',
  minimapNode: '#4C5562',
  minimapViewport: 'rgba(224, 138, 118, 0.18)',
} satisfies Record<keyof typeof treeLight, string>;

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
 * The app chrome is light-only in v1 (the tree canvas carries its own light and dark sets, see
 * `tree` below), but every value is a role rather than a literal, so a
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
    deceased: '#8F959E',
  },

  /**
   * The tree canvas's own palette, light and dark (#61).
   *
   * The canvas is the one surface that carries its own scheme: somebody reading a large *gia phả*
   * for an hour may want it dark while the forms around it stay light, and printing always wants
   * light whatever the screen shows. So these are not swapped through `ThemeProvider` — the canvas
   * frame publishes the chosen set as `--tree-*` custom properties and every part inside it reads
   * those (see `components/canvas/styles.ts`), which is also what lets the print sheet force
   * `light` without re-rendering anything with a different theme.
   *
   * Every role has a value in both sets; `TreePalette` is what keeps it that way. Edges are a
   * deliberate step darker (light) or lighter (dark) than the app's hairlines: in a family tree they
   * are content, and they must clear 3:1 against the surface (WCAG 1.4.11).
   */
  tree: {
    light: treeLight,
    dark: treeDark,
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

/** One of the canvas's two schemes. Both must name every role, so the type is the light set's. */
export type TreeScheme = keyof typeof tokens.tree;
export type TreePalette = Record<keyof typeof tokens.tree.light, string>;
