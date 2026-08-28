import { PlusOutlined, UndoOutlined } from '@ant-design/icons';
import { App as AntApp, Alert, Button, Dropdown, Skeleton, Statistic, Tag, Tooltip } from 'antd';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router';

import { type PersonSummary, type PlacementType } from '@/api/endpoints/persons.ts';
import { buildTreeNodes, type TreeNode } from '@/api/endpoints/tree.ts';
import { useActiveClan } from '@/hooks/useActiveClan.ts';
import { toMessage } from '@/lib/apiErrors.ts';

import { useBranches } from '../../branch';
import { PersonDrawer } from '../components/PersonDrawer';
import { PersonFormModal, type PersonFormMode } from '../components/PersonFormModal';
import {
  Page,
  PageHeader,
  PageSubtitle,
  PageTitle,
  PersonMeta,
  PersonName,
  SectionCard,
  StatGrid,
  TreeTable,
} from '../components/styles';
import { useDeletedPersons, useRestorePerson, useTree, useTreeStats } from '../hooks/useTree';
import { formatGeneration, formatLifespan } from '../model/format';

/** One row of the table: a person, their spouses, and the rows below them. */
interface TreeRow {
  key: string;
  person: PersonSummary;
  spouses: PersonSummary[];
  children?: TreeRow[];
}

/**
 * The clan tree as a list (F4.6).
 *
 * **The interim view and the permanent accessible one.** Phase 5 brings a canvas; this stays, because
 * NFR-7 wants a keyboard- and screen-reader-navigable route to every node and a table is that. It is
 * also the only view that works on a phone, and the only one that can be read aloud.
 *
 * Rows nest by descent and expand from the roots. Spouses appear beside the person they married
 * rather than as rows of their own — that is how the book reads, and it halves the row count.
 */
export function TreePage() {
  const { t } = useTranslation();
  const { message } = AntApp.useApp();
  const { clanId = '' } = useParams();
  const { can } = useActiveClan();

  const { data: tree, isPending, error } = useTree(clanId);
  const { data: stats } = useTreeStats(clanId);
  const { data: branches } = useBranches(clanId);

  const isAdmin = can('CLAN_ADMIN');
  const { data: deleted } = useDeletedPersons(clanId, isAdmin);
  const restorePerson = useRestorePerson(clanId);

  const [openPersonId, setOpenPersonId] = useState<string | null>(null);
  const [creating, setCreating] = useState<PersonFormMode | null>(null);

  const branchNames = useMemo(
    () => new Map((branches ?? []).map((branch) => [branch.id, branch.name])),
    [branches],
  );

  const rows = useMemo<TreeRow[]>(
    () => (tree === undefined ? [] : buildTreeNodes(tree).map(toRow)),
    [tree],
  );

  const canAddAnywhere = can('TREE_EDIT');

  if (error !== null) {
    return (
      <Page>
        <PageHeader>
          <div>
            <PageTitle>{t('tree.title')}</PageTitle>
            <PageSubtitle>{toMessage(error, t)}</PageSubtitle>
          </div>
        </PageHeader>
      </Page>
    );
  }

  return (
    <Page>
      <PageHeader>
        <div>
          <PageTitle>{t('tree.title')}</PageTitle>
          <PageSubtitle>{t('tree.subtitle')}</PageSubtitle>
        </div>
        {canAddAnywhere && (
          <Dropdown
            menu={{
              items: (
                ['CHILD_OF_PARENT', 'SPOUSE_OF', 'BRANCH_ROOT', 'UNPLACED'] as PlacementType[]
              ).map((placementType) => ({
                key: placementType,
                label: t(`tree.placement.${placementType}`),
                onClick: () => {
                  setCreating({ kind: 'create', placementType });
                },
              })),
            }}
          >
            <Button type="primary" icon={<PlusOutlined />}>
              {t('tree.action.addPerson')}
            </Button>
          </Dropdown>
        )}
      </PageHeader>

      {stats !== undefined && (
        <SectionCard>
          <StatGrid>
            <Statistic title={t('tree.stats.people')} value={stats.personCount} />
            <Statistic title={t('tree.stats.generations')} value={stats.maxGeneration ?? 0} />
            <Statistic title={t('tree.stats.living')} value={stats.livingCount} />
            <Statistic title={t('tree.stats.unlinked')} value={stats.unlinkedCount} />
          </StatGrid>
        </SectionCard>
      )}

      {!isPending && rows.length === 0 && (
        <Alert type="info" showIcon message={t('tree.empty')} description={t('tree.emptyHelp')} />
      )}

      <SectionCard>
        <Skeleton loading={isPending} active>
          <TreeTable<TreeRow>
            rowKey="key"
            dataSource={rows}
            pagination={false}
            scroll={{ x: 'max-content' }}
            expandable={{ defaultExpandAllRows: false }}
            onRow={(row) => ({
              onClick: () => {
                setOpenPersonId(row.person.id);
              },
            })}
            columns={[
              {
                title: t('tree.column.name'),
                key: 'name',
                render: (_, row: TreeRow) => (
                  <>
                    <PersonName $readOnly={!row.person.canEdit}>{row.person.fullName}</PersonName>
                    {row.spouses.length > 0 && (
                      <PersonMeta>
                        {t('tree.column.marriedTo', {
                          names: row.spouses.map((spouse) => spouse.fullName).join(', '),
                        })}
                      </PersonMeta>
                    )}
                  </>
                ),
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
                    // F4.8: say which part of the tree they *can* edit rather than showing a
                    // disabled control with no explanation.
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
      </SectionCard>

      {isAdmin && deleted !== undefined && deleted.length > 0 && (
        <SectionCard title={t('tree.deleted.title')}>
          <PageSubtitle>{t('tree.deleted.help')}</PageSubtitle>
          {deleted.map((person) => (
            <div key={person.id}>
              <PersonName $readOnly>{person.fullName}</PersonName>
              <Button
                type="link"
                icon={<UndoOutlined />}
                loading={restorePerson.isPending}
                onClick={() => {
                  void restorePerson
                    .mutateAsync(person.id)
                    .then(() => message.success(t('tree.deleted.restored')))
                    .catch((failure: unknown) => message.error(toMessage(failure, t)));
                }}
              >
                {t('tree.deleted.restore')}
              </Button>
            </div>
          ))}
        </SectionCard>
      )}

      <PersonDrawer
        clanId={clanId}
        personId={openPersonId}
        onClose={() => {
          setOpenPersonId(null);
        }}
        onOpenPerson={setOpenPersonId}
      />

      {creating !== null && (
        <PersonFormModal
          clanId={clanId}
          open
          mode={creating}
          onClose={() => {
            setCreating(null);
          }}
          onSaved={setOpenPersonId}
        />
      )}
    </Page>
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
