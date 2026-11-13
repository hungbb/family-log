import { DownloadOutlined, PrinterOutlined } from '@ant-design/icons';
import { Alert, App as AntApp, Button, Form, Modal, Radio, Space } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import styled from 'styled-components';

import { tokens } from '@/theme/tokens.ts';

import { type TreeGraph } from '../../model/buildGraph';
import { layoutTree, type Orientation, type TreeLayout } from '../../model/layout';
import {
  planPrint,
  PRINT,
  type PaperSize,
  type PrintMode,
  type PrintPlan,
} from '../../model/print';

import { fileNameOf, rasterize, saveBlob, serializeSvg } from './exportImage';
import { PrintSheet, TreeTile } from './PrintSheet';
import { schemeVars } from './styles';

/**
 * Print and export (#61).
 *
 * **Re-arranged for paper, not captured from the screen.** The dialog lays the tree out afresh in
 * both orientations and lets `planPrint` choose the one, and the paper orientation, that prints it
 * largest — then either squeezes it onto one sheet or tiles it across as many as legible text
 * needs. What is printed is vector SVG, so "Save as PDF" in the browser's print dialog is the PDF
 * export: text stays selectable and lines stay sharp at any size. SVG and PNG downloads use the same
 * drawing.
 *
 * **The whole tree by default.** Somebody who collapsed three branches to find their way around
 * rarely means to leave them off the printout; "as shown" is there for when they do.
 */

type Scope = 'all' | 'shown';
type Export = 'svg' | 'png';

/** Off screen but laid out, so the export can read computed styles from it. */
const ExportStage = styled.div`
  ${schemeVars('light')}
  position: fixed;
  top: 0;
  left: -100000px;
  pointer-events: none;
`;

/** Millimetres per canvas unit for exports: one canvas unit becomes one CSS pixel. */
const EXPORT_SCALE = 25.4 / 96;

/** Device pixels per CSS pixel in a PNG export — sharp on a high-density screen and in print. */
const PNG_DENSITY = 2;

interface PrintDialogProps {
  open: boolean;
  onClose: () => void;
  graph: TreeGraph;
  /** The canvas's current collapsed set, for "as shown". */
  collapsed: ReadonlySet<string>;
  title: string;
  myPersonId?: string;
}

export function PrintDialog({
  open,
  onClose,
  graph,
  collapsed,
  title,
  myPersonId,
}: PrintDialogProps) {
  const { t } = useTranslation();
  const { message } = AntApp.useApp();
  const [scope, setScope] = useState<Scope>('all');
  const [paper, setPaper] = useState<PaperSize>('A4');
  const [mode, setMode] = useState<PrintMode>('pages');
  const [printing, setPrinting] = useState(false);
  const [exporting, setExporting] = useState<Export | null>(null);
  const stageRef = useRef<HTMLDivElement>(null);

  // Both orientations, so the planner can choose; only while the dialog is open.
  const layouts = useMemo<Record<Orientation, TreeLayout> | null>(() => {
    if (!open && !printing) return null;
    const folded = scope === 'all' ? new Set<string>() : collapsed;
    return {
      vertical: layoutTree(graph.roots, { orientation: 'vertical', collapsed: folded }),
      horizontal: layoutTree(graph.roots, { orientation: 'horizontal', collapsed: folded }),
    };
  }, [open, printing, scope, collapsed, graph]);

  const plan = useMemo<PrintPlan | null>(
    () =>
      layouts === null
        ? null
        : planPrint(
            { vertical: layouts.vertical.bounds, horizontal: layouts.horizontal.bounds },
            paper,
            mode,
          ),
    [layouts, paper, mode],
  );

  const chosen = layouts === null || plan === null ? null : layouts[plan.orientation];

  const donePrinting = useCallback(() => {
    setPrinting(false);
  }, []);

  // The export reads from the off-screen drawing, so it runs once that drawing is in the document.
  useEffect(() => {
    if (exporting === null || chosen === null) return;
    const svg = stageRef.current?.querySelector('svg');
    if (svg === null || svg === undefined) return;
    const width = chosen.bounds.maxX - chosen.bounds.minX + PRINT.padding * 2;
    const height = chosen.bounds.maxY - chosen.bounds.minY + PRINT.padding * 2;
    const markup = serializeSvg(svg);
    const run =
      exporting === 'svg'
        ? Promise.resolve(new Blob([markup], { type: 'image/svg+xml' }))
        : rasterize(markup, width, height, PNG_DENSITY, tokens.tree.light.surface);
    let cancelled = false;
    run
      .then((blob) => {
        if (!cancelled) saveBlob(blob, fileNameOf(title, exporting));
      })
      .catch(() => {
        if (!cancelled) void message.error(t('tree.print.exportFailed'));
      })
      .finally(() => {
        if (!cancelled) setExporting(null);
      });
    return () => {
      cancelled = true;
    };
  }, [exporting, chosen, title, message, t]);

  const pages = plan?.tiles.length ?? 0;

  return (
    <>
      <Modal
        open={open}
        title={t('tree.print.title')}
        onCancel={onClose}
        footer={
          <Space wrap>
            <Button
              icon={<DownloadOutlined />}
              loading={exporting === 'svg'}
              disabled={plan === null}
              onClick={() => {
                setExporting('svg');
              }}
            >
              {t('tree.print.exportSvg')}
            </Button>
            <Button
              icon={<DownloadOutlined />}
              loading={exporting === 'png'}
              disabled={plan === null}
              onClick={() => {
                setExporting('png');
              }}
            >
              {t('tree.print.exportPng')}
            </Button>
            <Button
              type="primary"
              icon={<PrinterOutlined />}
              disabled={plan === null}
              onClick={() => {
                setPrinting(true);
              }}
            >
              {t('tree.print.print')}
            </Button>
          </Space>
        }
      >
        <Form layout="vertical">
          <Form.Item label={t('tree.print.scope')}>
            <Radio.Group
              value={scope}
              onChange={(event) => {
                setScope(event.target.value as Scope);
              }}
              options={[
                { value: 'all', label: t('tree.print.scopeAll') },
                { value: 'shown', label: t('tree.print.scopeShown') },
              ]}
            />
          </Form.Item>
          <Form.Item label={t('tree.print.paper')}>
            <Radio.Group
              optionType="button"
              value={paper}
              onChange={(event) => {
                setPaper(event.target.value as PaperSize);
              }}
              options={[
                { value: 'A4', label: 'A4' },
                { value: 'A3', label: 'A3' },
                { value: 'LETTER', label: t('tree.print.letter') },
              ]}
            />
          </Form.Item>
          <Form.Item label={t('tree.print.mode')} extra={t(`tree.print.modeHelp.${mode}`)}>
            <Radio.Group
              value={mode}
              onChange={(event) => {
                setMode(event.target.value as PrintMode);
              }}
              options={[
                { value: 'pages', label: t('tree.print.modePages') },
                { value: 'fit', label: t('tree.print.modeFit') },
              ]}
            />
          </Form.Item>
        </Form>

        {plan !== null && (
          <Alert
            type={plan.tooSmall ? 'warning' : 'info'}
            showIcon
            message={t('tree.print.summary', {
              count: pages,
              paper: paper === 'LETTER' ? t('tree.print.letter') : paper,
              paperOrientation: t(`tree.print.paperOrientation.${plan.paper}`),
              orientation: t(`tree.print.treeOrientation.${plan.orientation}`),
              columns: plan.columns,
              rows: plan.rows,
            })}
            description={plan.tooSmall ? t('tree.print.tooSmall') : t('tree.print.pdfHint')}
          />
        )}
      </Modal>

      {printing && plan !== null && chosen !== null && (
        <PrintSheet
          layout={chosen}
          graph={graph}
          plan={plan}
          title={title}
          {...(myPersonId === undefined ? {} : { myPersonId })}
          onDone={donePrinting}
        />
      )}

      {exporting !== null && chosen !== null && (
        <ExportStage ref={stageRef} aria-hidden="true">
          <TreeTile
            layout={chosen}
            graph={graph}
            tile={{
              minX: chosen.bounds.minX - PRINT.padding,
              minY: chosen.bounds.minY - PRINT.padding,
              maxX: chosen.bounds.maxX + PRINT.padding,
              maxY: chosen.bounds.maxY + PRINT.padding,
            }}
            plan={{
              scale: EXPORT_SCALE,
              areaWidth:
                (chosen.bounds.maxX - chosen.bounds.minX + PRINT.padding * 2) * EXPORT_SCALE,
              areaHeight:
                (chosen.bounds.maxY - chosen.bounds.minY + PRINT.padding * 2) * EXPORT_SCALE,
            }}
            {...(myPersonId === undefined ? {} : { myPersonId })}
          />
        </ExportStage>
      )}
    </>
  );
}
