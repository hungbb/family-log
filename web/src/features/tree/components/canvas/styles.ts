import styled, { css, keyframes } from 'styled-components';

import { tokens, type TreePalette, type TreeScheme } from '@/theme/tokens.ts';

/**
 * The canvas's styled parts (doc 07 §3.2 — every colour and gap comes from a token).
 *
 * SVG elements are styled here exactly like DOM elements are: `fill` and `stroke` are properties
 * styled-components sets happily, and doing it here rather than with attributes on each element
 * keeps the tree's palette in one place and out of the renderer.
 *
 * ## The canvas palette (#61)
 *
 * Everything inside the canvas reads its colour from a `--tree-*` custom property rather than from
 * `theme.color`. The frame publishes one of the two sets in `tokens.tree` — light or dark — and the
 * whole subtree, minimap and legend included, follows. The print sheet publishes the light set on
 * its own root, so the same components print light whatever the screen shows, without a second
 * `ThemeProvider` and without re-rendering a single card.
 */

type Role = keyof TreePalette;

/** `var(--tree-<role>)` — a colour from whichever scheme the nearest frame published. */
export function tv(role: Role): string {
  return `var(--tree-${role.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)})`;
}

/** The custom properties for one scheme, for a root that hosts canvas parts. */
export function schemeVars(scheme: TreeScheme) {
  const palette: TreePalette = tokens.tree[scheme];
  const declarations = Object.entries(palette)
    .map(([role, value]) => `${tv(role as Role).slice(4, -1)}: ${value};`)
    .join('\n');
  return css`
    ${declarations}
    color-scheme: ${scheme};
  `;
}

/** The colour role of a gender's tone. */
export type ToneRole = 'male' | 'female' | 'other' | 'unknown';

/** The frame the canvas fills. `relative` so the minimap and the legend can sit over it. */
export const CanvasFrame = styled.div<{ $scheme: TreeScheme }>`
  ${({ $scheme }) => schemeVars($scheme)}
  position: relative;
  overflow: hidden;
  /* Tall enough to see a few generations of a large clan without going fullscreen. */
  height: clamp(420px, calc(100vh - 220px), 1100px);
  border: 1px solid ${({ theme }) => theme.color.border};
  border-radius: ${({ theme }) => theme.radius.md};
  background: ${tv('surface')};
  color: ${tv('ink')};
  touch-action: none;
  transition: background-color ${({ theme }) => theme.motion.normal};
`;

export const CanvasSurface = styled.svg<{ $panning: boolean }>`
  display: block;
  width: 100%;
  height: 100%;
  cursor: ${({ $panning }) => ($panning ? 'grabbing' : 'grab')};

  &:focus-visible {
    outline: 2px solid ${tv('accent')};
    outline-offset: -2px;
  }
`;

/**
 * Connectors keep their weight on screen whatever the zoom (#61).
 *
 * `non-scaling-stroke` measures the stroke in screen pixels instead of canvas units, so the
 * viewport's scale no longer thins it to nothing at the bottom of the zoom range. The width itself
 * is an attribute on the layer that holds the connectors (see `screenStroke`), inherited by every
 * path in it — it changes with the zoom, and an attribute costs nothing where a styled prop would
 * mint a class per zoom level.
 */
const connector = css`
  fill: none;
  vector-effect: non-scaling-stroke;
  stroke-linejoin: round;
  stroke-linecap: round;
`;

const dimmable = css<{ $dimmed: boolean }>`
  opacity: ${({ $dimmed }) => ($dimmed ? 0.22 : 1)};
  transition: opacity 220ms cubic-bezier(0.2, 0, 0, 1);
`;

const fadeIn = keyframes`
  from { opacity: 0; }
  to { opacity: 1; }
`;

/** The layer the connectors are drawn in. Fades back in after the layout has moved (#61). */
export const ConnectorLayer = styled.g<{ $fadeIn: boolean }>`
  ${({ $fadeIn }) =>
    $fadeIn &&
    css`
      animation: ${fadeIn} 200ms ease-out both;
    `}
`;

/** A *đời* line. */
export const RailLine = styled.line`
  ${connector}
  stroke: ${tv('rail')};
  stroke-width: 1;
  stroke-dasharray: 2 6;
`;

/**
 * A *đời* label. Drawn in screen space, outside the zoomed group, so it is the same size at every
 * zoom and stays pinned to the edge of the frame while the tree pans under it (#61). The halo is the
 * surface colour painted under the glyphs, so a connector passing behind never cuts a letter.
 */
export const RailLabel = styled.text`
  fill: ${tv('railLabel')};
  font-family: ${({ theme }) => theme.font.serif};
  font-size: 12px;
  font-weight: ${({ theme }) => theme.fontWeight.semibold};
  letter-spacing: 0.04em;
  paint-order: stroke;
  stroke: ${tv('surface')};
  stroke-width: 4px;
  stroke-linejoin: round;
  pointer-events: none;
  user-select: none;
`;

/** The pill behind a *đời* label. */
export const RailLabelPill = styled.rect`
  fill: ${tv('railLabelBg')};
  stroke: ${tv('rail')};
  pointer-events: none;
`;

/**
 * A parent → child connector.
 *
 * An adopted, step or fostered child is drawn dashed. The family recorded a difference and the
 * picture should not quietly erase it — but it is a dash, not a colour, because it is a fact about
 * the relationship and not a warning about it.
 */
export const ParentEdgePath = styled.path<{ $dashed: boolean; $dimmed: boolean }>`
  ${connector}
  stroke: ${tv('edge')};
  ${({ $dashed }) =>
    $dashed &&
    css`
      stroke-dasharray: 6 4;
    `}
  ${dimmable}
`;

/**
 * The bar between two people who married.
 *
 * A *thứ thất* or a remarriage is drawn thinner and dashed — visibly a second union rather than a
 * lesser one. Older *gia phả* record several wives as a matter of course (phase 5 §8), so the
 * distinction has to be legible without being a judgement. The weight is the layer's attribute;
 * the secondary layer is lighter.
 */
export const UnionLine = styled.line<{ $secondary: boolean; $dimmed: boolean }>`
  ${connector}
  stroke: ${({ $secondary }) => ($secondary ? tv('unionSecondary') : tv('union'))};
  ${({ $secondary }) =>
    $secondary &&
    css`
      stroke-dasharray: 5 3;
    `}
  ${dimmable}
`;

/** The faint panel behind a stack of siblings, so the columns read as one family (#60). */
export const StackPanel = styled.rect`
  fill: ${tv('stackPanel')};
  stroke: ${tv('stackBorder')};
  stroke-dasharray: 3 4;
  vector-effect: non-scaling-stroke;
`;

/** The whole card, including its rail, avatar and text. */
export const NodeGroup = styled.g<{ $dimmed: boolean; $interactive: boolean }>`
  ${dimmable}
  cursor: ${({ $interactive }) => ($interactive ? 'pointer' : 'default')};

  &:focus {
    outline: none;
  }
`;

export const NodeCard = styled.rect<{ $selected: boolean; $readOnly: boolean }>`
  fill: ${({ $readOnly }) => ($readOnly ? tv('cardReadOnly') : tv('card'))};
  stroke: ${({ $selected }) => ($selected ? tv('accent') : tv('cardBorder'))};
  stroke-width: ${({ $selected }) => ($selected ? 2 : 1)};
  transition: stroke ${({ theme }) => theme.motion.fast};

  ${NodeGroup}:hover & {
    stroke: ${({ $selected }) => ($selected ? tv('accent') : tv('cardBorderHover'))};
  }

  ${NodeGroup}:focus-visible & {
    stroke: ${tv('accent')};
    stroke-width: 2;
  }
`;

/** A soft shadow under a card, drawn as a second offset rect rather than a filter — filters on
 * thousands of groups are what makes a pan stutter. */
export const NodeShadow = styled.rect`
  fill: ${tv('cardShadow')};
`;

/** The rail down the left edge of a card, coloured by gender (doc 07 §7.3). */
export const GenderRail = styled.rect<{ $tone: ToneRole }>`
  fill: ${({ $tone }) => tv($tone)};
`;

/**
 * The avatar, whose *shape* says the gender (#61): square, circle, diamond, or an open dashed
 * circle for unknown — the pedigree-chart convention, so the category survives greyscale and print.
 */
export const AvatarShape = styled.path<{ $tone: ToneRole; $deceased: boolean; $open: boolean }>`
  fill: ${({ $deceased }) => ($deceased ? tv('avatarDeceased') : tv('avatar'))};
  stroke: ${({ $tone }) => tv($tone)};
  stroke-width: 1.5;
  ${({ $open }) =>
    $open &&
    css`
      stroke-dasharray: 3 2.5;
    `}
`;

/**
 * The ring around the avatar of a node an account is linked to (F6.7).
 *
 * A ring rather than a badge, because it has to survive being read at half scale on a phone: at
 * that size a 6px icon is a smudge, but a coloured outline around a circle is still a coloured
 * outline. The caller's own node gets the accent colour and a thicker stroke — "where am I" is a
 * question people ask of a tree constantly, and the answer should be findable while panning.
 */
export const LinkRing = styled.circle<{ $self: boolean }>`
  fill: none;
  stroke: ${({ $self }) => ($self ? tv('accent') : tv('linked'))};
  stroke-width: ${({ $self }) => ($self ? 2.5 : 1.5)};
`;

/** "You", above the viewer's own card — the ring's colour, said in words (#61). */
export const SelfTag = styled.g`
  rect {
    fill: ${tv('accent')};
  }

  text {
    fill: ${tv('accentInk')};
    font-family: ${({ theme }) => theme.font.body};
    font-size: 10px;
    font-weight: ${({ theme }) => theme.fontWeight.semibold};
    letter-spacing: 0.06em;
    text-transform: uppercase;
  }
`;

/**
 * The band across the corner of a deceased person's card — the *khăn tang* folded over the corner
 * of a portrait. It is the non-colour half of "deceased": the muted name says it in colour, the
 * band says it in shape (#61).
 */
export const MourningBand = styled.path`
  fill: ${tv('mourning')};
`;

export const AvatarInitials = styled.text`
  fill: ${tv('inkMuted')};
  font-family: ${({ theme }) => theme.font.body};
  font-size: 12px;
  font-weight: ${({ theme }) => theme.fontWeight.semibold};
  text-anchor: middle;
  dominant-baseline: central;
  pointer-events: none;
`;

export const NodeName = styled.text<{ $deceased: boolean }>`
  fill: ${({ $deceased }) => ($deceased ? tv('deceased') : tv('ink'))};
  font-family: ${({ theme }) => theme.font.serif};
  font-size: 14px;
  font-weight: ${({ theme }) => theme.fontWeight.semibold};
`;

export const NodeMeta = styled.text`
  fill: ${tv('inkSubtle')};
  font-family: ${({ theme }) => theme.font.body};
  font-size: 11px;
`;

/** The branch a node belongs to, as a pill in the corner of the card. */
export const BranchPill = styled.rect<{ $highlighted: boolean }>`
  fill: ${({ $highlighted }) => ($highlighted ? tv('accentSubtle') : tv('pill'))};
  stroke: ${({ $highlighted }) => ($highlighted ? tv('accentBorder') : 'transparent')};
`;

export const BranchPillText = styled.text<{ $highlighted: boolean }>`
  fill: ${({ $highlighted }) => ($highlighted ? tv('accent') : tv('inkSubtle'))};
  font-family: ${({ theme }) => theme.font.body};
  font-size: 10px;
  font-weight: ${({ theme }) => theme.fontWeight.medium};
`;

/**
 * The hover actions on an editable node (F5.11). Hidden until the slot is hovered or focused.
 *
 * **Slow to leave, quick to arrive.** Hiding waits a moment before it starts: a hand moving from the
 * card to an icon, or from one icon to the next, overshoots by a few pixels all the time, and a bar
 * that disappears the instant the pointer slips is a bar nobody can use. While it is fading it is
 * still visible and still clickable, and coming back onto it cancels the fade. `visibility` rather
 * than `pointer-events` is what makes that work — it switches at the end of the delay, not the start.
 */
export const ActionLayer = styled.g`
  opacity: 0;
  visibility: hidden;
  transition:
    opacity 160ms ease 350ms,
    visibility 0s linear 510ms;

  ${NodeGroup}:hover &,
  ${NodeGroup}:focus-within & {
    opacity: 1;
    visibility: visible;
    transition:
      opacity 120ms ease 0s,
      visibility 0s linear 0s;
  }
`;

/** The pill the action buttons sit on. */
export const ActionStrip = styled.rect`
  fill: ${tv('control')};
  stroke: ${tv('cardBorder')};
  filter: drop-shadow(0 1px 2px rgb(0 0 0 / 12%));
`;

/** Invisible, but hit-testable: joins the strip to the card so the pointer never falls through. */
export const ActionBridge = styled.rect`
  fill: transparent;
  pointer-events: all;
`;

export const ActionButton = styled.g`
  cursor: pointer;

  circle {
    fill: ${tv('control')};
    stroke: ${tv('controlBorder')};
  }

  path {
    fill: none;
    stroke: ${tv('inkMuted')};
    stroke-width: 1.6;
    stroke-linecap: round;
  }

  &:hover circle,
  &:focus-visible circle {
    fill: ${tv('accentSubtle')};
    stroke: ${tv('accent')};
  }

  &:hover path,
  &:focus-visible path {
    stroke: ${tv('accent')};
  }
`;

/** The collapse/expand handle under a slot with descendants. */
export const CollapseToggle = styled.g`
  cursor: pointer;

  circle {
    fill: ${tv('control')};
    stroke: ${tv('controlBorder')};
    transition: stroke ${({ theme }) => theme.motion.fast};
  }

  path {
    stroke: ${tv('inkMuted')};
    stroke-width: 1.6;
    stroke-linecap: round;
  }

  &:hover circle {
    stroke: ${tv('accent')};
  }
`;

export const CollapsedCount = styled.text`
  fill: ${tv('inkSubtle')};
  font-family: ${({ theme }) => theme.font.body};
  font-size: 10px;
  text-anchor: middle;
`;

/** A slot drawn for its exit animation only: never hit-testable, never in the reading order. */
export const GhostGroup = styled.g`
  pointer-events: none;
`;

/**
 * A scope for canvas glyphs used outside the canvas — the list view's name column — so they read
 * the same palette. The app chrome is light, so this is the light set.
 */
export const GlyphScope = styled.span`
  ${schemeVars('light')}
  display: inline-flex;
  align-items: center;
  gap: ${({ theme }) => theme.space(1)};
  margin-right: ${({ theme }) => theme.space(2)};
  vertical-align: -2px;
`;

// --- the legend (#61) ----------------------------------------------------------------------------

export const LegendPanel = styled.div`
  position: absolute;
  left: ${({ theme }) => theme.space(3)};
  bottom: ${({ theme }) => theme.space(3)};
  max-width: min(320px, calc(100% - 24px));
  max-height: calc(100% - 24px);
  overflow: auto;
  border: 1px solid ${tv('cardBorder')};
  border-radius: ${({ theme }) => theme.radius.sm};
  background: ${tv('card')};
  color: ${tv('ink')};
  box-shadow: ${({ theme }) => theme.shadow.raised};
  font-size: ${({ theme }) => theme.fontSize.xs};
`;

export const LegendToggle = styled.button`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.space(2)};
  width: 100%;
  padding: ${({ theme }) => `${theme.space(2)} ${theme.space(3)}`};
  border: 0;
  background: transparent;
  color: ${tv('inkMuted')};
  font: inherit;
  font-weight: ${({ theme }) => theme.fontWeight.semibold};
  text-align: left;
  cursor: pointer;

  &:hover {
    color: ${tv('ink')};
  }
`;

export const LegendList = styled.dl`
  display: grid;
  grid-template-columns: 34px 1fr;
  align-items: center;
  gap: ${({ theme }) => `${theme.space(1.5)} ${theme.space(2)}`};
  margin: 0;
  padding: ${({ theme }) => `0 ${theme.space(3)} ${theme.space(3)}`};

  dt {
    display: flex;
    justify-content: center;
  }

  dd {
    margin: 0;
    color: ${tv('inkMuted')};
    line-height: ${({ theme }) => theme.lineHeight.tight};
  }
`;

export const LegendHeading = styled.p`
  grid-column: 1 / -1;
  margin: ${({ theme }) => `${theme.space(1)} 0 0`};
  color: ${tv('inkSubtle')};
  font-size: 11px;
  font-weight: ${({ theme }) => theme.fontWeight.semibold};
  letter-spacing: 0.06em;
  text-transform: uppercase;
`;

/** A swatch's tiny SVG. Connectors in it keep their real weights (no zoom to compensate for). */
export const LegendSwatch = styled.svg`
  display: block;
  overflow: visible;

  line,
  path {
    fill: none;
    stroke-linecap: round;
  }
`;

// --- chrome around the canvas ------------------------------------------------------------------

/**
 * The toolbar and the canvas together — the part that goes fullscreen (#60).
 *
 * Fullscreen is a fixed layer over the page rather than the browser's element fullscreen on its own:
 * a fullscreened element hides everything outside it, and the person drawer, the forms and every
 * dropdown are portalled to `<body>`, so clicking a card would open a drawer nobody could see. The
 * layer sits under AntD's popups (z-index 1000) and over the app shell, and the page asks the
 * browser for document fullscreen on top of it where that is available.
 */
export const TreeStage = styled.div<{ $fullscreen: boolean }>`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.space(2)};

  ${({ $fullscreen, theme }) =>
    $fullscreen &&
    css`
      position: fixed;
      inset: 0;
      z-index: ${theme.z.drawer};
      padding: ${theme.space(3)};
      background: ${theme.color.surfaceSunken};

      ${CanvasFrame} {
        flex: 1 1 auto;
        min-height: 0;
        height: auto;
      }
    `}
`;

export const ToolbarBar = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: ${({ theme }) => theme.space(2)};
  padding: ${({ theme }) => `${theme.space(2)} ${theme.space(3)}`};
  border: 1px solid ${({ theme }) => theme.color.border};
  border-radius: ${({ theme }) => theme.radius.md};
  background: ${({ theme }) => theme.color.surface};
`;

export const ToolbarSpacer = styled.span`
  flex: 1 1 auto;
`;

export const MiniMapFrame = styled.div`
  position: absolute;
  right: ${({ theme }) => theme.space(3)};
  bottom: ${({ theme }) => theme.space(3)};
  overflow: hidden;
  border: 1px solid ${tv('cardBorder')};
  border-radius: ${({ theme }) => theme.radius.sm};
  background: ${tv('card')};
  box-shadow: ${({ theme }) => theme.shadow.raised};
`;

export const MiniMapSurface = styled.svg`
  display: block;
  cursor: pointer;
`;

export const MiniMapNode = styled.rect<{ $highlighted: boolean }>`
  fill: ${({ $highlighted }) => ($highlighted ? tv('accent') : tv('minimapNode'))};
`;

/** The viewer's own node on the map — the same accent and the same ring as on the canvas. */
export const MiniMapSelf = styled.circle`
  fill: ${tv('accent')};
  stroke: ${tv('card')};
  stroke-width: 1.5;
`;

export const MiniMapViewport = styled.rect`
  fill: ${tv('minimapViewport')};
  stroke: ${tv('accent')};
  stroke-width: 1;
`;

/** The overlay a skeleton or an empty state sits in, centred over the canvas frame. */
export const CanvasOverlay = styled.div`
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: ${({ theme }) => theme.space(3)};
  padding: ${({ theme }) => theme.space(6)};
  text-align: center;
  background: ${tv('surface')};
`;

export const OverlayTitle = styled.h2`
  margin: 0;
  font-family: ${({ theme }) => theme.font.serif};
  font-size: ${({ theme }) => theme.fontSize.xl};
  font-weight: ${({ theme }) => theme.fontWeight.semibold};
  color: ${tv('ink')};
`;

export const OverlayBody = styled.p`
  margin: 0;
  max-width: 46ch;
  color: ${tv('inkMuted')};
  font-size: ${({ theme }) => theme.fontSize.md};
  line-height: ${({ theme }) => theme.lineHeight.normal};
`;

/** The skeleton the canvas shows while the payload is in flight (F5.14). */
export const SkeletonCard = styled.rect`
  fill: ${({ theme }) => theme.color.surfaceSunken};
  opacity: 0.7;
`;

/** A live region announcing what the keyboard did, for people who cannot see the canvas move. */
export const VisuallyHidden = styled.span`
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
  border: 0;
`;
