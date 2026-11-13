import { memo } from 'react';
import { useTranslation } from 'react-i18next';

import { type TreeBounds, type TreeLayout } from '../../model/layout';

import { MiniMapFrame, MiniMapNode, MiniMapSelf, MiniMapSurface, MiniMapViewport } from './styles';

/**
 * The whole tree at a glance, with the viewport drawn on it (F5.10).
 *
 * **Only earns its place on a large tree.** On twenty people the canvas already shows everything and
 * a minimap is decoration; on five thousand it is the only way to know where you are. The page
 * decides, using {@link MINIMAP_THRESHOLD}.
 *
 * One rectangle per slot rather than per person: at this size two cards side by side are the same
 * pixel, and drawing five thousand rects to render two thousand pixels is work nobody sees.
 */

/** Below this many slots, the canvas is its own overview. */
export const MINIMAP_THRESHOLD = 40;

/**
 * The map's box. Its shape follows the tree's: a clan that is twenty times wider than it is deep
 * would otherwise be a thin line across a mostly empty box, which is exactly the clan that needs
 * the map most.
 */
const SIZE = { maxWidth: 260, maxHeight: 160, minSide: 56, padding: 6 } as const;

interface MiniMapProps {
  layout: TreeLayout;
  /** The part of canvas space currently on screen. */
  viewport: TreeBounds;
  /** Branch to pick out, matching the canvas's highlighting. */
  highlightBranchId: string | null;
  /** The slot holding the viewer's own node, marked as it is on the canvas (#61). */
  selfSlotKey?: string;
  /** Called with a point in canvas space when somebody clicks the map. */
  onJumpTo: (point: { x: number; y: number }) => void;
}

function MiniMapComponent({
  layout,
  viewport,
  highlightBranchId,
  selfSlotKey,
  onJumpTo,
}: MiniMapProps) {
  const { t } = useTranslation();
  const { bounds } = layout;

  const treeWidth = Math.max(1, bounds.maxX - bounds.minX);
  const treeHeight = Math.max(1, bounds.maxY - bounds.minY);
  const scale = Math.min(
    (SIZE.maxWidth - SIZE.padding * 2) / treeWidth,
    (SIZE.maxHeight - SIZE.padding * 2) / treeHeight,
  );
  const width = Math.max(SIZE.minSide, treeWidth * scale + SIZE.padding * 2);
  const height = Math.max(SIZE.minSide, treeHeight * scale + SIZE.padding * 2);

  // Click to jump, or hold and drag to steer the viewport across the map.
  const jumpFrom = (event: React.PointerEvent<SVGSVGElement>) => {
    const box = event.currentTarget.getBoundingClientRect();
    onJumpTo({
      x: bounds.minX + (event.clientX - box.left - SIZE.padding) / scale,
      y: bounds.minY + (event.clientY - box.top - SIZE.padding) / scale,
    });
  };

  const toMap = (x: number, y: number) => ({
    x: SIZE.padding + (x - bounds.minX) * scale,
    y: SIZE.padding + (y - bounds.minY) * scale,
  });

  const view = toMap(viewport.minX, viewport.minY);
  const selfSlot =
    selfSlotKey === undefined ? undefined : layout.slots.find((slot) => slot.key === selfSlotKey);
  const selfAt =
    selfSlot === undefined
      ? undefined
      : toMap(selfSlot.x + selfSlot.width / 2, selfSlot.y + selfSlot.height / 2);

  return (
    <MiniMapFrame>
      <MiniMapSurface
        width={width}
        height={height}
        role="img"
        aria-label={t('tree.canvas.minimap')}
        onPointerDown={(event) => {
          if (event.button !== 0) return;
          event.currentTarget.setPointerCapture(event.pointerId);
          jumpFrom(event);
        }}
        onPointerMove={(event) => {
          if (event.currentTarget.hasPointerCapture(event.pointerId)) jumpFrom(event);
        }}
        onPointerUp={(event) => {
          if (event.currentTarget.hasPointerCapture(event.pointerId)) {
            event.currentTarget.releasePointerCapture(event.pointerId);
          }
        }}
      >
        {layout.slots.map((slot) => {
          const at = toMap(slot.x, slot.y);
          return (
            <MiniMapNode
              key={slot.key}
              x={at.x}
              y={at.y}
              width={Math.max(1.5, slot.width * scale)}
              height={Math.max(1.5, slot.height * scale)}
              $highlighted={
                highlightBranchId !== null && slot.node.anchor.branchId === highlightBranchId
              }
            />
          );
        })}

        {selfAt !== undefined && <MiniMapSelf cx={selfAt.x} cy={selfAt.y} r={4} />}

        <MiniMapViewport
          x={view.x}
          y={view.y}
          width={Math.max(4, (viewport.maxX - viewport.minX) * scale)}
          height={Math.max(4, (viewport.maxY - viewport.minY) * scale)}
        />
      </MiniMapSurface>
    </MiniMapFrame>
  );
}

export const MiniMap = memo(MiniMapComponent);
