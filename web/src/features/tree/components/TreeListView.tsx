import { Skeleton, Tag, Tooltip } from 'antd';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';

import { type PersonSummary } from '@/api/endpoints/persons.ts';
import { buildTreeNodes, type Tree, type TreeNode } from '@/api/endpoints/tree.ts';

import { formatGeneration, formatLifespan } from '../model/format';
import { visualOf } from '../model/visual';

import { GenderGlyph, MourningGlyph } from './canvas/Glyphs';
import { GlyphScope, VisuallyHidden } from './canvas/styles';
import { PersonMeta, PersonName, TreeTable } from './styles';

/**
 * The tree as a list (F4.6, F5.13).
 *
 * **Not a consolation prize** (phase 5 §8). NFR-7 requires a keyboard- and screen-reader-navigable
 * route to every node, and a nested table is that: rows expand by descent, every cell is text, and
 * the whole thing works with the canvas switched off. It is also the only view that works on a
 * phone, the one that will print, and the fastest way to audit a family's data.
 *
 * It shares the canvas's data hook — the same payload, the same `canEdit` flags, the same
 * invalidation — so the two views can never disagree about what the tree contains. It also shares
 * the canvas's visual vocabulary (#61): the same gender shapes, the same mourning band and the same
 * "You" marker, each with its meaning in words for a screen reader.
 */

interface TreeListViewProps {
  tree: Tree | undefined;
  loading: boolean;
  branchNames: Map<string, string>;
  /** The node linked to the signed-in account, marked as it is on the canvas. */
  myPersonId?: string;
  onOpenPerson: (personId: string) => void;
}

/** One row: a person, their spouses, and the rows below them. */
interface TreeRow {
  key: string;
  person: PersonSummary;
  spouses: PersonSummary[];
  children?: TreeRow[];
}

export function TreeListView({
  tree,
  loading,
  branchNames,
  myPersonId,
  onOpenPerson,
}: TreeListViewProps) {
  const { t } = useTranslation();

  const rows = useMemo<TreeRow[]>(
    () => (tree === undefined ? [] : buildTreeNodes(tree).map(toRow)),
    [tree],
  );

  return (
    <Skeleton loading={loading} active>
      <TreeTable<TreeRow>
        rowKey="key"
        dataSource={rows}
        pagination={false}
        scroll={{ x: 'max-content' }}
        expandable={{ defaultExpandAllRows: false }}
        onRow={(row) => ({
          onClick: () => {
            onOpenPerson(row.person.id);
          },
        })}
        columns={[
          {
            title: t('tree.column.name'),
            key: 'name',
            render: (_, row: TreeRow) => {
              const visual = visualOf(row.person, row.person.id === myPersonId);
              return (
                <>
                  <GlyphScope>
                    <GenderGlyph gender={visual.gender} deceased={visual.deceased} />
                    {visual.deceased && <MourningGlyph />}
                  </GlyphScope>
                  <VisuallyHidden>
                    {[
                      t(`tree.legend.gender.${visual.gender}`),
                      ...(visual.deceased ? [t('tree.legend.deceased')] : []),
                    ].join(', ')}
                  </VisuallyHidden>
                  <PersonName $readOnly={!row.person.canEdit}>{row.person.fullName}</PersonName>
                  {visual.self && (
                    <Tag color="volcano" bordered={false}>
                      {t('tree.canvas.you')}
                    </Tag>
                  )}
                  {row.spouses.length > 0 && (
                    <PersonMeta>
                      {t('tree.column.marriedTo', {
                        names: row.spouses.map((spouse) => spouse.fullName).join(', '),
                      })}
                    </PersonMeta>
                  )}
                </>
              );
            },
          },
          {
            title: t('tree.column.generation'),
            key: 'generation',
            width: 120,
            sorter: (left: TreeRow, right: TreeRow) =>
              (left.person.generation ?? Number.MAX_SAFE_INTEGER) -
              (right.person.generation ?? Number.MAX_SAFE_INTEGER),
            render: (_, row: TreeRow) => formatGeneration(row.person.generation, t),
          },
          {
            title: t('tree.column.lifespan'),
            key: 'lifespan',
            width: 200,
            render: (_, row: TreeRow) => formatLifespan(row.person, t) ?? t('tree.unknown'),
          },
          {
            title: t('tree.column.branch'),
            key: 'branch',
            width: 160,
            render: (_, row: TreeRow) =>
              row.person.branchId === undefined ? (
                <Tag>{t('tree.unplaced')}</Tag>
              ) : (
                (branchNames.get(row.person.branchId) ?? t('tree.unknown'))
              ),
          },
          {
            title: t('tree.column.access'),
            key: 'access',
            width: 120,
            render: (_, row: TreeRow) =>
              row.person.canEdit ? (
                <Tag color="green">{t('tree.canEdit')}</Tag>
              ) : (
                // F4.8: say which part of the tree they *can* edit rather than showing a disabled
                // control with no explanation.
                <Tooltip
                  title={
                    row.person.branchId === undefined
                      ? t('tree.readOnly.noBranch')
                      : t('tree.readOnly.branch', {
                          branch: branchNames.get(row.person.branchId) ?? '',
                        })
                  }
                >
                  <Tag>{t('tree.readOnlyTag')}</Tag>
                </Tooltip>
              ),
          },
        ]}
      />
    </Skeleton>
  );
}

function toRow(node: TreeNode): TreeRow {
  const children = node.children.map(toRow);
  return {
    key: node.person.id,
    person: node.person,
    spouses: node.spouses,
    // AntD renders an expander for any row with a `children` array, including an empty one — so a
    // leaf must not have the key at all.
    ...(children.length === 0 ? {} : { children }),
  };
}
