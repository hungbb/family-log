import {
  AimOutlined,
  ColumnWidthOutlined,
  CompressOutlined,
  ExpandOutlined,
  FullscreenExitOutlined,
  FullscreenOutlined,
  MinusOutlined,
  MoonOutlined,
  PlusOutlined,
  PrinterOutlined,
  SunOutlined,
} from '@ant-design/icons';
import { Button, Select, Space, Tooltip } from 'antd';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';

import { type PersonSummary } from '@/api/endpoints/persons.ts';
import { type TreeBranch } from '@/api/endpoints/tree.ts';

import { fold } from '../../model/fold';
import { formatGeneration } from '../../model/format';
import { ZOOM, useTreeViewStore } from '../../stores/useTreeViewStore';

import { ToolbarBar, ToolbarSpacer } from './styles';

/**
 * The canvas's controls (F5.9).
 *
 * **Search is local.** Every person is already on the client — that is what the flat payload is for
 * — so jumping to a name is a filter over an array rather than a request, and it answers while
 * somebody is still typing. The server-side search (`/persons?q=`) stays the right tool for the list
 * view, where the result set can exceed what the tree holds.
 *
 * **"Find me" only appears when there is a me to find.** Most nodes have no account attached and
 * most members are not yet linked to one (that is Phase 6), so the control is hidden rather than
 * shown disabled — a button that never works is worse than no button.
 */

interface TreeToolbarProps {
  persons: PersonSummary[];
  branches: TreeBranch[];
  /** The node linked to the signed-in account, if the tree contains one. */
  myPersonId?: string;
  onJumpToPerson: (personId: string) => void;
  onFit: () => void;
  /** Levels in the fully expanded tree; the "show N generations" choices run up to it. */
  levels: number;
  /** Show this many generations, or all of them for null. */
  onLimitGenerations: (levels: number | null) => void;
  /** Opens the print and export dialog (#61). */
  onPrint: () => void;
  disabled: boolean;
}

export function TreeToolbar({
  persons,
  branches,
  myPersonId,
  onJumpToPerson,
  onFit,
  levels,
  onLimitGenerations,
  onPrint,
  disabled,
}: TreeToolbarProps) {
  const { t } = useTranslation();

  const orientation = useTreeViewStore((state) => state.orientation);
  const setOrientation = useTreeViewStore((state) => state.setOrientation);
  const zoomBy = useTreeViewStore((state) => state.zoomBy);
  const scale = useTreeViewStore((state) => state.scale);
  const highlightBranchId = useTreeViewStore((state) => state.highlightBranchId);
  const highlightBranch = useTreeViewStore((state) => state.highlightBranch);
  const collapsed = useTreeViewStore((state) => state.collapsed);
  const expandAll = useTreeViewStore((state) => state.expandAll);
  const fullscreen = useTreeViewStore((state) => state.fullscreen);
  const setFullscreen = useTreeViewStore((state) => state.setFullscreen);
  const generationLimit = useTreeViewStore((state) => state.generationLimit);
  const scheme = useTreeViewStore((state) => state.scheme);
  const setScheme = useTreeViewStore((state) => state.setScheme);
  const nextScheme = scheme === 'light' ? 'dark' : 'light';

  // "All", then every depth short of the full tree — showing all N levels is the same as "All".
  const generationOptions = useMemo(
    () => [
      { value: 0, label: t('tree.canvas.generationsAll') },
      ...Array.from({ length: Math.max(0, levels - 1) }, (_, index) => ({
        value: index + 1,
        label: t('tree.canvas.generationsShown', { count: index + 1 }),
      })),
    ],
    [levels, t],
  );

  const searchOptions = useMemo(
    () =>
      persons.map((person) => ({
        value: person.id,
        label: `${person.fullName} · ${formatGeneration(person.generation, t)}`,
      })),
    [persons, t],
  );

  const branchOptions = useMemo(
    () =>
      [...branches]
        .sort((left, right) => left.path.localeCompare(right.path))
        .map((branch) => ({ value: branch.id, label: branch.name })),
    [branches],
  );

  return (
    <ToolbarBar>
      <Select<string | null, { value: string; label: string }>
        showSearch
        allowClear
        // Never holds a selection: jumping is an action, and a box still showing the last name
        // somebody jumped to reads as a filter that is still applied.
        value={null}
        disabled={disabled}
        options={searchOptions}
        placeholder={t('tree.canvas.searchPlaceholder')}
        optionFilterProp="label"
        // Diacritic-insensitive, the way the server's search is (B4.11): somebody typing "an" on a
        // keyboard without Vietnamese input should still find "Nguyễn Văn An".
        filterOption={(input, option) => fold(option?.label ?? '').includes(fold(input))}
        onChange={(value) => {
          if (value !== null) onJumpToPerson(value);
        }}
        aria-label={t('tree.canvas.searchPlaceholder')}
      />

      <Select<string | null, { value: string; label: string }>
        allowClear
        value={highlightBranchId}
        disabled={disabled || branchOptions.length === 0}
        options={branchOptions}
        placeholder={t('tree.canvas.highlightBranch')}
        onChange={(value) => {
          highlightBranch(value ?? null);
        }}
        aria-label={t('tree.canvas.highlightBranch')}
      />

      {myPersonId !== undefined && (
        <Button
          icon={<AimOutlined />}
          disabled={disabled}
          onClick={() => {
            onJumpToPerson(myPersonId);
          }}
        >
          {t('tree.canvas.findMe')}
        </Button>
      )}

      <ToolbarSpacer />

      <Select<number, { value: number; label: string }>
        value={generationLimit ?? 0}
        disabled={disabled || levels < 2}
        options={generationOptions}
        popupMatchSelectWidth={false}
        onChange={(value) => {
          onLimitGenerations(value === 0 ? null : value);
        }}
        aria-label={t('tree.canvas.generations')}
      />

      {collapsed.size > 0 && (
        <Button type="link" onClick={expandAll}>
          {t('tree.canvas.expandAll', { hidden: collapsed.size })}
        </Button>
      )}

      <Tooltip title={t('tree.canvas.orientation')}>
        <Button
          icon={orientation === 'vertical' ? <ColumnWidthOutlined /> : <CompressOutlined />}
          disabled={disabled}
          aria-label={t('tree.canvas.orientation')}
          onClick={() => {
            setOrientation(orientation === 'vertical' ? 'horizontal' : 'vertical');
          }}
        />
      </Tooltip>

      <Space.Compact>
        <Tooltip title={t('tree.canvas.zoomOut')}>
          <Button
            icon={<MinusOutlined />}
            disabled={disabled || scale <= ZOOM.min}
            aria-label={t('tree.canvas.zoomOut')}
            onClick={() => {
              zoomBy(1 / ZOOM.step);
            }}
          />
        </Tooltip>
        <Tooltip title={t('tree.canvas.fit')}>
          <Button
            icon={<ExpandOutlined />}
            disabled={disabled}
            aria-label={t('tree.canvas.fit')}
            onClick={onFit}
          />
        </Tooltip>
        <Tooltip title={t('tree.canvas.zoomIn')}>
          <Button
            icon={<PlusOutlined />}
            disabled={disabled || scale >= ZOOM.max}
            aria-label={t('tree.canvas.zoomIn')}
            onClick={() => {
              zoomBy(ZOOM.step);
            }}
          />
        </Tooltip>
      </Space.Compact>

      {/* The canvas's own scheme (#61): a long read of a large clan is easier on dark, and the
          forms around it are unaffected. */}
      <Tooltip title={t(`tree.canvas.scheme.${nextScheme}`)}>
        <Button
          icon={scheme === 'light' ? <MoonOutlined /> : <SunOutlined />}
          aria-label={t(`tree.canvas.scheme.${nextScheme}`)}
          aria-pressed={scheme === 'dark'}
          onClick={() => {
            setScheme(nextScheme);
          }}
        />
      </Tooltip>

      <Tooltip title={t('tree.print.open')}>
        <Button
          icon={<PrinterOutlined />}
          disabled={disabled}
          aria-label={t('tree.print.open')}
          onClick={onPrint}
        />
      </Tooltip>

      <Tooltip title={t(fullscreen ? 'tree.canvas.exitFullscreen' : 'tree.canvas.fullscreen')}>
        <Button
          icon={fullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
          aria-label={t(fullscreen ? 'tree.canvas.exitFullscreen' : 'tree.canvas.fullscreen')}
          aria-pressed={fullscreen}
          onClick={() => {
            setFullscreen(!fullscreen);
          }}
        />
      </Tooltip>
    </ToolbarBar>
  );
}
