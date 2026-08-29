import { type TFunction } from 'i18next';
import { memo } from 'react';
import { useTranslation } from 'react-i18next';

import { type PersonSummary } from '@/api/endpoints/persons.ts';

import { formatGeneration, formatLifespan } from '../../model/format';
import { type LaidOutCard } from '../../model/layout';

import {
  ActionButton,
  ActionLayer,
  AvatarCircle,
  AvatarInitials,
  BranchPill,
  BranchPillText,
  GenderRail,
  LinkRing,
  NodeCard,
  NodeGroup,
  NodeMeta,
  NodeName,
} from './styles';

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
  onOpen: (personId: string) => void;
  onAction: (action: NodeAction, person: PersonSummary) => void;
}

/** Icon paths, drawn in a 20×20 box centred on the origin. */
const ICON = {
  'add-child': 'M -4 0 H 4 M 0 -4 V 4',
  'add-spouse': 'M -5 -2 A 2.4 2.4 0 0 1 0 -2 A 2.4 2.4 0 0 1 5 -2 Q 5 1.5 0 4 Q -5 1.5 -5 -2 Z',
  edit: 'M -4.5 4.5 L -4.5 1.5 L 1.5 -4.5 L 4.5 -1.5 L -1.5 4.5 Z',
  delete: 'M -4 -2.5 H 4 M -2.5 -2.5 V 3.5 M 0 -2.5 V 3.5 M 2.5 -2.5 V 3.5 M -2.5 -4.5 H 2.5',
} as const;

const ACTIONS: NodeAction[] = ['add-child', 'add-spouse', 'edit', 'delete'];

function PersonNodeComponent({
  card,
  dimmed,
  selected,
  branchName,
  branchHighlighted,
  isSelf,
  onOpen,
  onAction,
}: PersonNodeProps) {
  const { t } = useTranslation();
  const person = card.person;
  const deceased = person.lifeStatus === 'DECEASED';
  const lifespan = formatLifespan(person, t);

  return (
    <NodeGroup
      transform={`translate(${card.x}, ${card.y})`}
      $dimmed={dimmed}
      role="button"
      tabIndex={-1}
      aria-label={describe(person, branchName, isSelf, t)}
      onClick={() => {
        onOpen(person.id);
      }}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          onOpen(person.id);
        }
      }}
    >
      <title>{describe(person, branchName, isSelf, t)}</title>

      <NodeCard
        width={card.width}
        height={card.height}
        rx={10}
        $selected={selected}
        $readOnly={!person.canEdit}
      />
      <GenderRail
        x={1}
        y={10}
        width={3}
        height={card.height - 20}
        rx={1.5}
        $gender={person.gender}
      />

      <AvatarCircle cx={30} cy={26} r={14} $deceased={deceased} />
      {person.linkedUserId !== undefined && <LinkRing cx={30} cy={26} r={16.5} $self={isSelf} />}
      <AvatarInitials x={30} y={26}>
        {initialsOf(person.fullName)}
      </AvatarInitials>

      <NodeName x={52} y={24} $deceased={deceased}>
        {truncate(person.fullName, 17)}
      </NodeName>
      <NodeMeta x={52} y={41}>
        {lifespan ?? formatGeneration(person.generation, t)}
      </NodeMeta>

      {branchName !== undefined && (
        <>
          <BranchPill
            x={52}
            y={48}
            width={Math.min(112, branchName.length * 5.6 + 14)}
            height={16}
            rx={8}
            $highlighted={branchHighlighted}
          />
          <BranchPillText x={59} y={59} $highlighted={branchHighlighted}>
            {truncate(branchName, 16)}
          </BranchPillText>
        </>
      )}

      {person.canEdit && (
        <ActionLayer transform={`translate(${card.width - 10}, -10)`}>
          {ACTIONS.map((action, index) => (
            <ActionButton
              key={action}
              transform={`translate(${(index - ACTIONS.length + 1) * 24}, 0)`}
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

/** SVG has no ellipsis, so the string is cut. Cheap, and stable across every browser. */
function truncate(value: string, maxLength: number): string {
  return value.length <= maxLength ? value : `${value.slice(0, maxLength - 1)}…`;
}
