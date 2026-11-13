import { type TFunction } from 'i18next';
import { memo } from 'react';
import { useTranslation } from 'react-i18next';

import { type PersonSummary } from '@/api/endpoints/persons.ts';

import { estimateTextWidth, fitFirst, fitName, fitText, fitWords } from '../../model/fitText';
import { formatCompactLifespan, formatGeneration, formatLifespan } from '../../model/format';
import { CARD, type LaidOutCard } from '../../model/layout';
import { GENDER_STYLE, genderShapePath, visualOf } from '../../model/visual';

import {
  ActionBridge,
  ActionButton,
  ActionLayer,
  ActionStrip,
  AvatarInitials,
  AvatarShape,
  BranchPill,
  BranchPillText,
  GenderRail,
  LinkRing,
  MourningBand,
  NodeCard,
  NodeGroup,
  NodeMeta,
  NodeName,
  NodeShadow,
  SelfTag,
} from './styles';

/** Where the text column starts: past the avatar and its gap. */
const TEXT_X = 52;

/** How wide the text column is: to the card's right edge, less a hair of breathing room. */
const TEXT_ROOM = CARD.width - TEXT_X - 4;

/** The font sizes of `NodeName`, `NodeMeta` and `BranchPillText` in `styles.ts`. */
const NAME_FONT_SIZE = 14;
const META_FONT_SIZE = 11;
const PILL_FONT_SIZE = 10;

/** The space inside the branch pill on either side of its label. */
const PILL_PADDING = 7;

/** The avatar's centre and size. */
const AVATAR = { cx: 30, cy: 26, r: 14 } as const;

/** The "You" tag above the viewer's own card. */
const SELF_TAG = { x: 10, y: -9, height: 15, padding: 7, fontSize: 10 } as const;

/** The mourning band across the top-left corner of a deceased person's card (#61). */
const MOURNING_BAND = 'M 0 16 L 16 0 L 23 0 L 0 23 Z';

/**
 * One person's card on the canvas (F5.5, F5.11, F5.12).
 *
 * **Memoized on purpose.** A five-thousand-person tree renders as many of these as fit the viewport,
 * and panning changes one number on the parent. Without the memo every frame re-renders every card;
 * with it, a pan re-renders nothing (F5.15).
 *
 * **A read-only node is not a disabled node.** When the caller cannot edit somebody the actions are
 * absent — not greyed out — and the card carries a sentence saying which part of the tree they *can*
 * edit (F5.12, doc 04 §6). The tree still shows the whole clan: branch scope constrains writing, not
 * reading, and the picture is where that distinction becomes obvious.
 */

/** What a hover action on a node asks for. The page decides which form to open. */
export type NodeAction = 'add-child' | 'add-spouse' | 'edit' | 'delete';

interface PersonNodeProps {
  card: LaidOutCard;
  /** Dimmed because a branch filter is on and this person is not in it. */
  dimmed: boolean;
  selected: boolean;
  /** The branch's name, for the pill. Undefined for an unplaced node. */
  branchName?: string;
  /** True when the branch filter names this person's branch. */
  branchHighlighted: boolean;
  /** True when this node is the signed-in account's own — the ring is drawn in the accent (F6.7). */
  isSelf: boolean;
  /**
   * False on the print sheet and in exports: no actions, no focus, no click — a picture of the
   * card rather than a control. Defaults to true.
   */
  interactive?: boolean;
  onOpen?: (personId: string) => void;
  onAction?: (action: NodeAction, person: PersonSummary) => void;
}

/** Icon paths, drawn in a 20×20 box centred on the origin. */
const ICON = {
  'add-child': 'M -4 0 H 4 M 0 -4 V 4',
  'add-spouse': 'M -5 -2 A 2.4 2.4 0 0 1 0 -2 A 2.4 2.4 0 0 1 5 -2 Q 5 1.5 0 4 Q -5 1.5 -5 -2 Z',
  edit: 'M -4.5 4.5 L -4.5 1.5 L 1.5 -4.5 L 4.5 -1.5 L -1.5 4.5 Z',
  delete: 'M -4 -2.5 H 4 M -2.5 -2.5 V 3.5 M 0 -2.5 V 3.5 M 2.5 -2.5 V 3.5 M -2.5 -4.5 H 2.5',
} as const;

const ACTIONS: NodeAction[] = ['add-child', 'add-spouse', 'edit', 'delete'];

/** Centre-to-centre distance between two action buttons, and the strip's padding around them. */
const ACTION_SPACING = 26;
const ACTION_PAD = 14;

function PersonNodeComponent({
  card,
  dimmed,
  selected,
  branchName,
  branchHighlighted,
  isSelf,
  interactive = true,
  onOpen,
  onAction,
}: PersonNodeProps) {
  const { t } = useTranslation();
  const person = card.person;
  const visual = visualOf(person, isSelf);
  const { deceased } = visual;
  const gender = GENDER_STYLE[visual.gender];
  const selfLabel = t('tree.canvas.you');
  const selfTagWidth =
    estimateTextWidth(selfLabel, SELF_TAG.fontSize) * 1.15 + SELF_TAG.padding * 2;
  const label = describe(person, branchName, isSelf, t);
  // As much of the lifespan as there is room for: with its lunar readings, then without them, and
  // cut only if even the bare years do not fit. With no dates at all, the đời.
  const metaLine =
    fitFirst(
      [formatLifespan(person, t), formatCompactLifespan(person, t)],
      TEXT_ROOM,
      META_FONT_SIZE,
    ) ?? fitText(formatGeneration(person.generation, t), TEXT_ROOM, META_FONT_SIZE);
  // The pill is as wide as its label needs and no wider than the column it sits in.
  const pillLabel =
    branchName === undefined
      ? ''
      : fitWords(branchName, TEXT_ROOM - 2 * PILL_PADDING, PILL_FONT_SIZE);
  const pillWidth = Math.min(
    TEXT_ROOM,
    estimateTextWidth(pillLabel, PILL_FONT_SIZE) + 2 * PILL_PADDING,
  );

  const interaction = interactive
    ? {
        role: 'button' as const,
        tabIndex: -1,
        'aria-label': label,
        onClick: () => {
          onOpen?.(person.id);
        },
        onKeyDown: (event: React.KeyboardEvent<SVGGElement>) => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            onOpen?.(person.id);
          }
        },
      }
    : {};

  return (
    <NodeGroup
      transform={`translate(${card.x}, ${card.y})`}
      $dimmed={dimmed}
      $interactive={interactive}
      {...interaction}
    >
      <title>{label}</title>

      <NodeShadow x={1} y={2} width={card.width} height={card.height} rx={10} />
      <NodeCard
        width={card.width}
        height={card.height}
        rx={10}
        $selected={selected}
        $readOnly={!person.canEdit}
      />
      <GenderRail x={1} y={10} width={3} height={card.height - 20} rx={1.5} $tone={gender.tone} />
      {deceased && <MourningBand d={MOURNING_BAND} />}

      {/* The shape is the gender (square, circle, diamond, open): legible without its colour. */}
      <AvatarShape
        d={genderShapePath(gender.shape, AVATAR.cx, AVATAR.cy, AVATAR.r)}
        $tone={gender.tone}
        $deceased={deceased}
        $open={gender.shape === 'open'}
      />
      {visual.linked && (
        <LinkRing cx={AVATAR.cx} cy={AVATAR.cy} r={AVATAR.r + 3.5} $self={isSelf} />
      )}
      <AvatarInitials x={AVATAR.cx} y={AVATAR.cy}>
        {initialsOf(person.fullName)}
      </AvatarInitials>

      {isSelf && (
        <SelfTag transform={`translate(${SELF_TAG.x}, ${SELF_TAG.y})`}>
          <rect width={selfTagWidth} height={SELF_TAG.height} rx={SELF_TAG.height / 2} />
          <text x={SELF_TAG.padding} y={SELF_TAG.height / 2 + SELF_TAG.fontSize * 0.36}>
            {selfLabel}
          </text>
        </SelfTag>
      )}

      {/*
        The text column, as a nested viewport. An inner <svg> clips to its own box, which is the
        guarantee that nothing written on a card is ever painted over the card beside it — whatever
        font the browser ends up with. `fitText` is what makes the cut read as a cut, with an
        ellipsis, instead of as a letter sliced in half.
      */}
      <svg x={TEXT_X} y={0} width={TEXT_ROOM} height={card.height}>
        <NodeName x={0} y={24} $deceased={deceased}>
          {fitName(person.fullName, TEXT_ROOM, NAME_FONT_SIZE)}
        </NodeName>
        <NodeMeta x={0} y={41}>
          {metaLine}
        </NodeMeta>

        {branchName !== undefined && (
          <>
            <BranchPill
              x={0}
              y={48}
              width={pillWidth}
              height={16}
              rx={8}
              $highlighted={branchHighlighted}
            />
            <BranchPillText x={PILL_PADDING} y={59} $highlighted={branchHighlighted}>
              {pillLabel}
            </BranchPillText>
          </>
        )}
      </svg>

      {interactive && onAction !== undefined && person.canEdit && (
        <ActionLayer transform={`translate(${card.width - 10}, -10)`}>
          {/*
            One solid strip under the buttons, reaching down into the card. Without it the gaps
            between two icons — and between the icons and the card — belong to nothing, so the
            pointer crossing them leaves the node and the whole bar vanished mid-reach (#60).
          */}
          <ActionBridge
            x={-ACTION_SPACING * (ACTIONS.length - 1) - ACTION_PAD}
            y={-ACTION_PAD}
            width={ACTION_SPACING * (ACTIONS.length - 1) + ACTION_PAD * 2}
            height={ACTION_PAD + 10 + 12}
          />
          <ActionStrip
            x={-ACTION_SPACING * (ACTIONS.length - 1) - ACTION_PAD}
            y={-ACTION_PAD}
            width={ACTION_SPACING * (ACTIONS.length - 1) + ACTION_PAD * 2}
            height={ACTION_PAD * 2}
            rx={ACTION_PAD}
          />
          {ACTIONS.map((action, index) => (
            <ActionButton
              key={action}
              transform={`translate(${(index - ACTIONS.length + 1) * ACTION_SPACING}, 0)`}
              role="button"
              tabIndex={-1}
              aria-label={t(`tree.canvas.action.${action}`, { name: person.fullName })}
              onClick={(event) => {
                event.stopPropagation();
                onAction(action, person);
              }}
              onKeyDown={(event) => {
                if (event.key !== 'Enter' && event.key !== ' ') return;
                event.preventDefault();
                event.stopPropagation();
                onAction(action, person);
              }}
            >
              <title>{t(`tree.canvas.action.${action}`, { name: person.fullName })}</title>
              <circle r={10} />
              <path d={ICON[action]} />
            </ActionButton>
          ))}
        </ActionLayer>
      )}
    </NodeGroup>
  );
}

export const PersonNode = memo(PersonNodeComponent);

/**
 * The card, said out loud.
 *
 * Everything the card shows plus the thing it only implies: whether this node can be edited, and if
 * not, why. A screen reader on the canvas should learn what a sighted person learns from a muted
 * card and a tooltip — that is the whole of F5.12 for somebody who cannot see the dimming.
 */
function describe(
  person: PersonSummary,
  branchName: string | undefined,
  isSelf: boolean,
  t: TFunction,
): string {
  const parts = [person.fullName, formatGeneration(person.generation, t)];
  // The ring is a colour; this is the same fact in words, for somebody who cannot see it.
  if (isSelf) parts.push(t('claims.thisIsYou'));
  const lifespan = formatLifespan(person, t);
  if (lifespan !== undefined) parts.push(lifespan);
  if (branchName !== undefined) parts.push(branchName);
  parts.push(
    person.canEdit
      ? t('tree.canEdit')
      : branchName === undefined
        ? t('tree.readOnly.noBranch')
        : t('tree.readOnly.branch', { branch: branchName }),
  );
  return parts.join(' · ');
}

/**
 * Up to two initials, taken from the end of the name.
 *
 * Vietnamese names run family name first, so the last word is the given name — the one a person is
 * actually called by. "Nguyễn Văn An" gives "VA", not "NV" (Phase 7 replaces these with photos).
 */
function initialsOf(fullName: string): string {
  const words = fullName.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '?';
  return words
    .slice(-2)
    .map((word) => word.charAt(0).toLocaleUpperCase('vi'))
    .join('');
}
