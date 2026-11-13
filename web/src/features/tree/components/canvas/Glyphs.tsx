import { type Gender } from '@/api/endpoints/persons.ts';

import { GENDER_STYLE, genderShapePath, STROKE } from '../../model/visual';

import {
  AvatarShape,
  LegendSwatch,
  LinkRing,
  MourningBand,
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

/** A gender's avatar shape, filled for the living and muted for the deceased. */
export function GenderGlyph({
  gender,
  deceased = false,
  size = 14,
}: {
  gender: Gender;
  deceased?: boolean;
  size?: number;
}) {
  const style = GENDER_STYLE[gender];
  const r = size / 2 - 1.5;
  return (
    <LegendSwatch width={size} height={size} aria-hidden="true" focusable="false">
      <AvatarShape
        d={genderShapePath(style.shape, size / 2, size / 2, r)}
        $tone={style.tone}
        $deceased={deceased}
        $open={style.shape === 'open'}
      />
    </LegendSwatch>
  );
}

/** The corner band a deceased person's card carries. */
export function MourningGlyph({ size = 14 }: { size?: number }) {
  return (
    <LegendSwatch width={size} height={size} aria-hidden="true" focusable="false">
      <AvatarShape
        d={genderShapePath('circle', size / 2, size / 2, size / 2 - 1.5)}
        $tone="unknown"
        $deceased
        $open={false}
      />
      <MourningBand
        d={`M 0 ${String(size * 0.42)} L ${String(size * 0.42)} 0 L ${String(size * 0.72)} 0 L 0 ${String(size * 0.72)} Z`}
      />
    </LegendSwatch>
  );
}

/** The ring an avatar with an account wears — accent for the viewer, green for anybody else. */
export function RingGlyph({ self }: { self: boolean }) {
  return (
    <LegendSwatch width={18} height={18} aria-hidden="true" focusable="false">
      <AvatarShape
        d={genderShapePath('circle', 9, 9, 6)}
        $tone="unknown"
        $deceased={false}
        $open={false}
      />
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
