import { DownOutlined, ReadOutlined, UpOutlined } from '@ant-design/icons';
import { useId } from 'react';
import { useTranslation } from 'react-i18next';

import { GENDERS } from '../../model/visual';

import { DescentGlyph, GenderGlyph, MourningGlyph, RingGlyph, UnionGlyph } from './Glyphs';
import { LegendHeading, LegendList, LegendPanel, LegendToggle } from './styles';

/**
 * What the shapes, lines and marks on the canvas mean (#61).
 *
 * On the canvas, collapsible, because a reader who knows the vocabulary does not want it covering
 * the tree; on the print sheet, always open, because paper has no button to press. The entries are
 * drawn with the canvas's own parts (see `Glyphs`), so the legend cannot disagree with the tree.
 */

interface LegendProps {
  /** Collapsible on screen. Without it the legend is always open, as it is in print. */
  open?: boolean;
  onToggle?: (open: boolean) => void;
  /** Leave out the "You" entry when nobody signed in is in this tree. */
  showSelf: boolean;
}

export function Legend({ open = true, onToggle, showSelf }: LegendProps) {
  const { t } = useTranslation();
  const listId = useId();

  return (
    <LegendPanel>
      {onToggle !== undefined && (
        <LegendToggle
          type="button"
          aria-expanded={open}
          aria-controls={listId}
          onClick={() => {
            onToggle(!open);
          }}
        >
          <ReadOutlined />
          <span>{t('tree.legend.title')}</span>
          {open ? <DownOutlined /> : <UpOutlined />}
        </LegendToggle>
      )}

      {open && <LegendEntries id={listId} showSelf={showSelf} />}
    </LegendPanel>
  );
}

export function LegendEntries({ id, showSelf }: { id?: string; showSelf: boolean }) {
  const { t } = useTranslation();
  return (
    <LegendList id={id}>
      <LegendHeading>{t('tree.legend.people')}</LegendHeading>
      {GENDERS.map((gender) => (
        <LegendRow key={gender} glyph={<GenderGlyph gender={gender} />}>
          {t(`tree.legend.gender.${gender}`)}
        </LegendRow>
      ))}
      <LegendRow glyph={<MourningGlyph />}>{t('tree.legend.deceased')}</LegendRow>
      {showSelf && <LegendRow glyph={<RingGlyph self />}>{t('tree.legend.self')}</LegendRow>}
      <LegendRow glyph={<RingGlyph self={false} />}>{t('tree.legend.linked')}</LegendRow>

      <LegendHeading>{t('tree.legend.relations')}</LegendHeading>
      <LegendRow glyph={<UnionGlyph secondary={false} />}>
        {t('tree.legend.unionPrimary')}
      </LegendRow>
      <LegendRow glyph={<UnionGlyph secondary />}>{t('tree.legend.unionSecondary')}</LegendRow>
      <LegendRow glyph={<DescentGlyph dashed={false} />}>{t('tree.legend.biological')}</LegendRow>
      <LegendRow glyph={<DescentGlyph dashed />}>{t('tree.legend.adoptive')}</LegendRow>
    </LegendList>
  );
}

function LegendRow({ glyph, children }: { glyph: React.ReactNode; children: React.ReactNode }) {
  return (
    <>
      <dt>{glyph}</dt>
      <dd>{children}</dd>
    </>
  );
}
