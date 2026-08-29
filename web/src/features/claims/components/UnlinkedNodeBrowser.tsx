import { Button, Empty, Input, List, Select, Skeleton } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { flattenBranches } from '@/api/endpoints/branches.ts';
import { type UnlinkedCandidate } from '@/api/endpoints/links.ts';
import { useBranches } from '@/features/branch';

import { useUnlinkedCandidates } from '../hooks/useClaims';
import { FilterBar, Meta, TreeContext } from '../pages/styles';

import { ClaimDialog, describeFamily } from './ClaimDialog';

interface UnlinkedNodeBrowserProps {
  clanId: string;
}

/**
 * "Find yourself in the tree" — the unlinked-node browser (F6.2).
 *
 * Only unlinked nodes are listed, and that is the server's filter rather than ours: a node somebody
 * else already holds is not a candidate, and showing it greyed out would invite a claim that can
 * only ever be refused.
 *
 * The filters are name, đời and *chi* because that is how a Vietnamese family record is actually
 * navigated — a person knows which branch they are from and roughly which generation, far more
 * reliably than they know how a great-uncle spelled their name in the book.
 */
export function UnlinkedNodeBrowser({ clanId }: UnlinkedNodeBrowserProps) {
  const { t } = useTranslation();
  const { data: branches } = useBranches(clanId);

  const [search, setSearch] = useState('');
  const [generation, setGeneration] = useState<number | undefined>(undefined);
  const [branchId, setBranchId] = useState<string | undefined>(undefined);
  const [page, setPage] = useState(0);
  const [claiming, setClaiming] = useState<UnlinkedCandidate | null>(null);

  const filters = {
    ...(search !== '' ? { q: search } : {}),
    ...(generation !== undefined ? { generation } : {}),
    ...(branchId !== undefined ? { branchId } : {}),
    page,
    size: 20,
  };
  const { data, isPending } = useUnlinkedCandidates(clanId, filters);

  const branchOptions = flattenBranches(branches ?? [])
    .filter((branch) => !branch.archived)
    .map((branch) => ({
      value: branch.id,
      label: `${'  '.repeat(branch.depth)}${branch.name}`,
    }));

  // đời 1–15 covers every clan the record has; beyond that a person can still search by name.
  const generationOptions = Array.from({ length: 15 }, (_value, index) => ({
    value: index + 1,
    label: t('claims.browse.generationOption', { n: index + 1 }),
  }));

  return (
    <>
      <FilterBar>
        <Input.Search
          allowClear
          placeholder={t('claims.browse.searchPlaceholder')}
          onSearch={(value) => {
            setPage(0);
            setSearch(value.trim());
          }}
        />
        <Select
          allowClear
          placeholder={t('claims.browse.allGenerations')}
          value={generation}
          options={generationOptions}
          onChange={(value: number | undefined) => {
            setPage(0);
            setGeneration(value);
          }}
        />
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder={t('claims.browse.allBranches')}
          value={branchId}
          options={branchOptions}
          onChange={(value: string | undefined) => {
            setPage(0);
            setBranchId(value);
          }}
        />
      </FilterBar>

      {isPending ? (
        <Skeleton active />
      ) : (
        <List
          dataSource={data?.content ?? []}
          locale={{ emptyText: <Empty description={t('claims.browse.empty')} /> }}
          pagination={
            (data?.totalElements ?? 0) > (data?.size ?? 20)
              ? {
                  current: page + 1,
                  pageSize: data?.size ?? 20,
                  total: data?.totalElements ?? 0,
                  showSizeChanger: false,
                  onChange: (next) => {
                    setPage(next - 1);
                  },
                }
              : false
          }
          renderItem={(candidate) => (
            <List.Item
              key={candidate.person.id}
              actions={[
                <Button
                  key="claim"
                  type="primary"
                  size="small"
                  onClick={() => {
                    setClaiming(candidate);
                  }}
                >
                  {t('claims.browse.thisIsMe')}
                </Button>,
              ]}
            >
              <List.Item.Meta
                title={candidate.person.fullName}
                description={
                  <>
                    <Meta>
                      {[
                        candidate.person.generation !== undefined
                          ? t('claims.browse.generationOption', { n: candidate.person.generation })
                          : t('claims.browse.generationUnknown'),
                        t(`tree.gender.${candidate.person.gender}`),
                      ].join(' · ')}
                    </Meta>
                    <TreeContext>
                      {describeFamily(
                        candidate,
                        t('claims.candidate.parents'),
                        t('claims.candidate.children'),
                      ) || t('claims.candidate.noFamily')}
                    </TreeContext>
                  </>
                }
              />
            </List.Item>
          )}
        />
      )}

      <ClaimDialog
        clanId={clanId}
        candidate={claiming}
        onClose={() => {
          setClaiming(null);
        }}
      />
    </>
  );
}
