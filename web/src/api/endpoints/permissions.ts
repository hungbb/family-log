import { api } from '../client';

import { type ClanPermission } from './clans';

/**
 * `/clans/{clanId}/permissions` and `/clans/{clanId}/my-permissions` — doc 05 §4.3, §4.6.
 *
 * The shapes mirror the backend's `GrantResponse`, `GrantableResponse` and `MyPermissionsResponse`
 * exactly. When the OpenAPI generator lands (doc 07 §4.4) these become imports from `api/types.ts`;
 * until then they are hand-written and the field names are the contract.
 */

/** Where a permission applies. `SUBTREE` exists in the model but is not grantable until Phase 4. */
export type ScopeType = 'CLAN' | 'BRANCH' | 'SUBTREE';

/** One grant, as the permissions screen shows it. */
export interface Grant {
  id: string;
  clanId: string;
  granteeUserId: string;
  granteeName?: string;
  granteeEmail?: string;
  permission: ClanPermission;
  scopeType: ScopeType;
  /** The branch, for a `BRANCH` scope. Absent for a clan-wide grant. */
  scopeRefId?: string;
  /** The branch's name. Absent for a clan-wide grant, which needs no label. */
  scopeLabel?: string;
  grantedByUserId?: string;
  grantedByName?: string;
  grantedAt: string;
  /** Absent for a grant that stands until somebody revokes it. */
  expiresAt?: string;
}

/**
 * What the caller holds in one clan.
 *
 * Two answers, and they are not the same one. `permissions` lays out the interface; `grants` is what
 * a person reads when they want to know *why* they can do something, and when it stops.
 */
export interface MyPermissions {
  permissions: ClanPermission[];
  grants: Grant[];
}

/** One place a permission may be granted at. `depth` lets the picker indent the branch tree. */
export interface GrantableScope {
  scopeType: ScopeType;
  scopeRefId?: string;
  label?: string;
  depth: number;
}

export interface GrantablePermission {
  permission: ClanPermission;
  scopes: GrantableScope[];
}

/**
 * The whole vocabulary the grant dialog is built from (F3.4).
 *
 * The client offers nothing that is not in here, so an escalation is not something the UI declines
 * to do — it is something the UI cannot express. The server refuses one that arrives anyway; this
 * is ergonomics, never the boundary (doc 07 §8).
 */
export interface Grantable {
  permissions: GrantablePermission[];
  /** False until the family tree exists. The scope picker shows the option disabled, not hidden. */
  subtreeAvailable: boolean;
}

export interface CreateGrantInput {
  granteeUserId: string;
  permission: ClanPermission;
  scopeType: ScopeType;
  /** Required for a `BRANCH` scope, omitted for `CLAN`. */
  scopeRefId?: string;
  /** ISO-8601. Omit for a permission that stands until somebody takes it back. */
  expiresAt?: string;
}

export interface GrantFilters {
  userId?: string;
  permission?: ClanPermission;
}

export function getMyPermissions(clanId: string, signal?: AbortSignal): Promise<MyPermissions> {
  return api.get<MyPermissions>(`/clans/${clanId}/my-permissions`, signal ? { signal } : {});
}

export function listGrants(
  clanId: string,
  filters: GrantFilters,
  signal?: AbortSignal,
): Promise<Grant[]> {
  return api.get<Grant[]>(`/clans/${clanId}/permissions`, {
    query: { userId: filters.userId, permission: filters.permission },
    ...(signal ? { signal } : {}),
  });
}

export function getGrantable(clanId: string, signal?: AbortSignal): Promise<Grantable> {
  return api.get<Grantable>(`/clans/${clanId}/permissions/grantable`, signal ? { signal } : {});
}

export function createGrant(clanId: string, input: CreateGrantInput): Promise<Grant> {
  return api.post<Grant>(`/clans/${clanId}/permissions`, input);
}

export function revokeGrant(clanId: string, grantId: string): Promise<void> {
  return api.del<undefined>(`/clans/${clanId}/permissions/${grantId}`);
}
