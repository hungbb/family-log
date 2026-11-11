import { describe, expect, it } from 'vitest';

import { branchNamesById, branchSelectOptions, flattenBranches, type Branch } from './branches';

/** One branch, with the fields these helpers read and plausible values for the rest. */
function branch(id: string, name: string, depth: number, children: Branch[] = []): Branch {
  return {
    id,
    clanId: 'clan',
    branchCode: `${id.toUpperCase()}-0000`,
    name,
    path: `/${id}/`,
    depth,
    archived: false,
    memberCount: 0,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    children,
  };
}

/**
 * The shape `GET /clans/{id}/branches` actually returns: **one** top-level entry, the clan's root
 * branch, with every chi nested beneath it.
 */
const response: Branch[] = [
  branch('root', 'Họ Nguyễn Phúc', 0, [
    branch('nhat', 'Chi Nhất', 1),
    branch('hai', 'Chi Hai', 1, [branch('ut', 'Nhánh Út', 2)]),
    { ...branch('tam', 'Chi tạm', 1), archived: true },
  ]),
];

describe('flattenBranches', () => {
  it('lists every branch, each parent before its children', () => {
    expect(flattenBranches(response).map((item) => item.id)).toEqual([
      'root',
      'nhat',
      'hai',
      'ut',
      'tam',
    ]);
  });
});

describe('branchNamesById', () => {
  it('names a branch at any depth, not only the root', () => {
    const names = branchNamesById(response);

    expect(names.get('root')).toBe('Họ Nguyễn Phúc');
    expect(names.get('hai')).toBe('Chi Hai');
    expect(names.get('ut')).toBe('Nhánh Út');
    expect(names.size).toBe(5);
  });

  it('still names an archived branch — people placed there are still there', () => {
    expect(branchNamesById(response).get('tam')).toBe('Chi tạm');
  });

  it('has no entry for an id from somewhere else', () => {
    expect(branchNamesById(response).get('other-clan')).toBeUndefined();
    expect(branchNamesById([]).size).toBe(0);
  });
});

describe('branchSelectOptions', () => {
  it('offers the whole hierarchy, indented by depth', () => {
    expect(branchSelectOptions(response)).toEqual([
      { value: 'root', label: 'Họ Nguyễn Phúc' },
      { value: 'nhat', label: '  Chi Nhất' },
      { value: 'hai', label: '  Chi Hai' },
      { value: 'ut', label: '    Nhánh Út' },
    ]);
  });

  it('leaves archived branches out of a picker, and puts them back for a filter', () => {
    expect(branchSelectOptions(response).map((option) => option.value)).not.toContain('tam');
    expect(
      branchSelectOptions(response, { includeArchived: true }).map((option) => option.value),
    ).toContain('tam');
  });
});
