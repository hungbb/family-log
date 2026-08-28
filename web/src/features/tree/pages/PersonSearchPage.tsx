import { Input, Select, Skeleton, Tag, Tooltip } from 'antd';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router';

import { type LifeStatus, type PersonSummary } from '@/api/endpoints/persons.ts';
import { toMessage } from '@/lib/apiErrors.ts';

import { useBranches } from '../../branch';
import { PersonDrawer } from '../components/PersonDrawer';
import {
  FilterBar,
  Page,
  PageHeader,
  PageSubtitle,
  PageTitle,
  PersonMeta,
  PersonName,
  SectionCard,
  TreeTable,
} from '../components/styles';
import { usePersonSearch } from '../hooks/useTree';
import { formatGeneration, formatLifespan } from '../model/format';

const PAGE_SIZE = 20;

/**
 * Finding a relative (F4.7, FR-P11).
 *
 * **Diacritics are optional in both directions.** The server folds them on the way in and on the way
 * out, so "nguyen van an" finds "Nguyễn Văn An" and so does "NGUYỄN" — which matters because most
 * people looking up an ancestor are doing it from a phone without a Vietnamese keyboard.
 *
 * The filters are the ones somebody actually narrows by when a surname returns forty rows: which
 * đời, which chi, living or not, and whether the node already belongs to somebody's account.
 */
export function PersonSearchPage() {
  const { t } = useTranslation();
  const { clanId = '' } = useParams();
  const { data: branches } = useBranches(clanId);

  const [term, setTerm] = useState('');
  const [generation, setGeneration] = useState<number | undefined>(undefined);
  const [lifeStatus, setLifeStatus] = useState<LifeStatus | undefined>(undefined);
  const [branchId, setBranchId] = useState<string | undefined>(undefined);
  const [linked, setLinked] = useState<boolean | undefined>(undefined);
  const [page, setPage] = useState(0);
  const [openPersonId, setOpenPersonId] = useState<string | null>(null);

  const filters = {
    ...(term === '' ? {} : { q: term }),
    ...(generation === undefined ? {} : { generation }),
    ...(lifeStatus === undefined ? {} : { lifeStatus }),
    ...(branchId === undefined ? {} : { branchId }),
    ...(linked === undefined ? {} : { linked }),
    page,
    size: PAGE_SIZE,
  };

  const { data, isPending, error } = usePersonSearch(clanId, filters);
  const branchNames = useMemo(
    () => new Map((branches ?? []).map((branch) => [branch.id, branch.name])),
    [branches],
  );

  return (
    <Page>
      <PageHeader>
        <div>
          <PageTitle>{t('tree.search.title')}</PageTitle>
          <PageSubtitle>{t('tree.search.subtitle')}</PageSubtitle>
        </div>
      </PageHeader>

      <SectionCard>
        <FilterBar>
          <Input.Search
            allowClear
            placeholder={t('tree.search.placeholder')}
            defaultValue={term}
            onSearch={(value) => {
              setTerm(value);
              setPage(0);
            }}
          />
          <Select
            allowClear
            placeholder={t('tree.column.generation')}
            value={generation}
            onChange={(value: number | undefined) => {
              setGeneration(value);
              setPage(0);
            }}
            options={Array.from({ length: 20 }, (_, index) => ({
              value: index + 1,
              label: formatGeneration(index + 1, t),
            }))}
          />
          <Select
            allowClear
            placeholder={t('tree.field.lifeStatus')}
            value={lifeStatus}
            onChange={(value: LifeStatus | undefined) => {
              setLifeStatus(value);
              setPage(0);
            }}
            options={(['ALIVE', 'DECEASED', 'UNKNOWN'] as LifeStatus[]).map((value) => ({
              value,
              label: t(`tree.lifeStatus.${value}`),
            }))}
          />
          <Select
            allowClear
            placeholder={t('tree.column.branch')}
            value={branchId}
            onChange={(value: string | undefined) => {
              setBranchId(value);
              setPage(0);
            }}
            options={(branches ?? []).map((branch) => ({
              value: branch.id,
              label: branch.name,
            }))}
          />
          <Select
            allowClear
            placeholder={t('tree.search.linked')}
            value={linked}
            onChange={(value: boolean | undefined) => {
              setLinked(value);
              setPage(0);
            }}
            options={[
              { value: true, label: t('tree.search.linkedYes') },
              { value: false, label: t('tree.search.linkedNo') },
            ]}
          />
        </FilterBar>

        {error !== null && <PageSubtitle>{toMessage(error, t)}</PageSubtitle>}

        <Skeleton loading={isPending} active>
          <TreeTable<PersonSummary>
            rowKey="id"
            dataSource={data?.content ?? []}
            onRow={(person) => ({
              onClick: () => {
                setOpenPersonId(person.id);
              },
            })}
            pagination={{
              current: page + 1,
              pageSize: PAGE_SIZE,
              total: data?.totalElements ?? 0,
              showSizeChanger: false,
              onChange: (next) => {
                setPage(next - 1);
              },
            }}
            columns={[
              {
                title: t('tree.column.name'),
                key: 'name',
                render: (_, person: PersonSummary) => (
                  <>
                    <PersonName $readOnly={!person.canEdit}>{person.fullName}</PersonName>
                    {person.alias !== undefined && <PersonMeta>{person.alias}</PersonMeta>}
                  </>
                ),
              },
              {
                title: t('tree.column.generation'),
                key: 'generation',
                width: 120,
                render: (_, person: PersonSummary) => formatGeneration(person.generation, t),
              },
              {
                title: t('tree.column.lifespan'),
                key: 'lifespan',
                width: 200,
                render: (_, person: PersonSummary) =>
                  formatLifespan(person, t) ?? t('tree.unknown'),
              },
              {
                title: t('tree.column.branch'),
                key: 'branch',
                width: 160,
                render: (_, person: PersonSummary) =>
                  person.branchId === undefined ? (
                    <Tag>{t('tree.unplaced')}</Tag>
                  ) : (
                    (branchNames.get(person.branchId) ?? t('tree.unknown'))
                  ),
              },
              {
                title: t('tree.column.access'),
                key: 'access',
                width: 120,
                render: (_, person: PersonSummary) =>
                  person.canEdit ? (
                    <Tag color="green">{t('tree.canEdit')}</Tag>
                  ) : (
                    <Tooltip title={t('tree.readOnly.generic')}>
                      <Tag>{t('tree.readOnlyTag')}</Tag>
                    </Tooltip>
                  ),
              },
            ]}
          />
        </Skeleton>
      </SectionCard>

      <PersonDrawer
        clanId={clanId}
        personId={openPersonId}
        onClose={() => {
          setOpenPersonId(null);
        }}
        onOpenPerson={setOpenPersonId}
      />
    </Page>
  );
}
