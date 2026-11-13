import { type Orientation, type TreeBounds } from './layout';

/**
 * Fitting a tree to paper (#61).
 *
 * **Pure maths.** The input is how big the tree is in each orientation; the output is which
 * orientation to print, which way round the paper goes, the scale, and the page tiles. The print
 * sheet turns that into SVG; nothing here touches the DOM, so the decisions are testable.
 *
 * ## Re-arranging, not shrinking
 *
 * A screenshot of the canvas scaled onto A4 is a grey smear. The planner instead tries every
 * combination of tree orientation (descent down the page, or across it) and paper orientation, and
 * keeps whichever lets the tree be drawn largest. A broad clan goes landscape with descent running
 * down; a deep, narrow line goes portrait, or turns sideways. The layout itself already stacks a
 * large family's childless children into columns (#60), which is what makes a real clan's
 * proportions printable at all.
 *
 * ## One page, or legible
 *
 * `fit` puts everything on one sheet whatever the result, and says so when names will come out too
 * small to read. `pages` keeps text at a legible size and tiles the tree across as many sheets as
 * that takes, with a strip of overlap so a connector cut at a page edge can be followed onto the
 * next one — the way a large *gia phả* has always been printed and taped together.
 */

export type PaperSize = 'A4' | 'A3' | 'LETTER';
export type PrintMode = 'fit' | 'pages';
export type PaperOrientation = 'portrait' | 'landscape';

/** Paper in millimetres, portrait. */
export const PAPER: Record<PaperSize, { width: number; height: number }> = {
  A4: { width: 210, height: 297 },
  A3: { width: 297, height: 420 },
  LETTER: { width: 215.9, height: 279.4 },
};

export const PRINT = {
  /** The printer's margin on every side. */
  margin: 10,
  /** Room at the top of each page for the title and the page number. */
  header: 12,
  /**
   * Millimetres per canvas unit at which a card is comfortably legible: a 176-unit card is 39 mm
   * wide and a 14-unit name is about 9 pt. `pages` mode prints at this scale.
   */
  readable: 0.22,
  /** Below this the 14-unit name drops under ~5 pt. `fit` still prints, but warns. */
  tiny: 0.12,
  /** Never print larger than this: a three-person tree should not fill an A3 sheet. */
  max: 0.3,
  /** Overlap between neighbouring tiles, in millimetres. */
  overlap: 12,
  /** Breathing room around the tree, in canvas units. */
  padding: 24,
} as const;

export interface PrintPlan {
  orientation: Orientation;
  paper: PaperOrientation;
  /** Page size in millimetres, as it will be printed. */
  pageWidth: number;
  pageHeight: number;
  /** The drawable area of one page, in millimetres. */
  areaWidth: number;
  areaHeight: number;
  /** Millimetres per canvas unit. */
  scale: number;
  columns: number;
  rows: number;
  /** One window into canvas space per page, row by row. */
  tiles: TreeBounds[];
  /** True when the text will print smaller than is comfortable to read. */
  tooSmall: boolean;
}

interface Candidate {
  orientation: Orientation;
  paper: PaperOrientation;
  bounds: TreeBounds;
}

/**
 * Plans the print of a tree whose bounds in each orientation are given.
 *
 * Either orientation may be absent (the caller may have laid out only one) — but at least one must
 * be there.
 */
export function planPrint(
  boundsByOrientation: Partial<Record<Orientation, TreeBounds>>,
  size: PaperSize,
  mode: PrintMode,
): PrintPlan {
  const candidates: Candidate[] = [];
  for (const orientation of ['vertical', 'horizontal'] as const) {
    const bounds = boundsByOrientation[orientation];
    if (bounds === undefined) continue;
    for (const paper of ['landscape', 'portrait'] as const) {
      candidates.push({ orientation, paper, bounds: padded(bounds) });
    }
  }
  if (candidates.length === 0) throw new Error('planPrint needs the bounds of at least one layout');

  const plans = candidates.map((candidate) => planOne(candidate, size, mode));
  // Fewest pages first; then the largest print; then the earlier candidate (vertical, landscape),
  // which is how a *gia phả* is usually drawn, so a tie goes to the familiar shape.
  return plans.reduce((best, plan) => {
    const pages = plan.columns * plan.rows;
    const bestPages = best.columns * best.rows;
    if (pages !== bestPages) return pages < bestPages ? plan : best;
    return plan.scale > best.scale + 1e-9 ? plan : best;
  });
}

function planOne(candidate: Candidate, size: PaperSize, mode: PrintMode): PrintPlan {
  const paper = PAPER[size];
  const pageWidth = candidate.paper === 'portrait' ? paper.width : paper.height;
  const pageHeight = candidate.paper === 'portrait' ? paper.height : paper.width;
  const areaWidth = pageWidth - PRINT.margin * 2;
  const areaHeight = pageHeight - PRINT.margin * 2 - PRINT.header;

  const { bounds } = candidate;
  const treeWidth = Math.max(1, bounds.maxX - bounds.minX);
  const treeHeight = Math.max(1, bounds.maxY - bounds.minY);
  const fitScale = Math.min(PRINT.max, areaWidth / treeWidth, areaHeight / treeHeight);

  const base = {
    orientation: candidate.orientation,
    paper: candidate.paper,
    pageWidth,
    pageHeight,
    areaWidth,
    areaHeight,
  };

  if (mode === 'fit' || fitScale >= PRINT.readable) {
    const window = centredWindow(bounds, areaWidth / fitScale, areaHeight / fitScale);
    return {
      ...base,
      scale: fitScale,
      columns: 1,
      rows: 1,
      tiles: [window],
      tooSmall: fitScale < PRINT.tiny,
    };
  }

  const scale = PRINT.readable;
  const tileWidth = areaWidth / scale;
  const tileHeight = areaHeight / scale;
  const stepX = (areaWidth - PRINT.overlap) / scale;
  const stepY = (areaHeight - PRINT.overlap) / scale;
  const columns = Math.max(1, Math.ceil((treeWidth - tileWidth) / stepX) + 1);
  const rows = Math.max(1, Math.ceil((treeHeight - tileHeight) / stepY) + 1);

  // The grid covers a little more than the tree; centre the tree in it so the slack is shared
  // between the outer edges instead of piling up on the last row and column.
  const gridWidth = tileWidth + (columns - 1) * stepX;
  const gridHeight = tileHeight + (rows - 1) * stepY;
  const originX = bounds.minX - (gridWidth - treeWidth) / 2;
  const originY = bounds.minY - (gridHeight - treeHeight) / 2;

  const tiles: TreeBounds[] = [];
  for (let row = 0; row < rows; row += 1) {
    for (let column = 0; column < columns; column += 1) {
      const minX = originX + column * stepX;
      const minY = originY + row * stepY;
      tiles.push({ minX, minY, maxX: minX + tileWidth, maxY: minY + tileHeight });
    }
  }
  return { ...base, scale, columns, rows, tiles, tooSmall: false };
}

function padded(bounds: TreeBounds): TreeBounds {
  return {
    minX: bounds.minX - PRINT.padding,
    minY: bounds.minY - PRINT.padding,
    maxX: bounds.maxX + PRINT.padding,
    maxY: bounds.maxY + PRINT.padding,
  };
}

/** A window of the given size centred on the tree. */
function centredWindow(bounds: TreeBounds, width: number, height: number): TreeBounds {
  const cx = (bounds.minX + bounds.maxX) / 2;
  const cy = (bounds.minY + bounds.maxY) / 2;
  return {
    minX: cx - width / 2,
    minY: cy - height / 2,
    maxX: cx + width / 2,
    maxY: cy + height / 2,
  };
}
