import { type CardTone, STROKE } from '../../model/visual';

import {
  AvatarCircle,
  CardSwatch,
  LegendSwatch,
  LinkRing,
  ParentEdgePath,
  UnionLine,
} from './styles';

/**
 * Small, self-contained pictures of each visual category (#61).
 *
 * Drawn with the very styled parts the canvas uses, so the legend, the list view and the print
 * sheet show what the canvas shows rather than an approximation of it. Each is decorative: the
 * text beside it carries the meaning, so the SVG is hidden from assistive technology.
 */

/** A miniature card in a tone's colours — a gender's tint, or the gray of the deceased. */
export function CardGlyph({
  tone,
  width = 20,
  height = 14,
}: {
  tone: CardTone;
  width?: number;
  height?: number;
}) {
  return (
    <LegendSwatch width={width} height={height} aria-hidden="true" focusable="false">
      <CardSwatch x={0.5} y={0.5} width={width - 1} height={height - 1} rx={3} $tone={tone} />
      <AvatarCircle cx={height / 2} cy={height / 2} r={height / 2 - 3.5} $tone={tone} />
    </LegendSwatch>
  );
}

/** The ring an avatar with an account wears — accent for the viewer, green for anybody else. */
export function RingGlyph({ self }: { self: boolean }) {
  return (
    <LegendSwatch width={18} height={18} aria-hidden="true" focusable="false">
      <AvatarCircle cx={9} cy={9} r={6} $tone="unknown" />
      <LinkRing cx={9} cy={9} r={8} $self={self} />
    </LegendSwatch>
  );
}

/** A connector into a child, solid for biological descent and dashed otherwise. */
export function DescentGlyph({ dashed }: { dashed: boolean }) {
  return (
    <LegendSwatch
      width={30}
      height={14}
      aria-hidden="true"
      focusable="false"
      strokeWidth={STROKE.edge.base}
    >
      <ParentEdgePath d="M 2 2 V 7 H 28 V 12" $dashed={dashed} $dimmed={false} />
    </LegendSwatch>
  );
}

/** The bar between two partners, heavy for the primary marriage and light and dashed otherwise. */
export function UnionGlyph({ secondary }: { secondary: boolean }) {
  return (
    <LegendSwatch
      width={30}
      height={14}
      aria-hidden="true"
      focusable="false"
      strokeWidth={secondary ? STROKE.unionSecondary.base : STROKE.union.base}
    >
      <UnionLine x1={2} y1={7} x2={28} y2={7} $secondary={secondary} $dimmed={false} />
    </LegendSwatch>
  );
}
