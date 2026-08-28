import { api } from '../client';

/** `/clans/{clanId}/members` — doc 05 §4.5. */

/** The paged envelope of doc 05 §3.2. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Member {
  userId: string;
  displayName?: string;
  email?: string;
  avatarMediaId?: string;
  homeBranchId?: string;
  homeBranchName?: string;
  joinedAt: string;
  /** Whether this member holds CLAN_ADMIN. Any number of people can; this is who to ask. */
  isAdministrator: boolean;
  /** The person node this member is linked to. Null until node claiming ships. */
  linkedPersonId?: string;
}

export interface MemberFilters {
  search?: string;
  branchId?: string;
  page?: number;
  size?: number;
}

export function listMembers(
  clanId: string,
  filters: MemberFilters,
  signal?: AbortSignal,
): Promise<Page<Member>> {
  return api.get<Page<Member>>(`/clans/${clanId}/members`, {
    query: {
      search: filters.search,
      branchId: filters.branchId,
      page: filters.page ?? 0,
      size: filters.size ?? 20,
    },
    ...(signal ? { signal } : {}),
  });
}

export function removeMember(clanId: string, userId: string): Promise<void> {
  return api.del<undefined>(`/clans/${clanId}/members/${userId}`);
}

export function leaveClan(clanId: string): Promise<void> {
  return api.post<undefined>(`/clans/${clanId}/members/leave`);
}
