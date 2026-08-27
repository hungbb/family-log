import { api } from '../client';

/**
 * `/clans` — doc 05 §4.3.
 *
 * The shapes here mirror the backend's `ClanResponse` and friends exactly. When the OpenAPI
 * generator lands (doc 07 §4.4) these become imports from `api/types.ts`; until then they are
 * hand-written and the field names are the contract.
 */

/**
 * What the caller may do in a clan.
 *
 * A string union rather than an enum because the server may add values within v1 (doc 05 §6), and a
 * client that crashes on an unknown one is a client that breaks on a deploy. Everything reading
 * these treats an unrecognised value as "no extra affordance".
 */
export type ClanPermission =
  | 'CLAN_VIEW'
  | 'TREE_EDIT'
  | 'MEMBER_APPROVE'
  | 'NODE_CLAIM_APPROVE'
  | 'ROLE_MANAGE'
  | 'CLAN_ADMIN';

/** A clan as a member sees it. */
export interface Clan {
  id: string;
  clanCode: string;
  name: string;
  description?: string;
  originPlace?: string;
  coverMediaId?: string;
  visibility: string;
  rootBranchId?: string;
  memberCount: number;
  branchCount: number;
  /** For laying out the UI only. The server re-decides every one of these on every request. */
  myPermissions: ClanPermission[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** One row of the clan switcher. */
export interface ClanSummary {
  id: string;
  clanCode: string;
  name: string;
  originPlace?: string;
  coverMediaId?: string;
  rootBranchId?: string;
  homeBranchId?: string;
  joinedAt: string;
  myPermissions: ClanPermission[];
}

/**
 * The card shown to somebody holding a code, before they ask to join.
 *
 * Deliberately carries no clan id: the person looking at it is not a member.
 */
export interface ClanCard {
  clanCode: string;
  name: string;
  originPlace?: string;
  memberCount: number;
  /** Set when the code was a branch code, so the card can name the branch. */
  targetBranchId?: string;
  targetBranchName?: string;
  alreadyMember: boolean;
  hasPendingRequest: boolean;
}

export interface CreateClanInput {
  name: string;
  /** Optional. The clan name is used when it is absent, which is right more often than not. */
  surname?: string;
  originPlace?: string;
  description?: string;
}

/** An absent field is left unchanged. There is no `clanCode` here: it is immutable. */
export interface UpdateClanInput {
  name?: string;
  description?: string;
  originPlace?: string;
  /** The version last read. A mismatch is a 409 rather than a silent overwrite. */
  version: number;
}

export function createClan(input: CreateClanInput): Promise<Clan> {
  return api.post<Clan>('/clans', input);
}

export function listClans(signal?: AbortSignal): Promise<ClanSummary[]> {
  return api.get<ClanSummary[]>('/clans', signal ? { signal } : {});
}

export function getClan(clanId: string, signal?: AbortSignal): Promise<Clan> {
  return api.get<Clan>(`/clans/${clanId}`, signal ? { signal } : {});
}

export function updateClan(clanId: string, input: UpdateClanInput): Promise<Clan> {
  return api.patch<Clan>(`/clans/${clanId}`, input);
}

export function deleteClan(clanId: string): Promise<void> {
  return api.del<undefined>(`/clans/${clanId}`);
}

/** Accepts a clan code or a branch code — a person pasting one has no reason to know which. */
export function lookupClan(code: string, signal?: AbortSignal): Promise<ClanCard> {
  return api.get<ClanCard>('/clans/lookup', { query: { code }, ...(signal ? { signal } : {}) });
}
