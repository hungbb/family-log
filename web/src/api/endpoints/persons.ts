import { api } from '../client';

/**
 * `/clans/{clanId}/persons` and the relationship routes — doc 05 §4.7, §4.8.
 *
 * The shapes mirror the backend's `PersonSummaryResponse`, `PersonDetailResponse`,
 * `ParentageResponse` and `UnionResponse` exactly. When the OpenAPI generator lands (doc 07 §4.4)
 * these become imports from `api/types.ts`; until then they are hand-written and the field names are
 * the contract.
 */

/** How much of a date the family record actually has (doc 05 §3.1). */
export type DatePrecision = 'EXACT' | 'MONTH' | 'YEAR' | 'RANGE' | 'UNKNOWN';

/**
 * A date at whatever precision is known.
 *
 * Most ancestors have a year and nothing else, so every field but `precision` is optional and the
 * form must never require more than the book contains. `lunarText` is the âm lịch reading recorded
 * verbatim — the server does not convert it and neither should we.
 */
export interface PartialDate {
  year?: number;
  month?: number;
  day?: number;
  precision: DatePrecision;
  circa: boolean;
  rangeEndYear?: number;
  lunarText?: string;
}

export type Gender = 'MALE' | 'FEMALE';
export type LifeStatus = 'ALIVE' | 'DECEASED' | 'UNKNOWN';
export type ParentRole = 'FATHER' | 'MOTHER';
export type ParentageNature = 'BIOLOGICAL' | 'ADOPTED' | 'STEP' | 'FOSTER';
export type UnionType = 'PRIMARY' | 'SECONDARY' | 'SEQUENTIAL';
export type UnionStatus = 'ACTIVE' | 'ENDED' | 'WIDOWED' | 'UNKNOWN';

/** Where a new person attaches. Drives both the branch they inherit and the permission checked. */
export type PlacementType =
  'CHILD_OF_UNION' | 'CHILD_OF_PARENT' | 'SPOUSE_OF' | 'BRANCH_ROOT' | 'UNPLACED';

export interface Placement {
  type: PlacementType;
  unionId?: string;
  personId?: string;
  branchId?: string;
}

/** A person as a tree node or a search hit. */
export interface PersonSummary {
  id: string;
  clanId: string;
  branchId?: string;
  fullName: string;
  alias?: string;
  gender: Gender;
  lifeStatus: LifeStatus;
  birth?: PartialDate;
  death?: PartialDate;
  /** đời. Null when unknown — never 0, and the UI must render the difference. */
  generation?: number;
  birthOrder?: number;
  avatarMediaId?: string;
  linkedUserId?: string;
  foundingAncestor: boolean;
  /** Whether the caller may edit this node. A UI affordance; the server decides again on write. */
  canEdit: boolean;
}

export interface Parentage {
  id: string;
  parentPersonId: string;
  childPersonId: string;
  parentRole: ParentRole;
  nature: ParentageNature;
  unionId?: string;
}

export interface Union {
  id: string;
  partnerAId: string;
  partnerBId: string;
  unionType: UnionType;
  status: UnionStatus;
  startDate?: PartialDate;
  endDate?: PartialDate;
  notes?: string;
}

export interface ParentLink {
  parentageId: string;
  parentRole: ParentRole;
  nature: ParentageNature;
  person: PersonSummary;
}

export interface UnionLink {
  union: Union;
  partner: PersonSummary;
}

export interface PersonDetail extends PersonSummary {
  familyName?: string;
  givenName?: string;
  posthumousName?: string;
  burialPlace?: string;
  notesHtml?: string;
  parents: ParentLink[];
  unions: UnionLink[];
  children: PersonSummary[];
  siblings: PersonSummary[];
  version: number;
  createdAt: string;
  updatedAt: string;
  deletedAt?: string;
}

export interface CreatePersonInput {
  fullName: string;
  familyName?: string;
  givenName?: string;
  alias?: string;
  posthumousName?: string;
  gender?: Gender;
  lifeStatus?: LifeStatus;
  birth?: PartialDate;
  death?: PartialDate;
  burialPlace?: string;
  birthOrder?: number;
  notesHtml?: string;
  foundingAncestor?: boolean;
  placement: Placement;
  parentRole?: ParentRole;
  nature?: ParentageNature;
}

/**
 * The edit body.
 *
 * The same shape serves `PUT` and `PATCH`, and the verb decides what an absent field means: `PUT`
 * clears it, `PATCH` leaves it alone. `version` is not optional — a write without the version the
 * client last read is a write that would silently overwrite somebody (doc 05 §1).
 */
export interface UpdatePersonInput {
  fullName?: string;
  familyName?: string;
  givenName?: string;
  alias?: string;
  posthumousName?: string;
  gender?: Gender;
  lifeStatus?: LifeStatus;
  birth?: PartialDate;
  death?: PartialDate;
  burialPlace?: string;
  birthOrder?: number;
  notesHtml?: string;
  version: number;
}

export interface PersonFilters {
  q?: string;
  generation?: number;
  lifeStatus?: LifeStatus;
  branchId?: string;
  /** True for nodes with an account attached, false for those without. Omit for both. */
  linked?: boolean;
  page?: number;
  size?: number;
}

/** The page envelope of doc 05 §3.2. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function searchPersons(
  clanId: string,
  filters: PersonFilters,
  signal?: AbortSignal,
): Promise<Page<PersonSummary>> {
  return api.get<Page<PersonSummary>>(`/clans/${clanId}/persons`, {
    query: {
      q: filters.q,
      generation: filters.generation,
      lifeStatus: filters.lifeStatus,
      branchId: filters.branchId,
      linked: filters.linked,
      page: filters.page,
      size: filters.size,
    },
    ...(signal ? { signal } : {}),
  });
}

export function getPerson(
  clanId: string,
  personId: string,
  signal?: AbortSignal,
): Promise<PersonDetail> {
  return api.get<PersonDetail>(`/clans/${clanId}/persons/${personId}`, signal ? { signal } : {});
}

export function createPerson(clanId: string, input: CreatePersonInput): Promise<PersonDetail> {
  return api.post<PersonDetail>(`/clans/${clanId}/persons`, input);
}

/** Partial update: an absent field is left as it is. */
export function patchPerson(
  clanId: string,
  personId: string,
  input: UpdatePersonInput,
): Promise<PersonDetail> {
  return api.patch<PersonDetail>(`/clans/${clanId}/persons/${personId}`, input);
}

export function deletePerson(clanId: string, personId: string): Promise<void> {
  return api.del<undefined>(`/clans/${clanId}/persons/${personId}`);
}

export function restorePerson(clanId: string, personId: string): Promise<PersonDetail> {
  return api.post<PersonDetail>(`/clans/${clanId}/persons/${personId}/restore`);
}

/** Nodes removed within the recovery window, most recent first. Administrators only. */
export function listDeletedPersons(clanId: string, signal?: AbortSignal): Promise<PersonSummary[]> {
  return api.get<PersonSummary[]>(`/clans/${clanId}/persons/deleted`, signal ? { signal } : {});
}

export function movePersonToBranch(
  clanId: string,
  personId: string,
  branchId: string,
): Promise<PersonDetail> {
  return api.post<PersonDetail>(`/clans/${clanId}/persons/${personId}/move-branch`, { branchId });
}

// --- relationships (doc 05 §4.8) ---------------------------------------------------------------

export interface AddParentageInput {
  parentPersonId: string;
  childPersonId: string;
  /** Omit to take it from the parent's recorded gender. */
  parentRole?: ParentRole;
  nature?: ParentageNature;
  unionId?: string;
}

export function addParentage(clanId: string, input: AddParentageInput): Promise<Parentage> {
  return api.post<Parentage>(`/clans/${clanId}/parentages`, input);
}

export function removeParentage(clanId: string, parentageId: string): Promise<void> {
  return api.del<undefined>(`/clans/${clanId}/parentages/${parentageId}`);
}

export interface CreateUnionInput {
  partnerAId: string;
  partnerBId: string;
  unionType?: UnionType;
  status?: UnionStatus;
  startDate?: PartialDate;
  endDate?: PartialDate;
  notes?: string;
  /** Records a marriage between two people already related as ancestor and descendant. Admin only. */
  force?: boolean;
}

export function createUnion(clanId: string, input: CreateUnionInput): Promise<Union> {
  return api.post<Union>(`/clans/${clanId}/unions`, input);
}

export interface UpdateUnionInput {
  unionType?: UnionType;
  status?: UnionStatus;
  startDate?: PartialDate;
  endDate?: PartialDate;
  notes?: string;
}

export function updateUnion(
  clanId: string,
  unionId: string,
  input: UpdateUnionInput,
): Promise<Union> {
  return api.patch<Union>(`/clans/${clanId}/unions/${unionId}`, input);
}

export function deleteUnion(clanId: string, unionId: string): Promise<void> {
  return api.del<undefined>(`/clans/${clanId}/unions/${unionId}`);
}

export function attachChildToUnion(
  clanId: string,
  unionId: string,
  personId: string,
): Promise<Parentage[]> {
  return api.post<Parentage[]>(`/clans/${clanId}/unions/${unionId}/children`, { personId });
}
