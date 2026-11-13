import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import styled, { createGlobalStyle } from 'styled-components';

import { type TreeGraph } from '../../model/buildGraph';
import { formatGeneration } from '../../model/format';
import { intersects, type TreeBounds, type TreeLayout } from '../../model/layout';
import { type PrintPlan } from '../../model/print';

import { LegendEntries } from './Legend';
import { RailLabel, schemeVars } from './styles';
import { TreeScene } from './TreeScene';

/**
 * The tree on paper (#61).
 *
 * Rendered into a portal on `<body>` only while a print is being made. On screen it is not shown
 * at all; in print it is the *only* thing shown — every other child of `<body>` is hidden — so the
 * app's chrome, the toolbar and the drawer never reach the paper.
 *
 * Each page is its own `<svg>` with a `viewBox` onto one tile of canvas space and a physical size in
 * millimetres, so the browser prints vectors: text stays text (selectable in a saved PDF), lines
 * stay lines, and nothing is rasterised. Only the slots intersecting a tile are drawn on it. The
 * sheet always uses the light scheme, whatever the canvas shows — dark paper is nobody's intent.
 */

/** Pixels per millimetre at the CSS reference resolution — what non-scaling strokes measure in. */
const PX_PER_MM = 96 / 25.4;

const PrintStyles = createGlobalStyle<{ $width: number; $height: number }>`
  @media screen {
    [data-tree-print] {
      display: none;
    }
  }

  @media print {
    @page {
      size: ${({ $width, $height }) => `${String($width)}mm ${String($height)}mm`};
      margin: 0;
    }

    html,
    body {
      height: auto !important;
      overflow: visible !important;
      background: ${({ theme }) => theme.color.surface} !important;
    }

    body > *:not([data-tree-print]) {
      display: none !important;
    }
  }
`;

const Sheet = styled.div`
  ${schemeVars('light')}
  color: var(--tree-ink);
  font-family: ${({ theme }) => theme.font.body};
`;

const Page = styled.section<{ $width: number; $height: number }>`
  box-sizing: border-box;
  width: ${({ $width }) => `${String($width)}mm`};
  height: ${({ $height }) => `${String($height)}mm`};
  padding: 10mm;
  overflow: hidden;
  break-after: page;
  page-break-after: always;

  &:last-child {
    break-after: auto;
    page-break-after: auto;
  }

  svg {
    display: block;
    overflow: hidden;
  }
`;

const PageHeader = styled.header`
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 4mm;
  height: 12mm;
  font-size: 9pt;
  color: var(--tree-ink-muted);

  strong {
    font-family: ${({ theme }) => theme.font.serif};
    font-size: 12pt;
    color: var(--tree-ink);
  }
`;

/** The legend on the first page, floated over the tree's corner where paper is usually empty. */
const PrintLegend = styled.div`
  position: relative;

  > div {
    position: absolute;
    left: 0;
    bottom: 0;
    padding-top: 2mm;
    border: 0.3mm solid var(--tree-card-border);
    border-radius: 2mm;
    background: var(--tree-card);
    font-size: 8pt;
  }
`;

interface PrintSheetProps {
  layout: TreeLayout;
  graph: TreeGraph;
  plan: PrintPlan;
  title: string;
  myPersonId?: string;
  /** Called once the browser's print dialog has closed, printed or not. */
  onDone: () => void;
}

export function PrintSheet({ layout, graph, plan, title, myPersonId, onDone }: PrintSheetProps) {
  const { t } = useTranslation();

  useEffect(() => {
    const afterPrint = () => {
      onDone();
    };
    window.addEventListener('afterprint', afterPrint);
    // Two frames: one for React to commit the portal, one for the browser to lay it out — printing
    // inside the same frame prints a page that has not been styled yet.
    let frame = window.requestAnimationFrame(() => {
      frame = window.requestAnimationFrame(() => {
        window.print();
      });
    });
    return () => {
      window.cancelAnimationFrame(frame);
      window.removeEventListener('afterprint', afterPrint);
    };
  }, [onDone]);

  const pages = plan.tiles.length;
  const printedOn = new Date().toLocaleDateString();

  return createPortal(
    <div data-tree-print="">
      <PrintStyles $width={plan.pageWidth} $height={plan.pageHeight} />
      <Sheet>
        {plan.tiles.map((tile, index) => (
          <Page
            key={`${String(tile.minX)}:${String(tile.minY)}`}
            $width={plan.pageWidth}
            $height={plan.pageHeight}
          >
            <PageHeader>
              <strong>{title}</strong>
              <span>
                {pages > 1
                  ? t('tree.print.pageOf', {
                      page: index + 1,
                      pages,
                      row: Math.floor(index / plan.columns) + 1,
                      column: (index % plan.columns) + 1,
                    })
                  : printedOn}
              </span>
            </PageHeader>
            <TreeTile
              layout={layout}
              graph={graph}
              tile={tile}
              plan={plan}
              {...(myPersonId === undefined ? {} : { myPersonId })}
            />
            {index === 0 && (
              <PrintLegend>
                <div>
                  <LegendEntries showSelf={myPersonId !== undefined} />
                </div>
              </PrintLegend>
            )}
          </Page>
        ))}
      </Sheet>
    </div>,
    document.body,
  );
}

/** One tile of the tree, as a vector picture of the given physical size. */
export function TreeTile({
  layout,
  graph,
  tile,
  plan,
  myPersonId,
}: {
  layout: TreeLayout;
  graph: TreeGraph;
  tile: TreeBounds;
  plan: Pick<PrintPlan, 'areaWidth' | 'areaHeight' | 'scale'>;
  myPersonId?: string;
}) {
  const { t } = useTranslation();
  const slots = layout.slots.filter((slot) =>
    intersects(
      { minX: slot.x, minY: slot.y, maxX: slot.x + slot.width, maxY: slot.y + slot.height },
      tile,
      0,
    ),
  );
  const width = tile.maxX - tile.minX;
  const height = tile.maxY - tile.minY;
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={`${String(plan.areaWidth)}mm`}
      height={`${String(plan.areaHeight)}mm`}
      viewBox={`${String(tile.minX)} ${String(tile.minY)} ${String(width)} ${String(height)}`}
      role="img"
      aria-label={t('tree.canvas.label')}
    >
      <TreeScene
        layout={layout}
        slots={slots}
        window={tile}
        margin={0}
        scale={plan.scale * PX_PER_MM}
        branchNames={graph.branchNames}
        highlightBranchId={null}
        {...(myPersonId === undefined ? {} : { myPersonId })}
      />
      {layout.rails.map((rail) =>
        layout.orientation === 'vertical' ? (
          <RailLabel key={rail.depth} x={tile.minX + 8} y={rail.position - 28}>
            {formatGeneration(rail.generation, t)}
          </RailLabel>
        ) : (
          <RailLabel key={rail.depth} x={rail.position - 18} y={tile.minY + 18}>
            {formatGeneration(rail.generation, t)}
          </RailLabel>
        ),
      )}
    </svg>
  );
}
