import styled, { css } from 'styled-components';

/**
 * The canvas's styled parts (doc 07 §3.2 — every colour and gap comes from a token).
 *
 * SVG elements are styled here exactly like DOM elements are: `fill` and `stroke` are properties
 * styled-components sets happily, and doing it here rather than with attributes on each element
 * keeps the tree's palette in one place and out of the renderer.
 */

/** The frame the canvas fills. `relative` so the minimap and the toolbar can sit over it. */
export const CanvasFrame = styled.div`
  position: relative;
  overflow: hidden;
  /* Tall enough to see a few generations of a large clan without going fullscreen. */
  height: clamp(420px, calc(100vh - 220px), 1100px);
  border: 1px solid ${({ theme }) => theme.color.border};
  border-radius: ${({ theme }) => theme.radius.md};
  background: ${({ theme }) => theme.color.surfaceAlt};
  touch-action: none;
`;

export const CanvasSurface = styled.svg<{ $panning: boolean }>`
  display: block;
  width: 100%;
  height: 100%;
  cursor: ${({ $panning }) => ($panning ? 'grabbing' : 'grab')};

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.color.primary};
    outline-offset: -2px;
  }
`;

/** A *đời* line, and the label that sits on it. */
export const RailLine = styled.line`
  stroke: ${({ theme }) => theme.color.border};
  stroke-width: 1;
  stroke-dasharray: 2 6;
`;

export const RailLabel = styled.text`
  fill: ${({ theme }) => theme.color.inkSubtle};
  font-family: ${({ theme }) => theme.font.serif};
  font-size: 13px;
  letter-spacing: 0.04em;
`;

/**
 * A parent → child connector.
 *
 * An adopted, step or fostered child is drawn dashed. The family recorded a difference and the
 * picture should not quietly erase it — but it is a dash, not a colour, because it is a fact about
 * the relationship and not a warning about it.
 */
export const ParentEdgePath = styled.path<{ $dashed: boolean; $dimmed: boolean }>`
  fill: none;
  stroke: ${({ theme }) => theme.color.borderStrong};
  stroke-width: 1.5;
  stroke-linejoin: round;
  ${({ $dashed }) =>
    $dashed &&
    css`
      stroke-dasharray: 5 4;
    `}
  opacity: ${({ $dimmed }) => ($dimmed ? 0.25 : 1)};
`;

/**
 * The bar between two people who married.
 *
 * A *thứ thất* or a remarriage is drawn thinner and dashed — visibly a second union rather than a
 * lesser one. Older *gia phả* record several wives as a matter of course (phase 5 §8), so the
 * distinction has to be legible without being a judgement.
 */
export const UnionLine = styled.line<{ $secondary: boolean; $dimmed: boolean }>`
  stroke: ${({ theme, $secondary }) =>
    $secondary ? theme.color.inkSubtle : theme.color.borderStrong};
  stroke-width: ${({ $secondary }) => ($secondary ? 1.5 : 2.5)};
  ${({ $secondary }) =>
    $secondary &&
    css`
      stroke-dasharray: 4 3;
    `}
  opacity: ${({ $dimmed }) => ($dimmed ? 0.25 : 1)};
`;

/** The faint panel behind a stack of siblings, so the columns read as one family (#60). */
export const StackPanel = styled.rect`
  fill: ${({ theme }) => theme.color.surfaceSunken};
  fill-opacity: 0.6;
  stroke: ${({ theme }) => theme.color.border};
  stroke-dasharray: 3 4;
`;

/** The whole card, including its rail, avatar and text. */
export const NodeGroup = styled.g<{ $dimmed: boolean }>`
  opacity: ${({ $dimmed }) => ($dimmed ? 0.28 : 1)};
  cursor: pointer;
  transition: opacity ${({ theme }) => theme.motion.fast};

  &:focus {
    outline: none;
  }
`;

export const NodeCard = styled.rect<{ $selected: boolean; $readOnly: boolean }>`
  fill: ${({ theme, $readOnly }) => ($readOnly ? theme.color.surfaceAlt : theme.color.surface)};
  stroke: ${({ theme, $selected }) => ($selected ? theme.color.primary : theme.color.border)};
  stroke-width: ${({ $selected }) => ($selected ? 2 : 1)};

  ${NodeGroup}:hover & {
    stroke: ${({ theme }) => theme.color.borderStrong};
  }

  ${NodeGroup}:focus-visible & {
    stroke: ${({ theme }) => theme.color.primary};
    stroke-width: 2;
  }
`;

/** The 3px rail down the left edge of a card, coloured by gender (doc 07 §7.3). */
export const GenderRail = styled.rect<{ $gender: 'MALE' | 'FEMALE' | 'OTHER' | 'UNKNOWN' }>`
  fill: ${({ theme, $gender }) => {
    if ($gender === 'MALE') return theme.color.male;
    if ($gender === 'FEMALE') return theme.color.female;
    return theme.color.unknownGender;
  }};
`;

export const AvatarCircle = styled.circle<{ $deceased: boolean }>`
  fill: ${({ theme, $deceased }) =>
    $deceased ? theme.color.surfaceSunken : theme.color.primarySubtle};
  stroke: ${({ theme }) => theme.color.border};
  stroke-width: 1;
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
  stroke: ${({ theme, $self }) => ($self ? theme.color.primary : theme.color.success)};
  stroke-width: ${({ $self }) => ($self ? 2 : 1.5)};
`;

export const AvatarInitials = styled.text`
  fill: ${({ theme }) => theme.color.inkMuted};
  font-family: ${({ theme }) => theme.font.body};
  font-size: 12px;
  font-weight: ${({ theme }) => theme.fontWeight.semibold};
  text-anchor: middle;
  dominant-baseline: central;
`;

export const NodeName = styled.text<{ $deceased: boolean }>`
  fill: ${({ theme, $deceased }) => ($deceased ? theme.color.deceased : theme.color.ink)};
  font-family: ${({ theme }) => theme.font.serif};
  font-size: 14px;
  font-weight: ${({ theme }) => theme.fontWeight.semibold};
`;

export const NodeMeta = styled.text`
  fill: ${({ theme }) => theme.color.inkSubtle};
  font-family: ${({ theme }) => theme.font.body};
  font-size: 11px;
`;

/** The branch a node belongs to, as a pill in the corner of the card. */
export const BranchPill = styled.rect<{ $highlighted: boolean }>`
  fill: ${({ theme, $highlighted }) =>
    $highlighted ? theme.color.primarySubtle : theme.color.surfaceSunken};
  stroke: ${({ theme, $highlighted }) =>
    $highlighted ? theme.color.primaryBorder : 'transparent'};
`;

export const BranchPillText = styled.text<{ $highlighted: boolean }>`
  fill: ${({ theme, $highlighted }) =>
    $highlighted ? theme.color.primary : theme.color.inkSubtle};
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
  fill: ${({ theme }) => theme.color.surface};
  stroke: ${({ theme }) => theme.color.border};
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
    fill: ${({ theme }) => theme.color.surface};
    stroke: ${({ theme }) => theme.color.borderStrong};
  }

  path {
    fill: none;
    stroke: ${({ theme }) => theme.color.inkMuted};
    stroke-width: 1.6;
    stroke-linecap: round;
  }

  &:hover circle,
  &:focus-visible circle {
    fill: ${({ theme }) => theme.color.primarySubtle};
    stroke: ${({ theme }) => theme.color.primary};
  }

  &:hover path,
  &:focus-visible path {
    stroke: ${({ theme }) => theme.color.primary};
  }
`;

/** The collapse/expand handle under a slot with descendants. */
export const CollapseToggle = styled.g`
  cursor: pointer;

  circle {
    fill: ${({ theme }) => theme.color.surface};
    stroke: ${({ theme }) => theme.color.borderStrong};
  }

  path {
    stroke: ${({ theme }) => theme.color.inkMuted};
    stroke-width: 1.6;
    stroke-linecap: round;
  }

  &:hover circle {
    stroke: ${({ theme }) => theme.color.primary};
  }
`;

export const CollapsedCount = styled.text`
  fill: ${({ theme }) => theme.color.inkSubtle};
  font-family: ${({ theme }) => theme.font.body};
  font-size: 10px;
  text-anchor: middle;
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
  border: 1px solid ${({ theme }) => theme.color.border};
  border-radius: ${({ theme }) => theme.radius.sm};
  background: ${({ theme }) => theme.color.surface};
  box-shadow: ${({ theme }) => theme.shadow.raised};
`;

export const MiniMapSurface = styled.svg`
  display: block;
  cursor: pointer;
`;

export const MiniMapNode = styled.rect<{ $highlighted: boolean }>`
  fill: ${({ theme, $highlighted }) => ($highlighted ? theme.color.primary : theme.color.borderStrong)};
`;

export const MiniMapViewport = styled.rect`
  fill: ${({ theme }) => theme.color.primarySubtle};
  fill-opacity: 0.35;
  stroke: ${({ theme }) => theme.color.primary};
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
  background: ${({ theme }) => theme.color.surfaceAlt};
`;

export const OverlayTitle = styled.h2`
  margin: 0;
  font-family: ${({ theme }) => theme.font.serif};
  font-size: ${({ theme }) => theme.fontSize.xl};
  font-weight: ${({ theme }) => theme.fontWeight.semibold};
  color: ${({ theme }) => theme.color.ink};
`;

export const OverlayBody = styled.p`
  margin: 0;
  max-width: 46ch;
  color: ${({ theme }) => theme.color.inkMuted};
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
