import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';

import { type PersonSummary } from '@/api/endpoints/persons.ts';

import {
  boxOf,
  intersects,
  type LaidOutSlot,
  type LaidOutUnion,
  type TreeBounds,
  type TreeLayout,
} from '../../model/layout';
import {
  enteringFrame,
  exitingFrame,
  type LayoutTransition,
  type SlotFrame,
} from '../../model/motion';
import { isDashedDescent, isSecondaryUnion, screenStroke, STROKE } from '../../model/visual';

import { PersonNode, type NodeAction } from './PersonNode';
import {
  CollapsedCount,
  CollapseToggle,
  ConnectorLayer,
  GhostGroup,
  ParentEdgePath,
  RailLine,
  StackPanel,
  UnionLine,
} from './styles';

/**
 * The tree's drawing, shared by the canvas and the print sheet (#61).
 *
 * Everything that is *picture* lives here — rails, stack panels, connectors, marriages, cards — and
 * everything that is *interaction* is passed in. The canvas hands it the viewport's slots, the
 * zoom, a layout transition and the click handlers; the print sheet hands it one page's slots and
 * no handlers at all. That is what keeps the printed tree the same tree as the one on screen.
 *
 * Drawn in canvas units: the caller owns the transform that puts it on screen or on paper.
 */

export interface SceneInteraction {
  focusedSlotKey: string | null;
  collapsed: ReadonlySet<string>;
  onOpen: (personId: string) => void;
  onAction: (action: NodeAction, person: PersonSummary) => void;
  onToggle: (slotKey: string) => void;
}

interface TreeSceneProps {
  layout: TreeLayout;
  /** The slots to draw — the viewport's, or one printed page's. */
  slots: LaidOutSlot[];
  /** The window being drawn, with a margin, for culling the panels behind stacks. */
  window: TreeBounds;
  margin: number;
  /**
   * Screen pixels per canvas unit. Connectors are non-scaling (see `styles.ts`), so their weight is
   * chosen from this rather than inherited from the transform.
   */
  scale: number;
  branchNames: Map<string, string>;
  highlightBranchId: string | null;
  myPersonId?: string;
  /** The layout change in flight and the eased progress through it, if one is animating. */
  transition?: LayoutTransition | null;
  progress?: number;
  /** Changes when connectors should fade back in after a transition. */
  connectorKey?: number;
  /** Absent on paper: no handles, no actions, no focus. */
  interaction?: SceneInteraction;
}

export function TreeScene({
  layout,
  slots,
  window,
  margin,
  scale,
  branchNames,
  highlightBranchId,
  myPersonId,
  transition = null,
  progress = 1,
  connectorKey = 0,
  interaction,
}: TreeSceneProps) {
  const { t } = useTranslation();
  const animating = transition !== null && progress < 1;

  const drawnKeys = useMemo(() => new Set(slots.map((slot) => slot.key)), [slots]);
  const branchOfSlot = useMemo(
    () => new Map(layout.slots.map((slot) => [slot.key, slot.node.anchor.branchId])),
    [layout.slots],
  );
  const unionsBySlot = useMemo(() => {
    const bySlot = new Map<string, LaidOutUnion[]>();
    for (const union of layout.unions) {
      const list = bySlot.get(union.slotKey) ?? [];
      list.push(union);
      bySlot.set(union.slotKey, list);
    }
    return bySlot;
  }, [layout.unions]);

  const inBranch = (slotKey: string) =>
    highlightBranchId === null || branchOfSlot.get(slotKey) === highlightBranchId;

  const edgeWidth = screenStroke(STROKE.edge, scale);
  const unionWidth = screenStroke(STROKE.union, scale);
  const secondaryWidth = screenStroke(STROKE.unionSecondary, scale);

  const drawSlot = (slot: LaidOutSlot, frame: SlotFrame, ghost: boolean) => {
    const moved = frame.dx !== 0 || frame.dy !== 0;
    const dimmed = !inBranch(slot.key);
    const isCollapsed = interaction?.collapsed.has(slot.key) ?? slot.collapsed;
    const content = (
      <>
        {(unionsBySlot.get(slot.key) ?? []).map((union) => {
          const secondary = isSecondaryUnion(union.unionType);
          return (
            <UnionLine
              key={union.key}
              x1={union.x1}
              y1={union.y1}
              x2={union.x2}
              y2={union.y2}
              strokeWidth={secondary ? secondaryWidth : unionWidth}
              $secondary={secondary}
              $dimmed={dimmed}
            />
          );
        })}

        {slot.cards.map((card) => (
          <PersonNode
            key={card.personId}
            card={card}
            dimmed={dimmed}
            selected={!ghost && interaction?.focusedSlotKey === slot.key}
            {...branchNameProp(branchNames, card.person.branchId)}
            branchHighlighted={
              highlightBranchId !== null && card.person.branchId === highlightBranchId
            }
            isSelf={card.personId === myPersonId}
            interactive={!ghost && interaction !== undefined}
            {...(ghost || interaction === undefined
              ? {}
              : { onOpen: interaction.onOpen, onAction: interaction.onAction })}
          />
        ))}

        {slot.node.children.length > 0 &&
          (interaction !== undefined && !ghost ? (
            <CollapseToggle
              transform={toggleAt(slot, layout.orientation)}
              role="button"
              tabIndex={-1}
              aria-expanded={!isCollapsed}
              aria-label={t(isCollapsed ? 'tree.canvas.expand' : 'tree.canvas.collapse', {
                name: slot.node.anchor.fullName,
              })}
              onClick={(event) => {
                event.stopPropagation();
                interaction.onToggle(slot.key);
              }}
              onKeyDown={(event) => {
                if (event.key !== 'Enter' && event.key !== ' ') return;
                event.preventDefault();
                interaction.onToggle(slot.key);
              }}
            >
              <title>
                {t(isCollapsed ? 'tree.canvas.expand' : 'tree.canvas.collapse', {
                  name: slot.node.anchor.fullName,
                })}
              </title>
              <circle r={9} />
              <path d={isCollapsed ? 'M -4 0 H 4 M 0 -4 V 4' : 'M -4 0 H 4'} />
              {isCollapsed && <CollapsedCount y={22}>{slot.node.children.length}</CollapsedCount>}
            </CollapseToggle>
          ) : (
            // On paper a collapsed branch still says how much is folded away under it.
            isCollapsed && (
              <CollapsedCount transform={toggleAt(slot, layout.orientation)} y={4}>
                {`+${String(slot.node.children.length)}`}
              </CollapsedCount>
            )
          ))}
      </>
    );

    const transform = moved ? `translate(${frame.dx}, ${frame.dy})` : undefined;
    const opacity = frame.opacity < 1 ? frame.opacity : undefined;
    return ghost ? (
      <GhostGroup
        key={`ghost:${slot.key}`}
        transform={transform}
        opacity={opacity}
        aria-hidden="true"
      >
        {content}
      </GhostGroup>
    ) : (
      <g key={slot.key} transform={transform} opacity={opacity}>
        {content}
      </g>
    );
  };

  return (
    <>
      {layout.rails.map((rail) =>
        layout.orientation === 'vertical' ? (
          <RailLine
            key={rail.depth}
            x1={layout.bounds.minX - 24}
            y1={rail.position - 22}
            x2={layout.bounds.maxX + 24}
            y2={rail.position - 22}
          />
        ) : (
          <RailLine
            key={rail.depth}
            x1={rail.position - 22}
            y1={layout.bounds.minY - 24}
            x2={rail.position - 22}
            y2={layout.bounds.maxY + 24}
          />
        ),
      )}

      {layout.stacks.map((stack) =>
        intersects(
          {
            minX: stack.x,
            minY: stack.y,
            maxX: stack.x + stack.width,
            maxY: stack.y + stack.height,
          },
          window,
          margin,
        ) ? (
          <StackPanel
            key={stack.key}
            x={stack.x}
            y={stack.y}
            width={stack.width}
            height={stack.height}
            rx={12}
          />
        ) : null,
      )}

      {/*
        Connectors are hidden while the layout moves and fade back in where they now belong. An
        elbow cannot be tweened as a shape without a path-morphing library, and one that lags behind
        the cards it joins reads as a glitch; one that arrives a beat after them reads as settling.
      */}
      {!animating && (
        <ConnectorLayer key={connectorKey} $fadeIn={connectorKey > 0} strokeWidth={edgeWidth}>
          {layout.edges.map((edge) =>
            drawnKeys.has(edge.childSlotKey) || drawnKeys.has(edge.parentSlotKey) ? (
              <ParentEdgePath
                key={edge.key}
                d={edge.path}
                $dashed={isDashedDescent(edge.nature)}
                $dimmed={!inBranch(edge.childSlotKey)}
              />
            ) : null,
          )}
        </ConnectorLayer>
      )}

      {transition !== null &&
        animating &&
        transition.exiting
          .filter((slot) => intersects(boxOf(slot), window, margin))
          .map((slot) => drawSlot(slot, exitingFrame(slot, transition, progress), true))}

      {slots.map((slot) =>
        drawSlot(slot, enteringFrame(slot, animating ? transition : null, progress), false),
      )}
    </>
  );
}

/**
 * `branchName` only when there is one.
 *
 * `exactOptionalPropertyTypes` is on, so an optional prop is either absent or a string — passing an
 * explicit `undefined` is a different thing and does not compile. Worth the ceremony: "unplaced" and
 * "placed in a branch we failed to look up" are different states, and the card renders them
 * differently.
 */
function branchNameProp(
  names: Map<string, string>,
  branchId: string | undefined,
): { branchName?: string } {
  if (branchId === undefined) return {};
  const name = names.get(branchId);
  return name === undefined ? {} : { branchName: name };
}

/** Where the collapse handle sits: under the slot when vertical, to its right when horizontal. */
function toggleAt(slot: LaidOutSlot, orientation: 'vertical' | 'horizontal'): string {
  const anchor = slot.cards[0];
  if (anchor === undefined) return `translate(${slot.x}, ${slot.y})`;
  return orientation === 'vertical'
    ? `translate(${anchor.x + anchor.width / 2}, ${slot.y + slot.height + 12})`
    : `translate(${slot.x + slot.width + 12}, ${anchor.y + anchor.height / 2})`;
}
