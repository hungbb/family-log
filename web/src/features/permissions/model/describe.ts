import type { TFunction } from 'i18next';

import { type Grant } from '@/api/endpoints/permissions.ts';

/**
 * One grant, in a sentence a family can argue about (F3.5).
 *
 * "Can edit *Chi Hai* and everything under it" is the line the member drawer shows, and it is
 * doing real work: doc 04 §3.1's isolation guarantee is a rule about path prefixes, and nobody
 * outside this codebase should have to think in those terms to know what they have given somebody.
 * The words "and everything under it" are the whole point — a person who reads "can edit Chi Hai"
 * and does not realise the sub-chi came with it has been misled by their own tools.
 *
 * Vietnamese wording matters here more than anywhere else in the product (F3.8); the translations
 * carry the nuance, this only decides which sentence to ask for.
 */
export function describeGrant(grant: Grant, t: TFunction): string {
  const permission = t(`permissions.name.${grant.permission}`);

  if (grant.scopeType === 'CLAN') {
    return t('permissions.summary.clan', { permission });
  }
  if (grant.scopeType === 'SUBTREE') {
    return t('permissions.summary.subtree', { permission });
  }
  return t('permissions.summary.branch', {
    permission,
    branch: grant.scopeLabel ?? t('permissions.summary.unnamedBranch'),
  });
}

/**
 * Every grant a person holds, as sentences — the drawer's summary block.
 *
 * Sorted so the widest scope reads first: "can administer the whole clan" before "can edit Chi
 * Hai" is the order somebody scanning the list expects, and it puts the answer to "why can they do
 * that?" at the top.
 */
export function describeGrants(grants: Grant[], t: TFunction): string[] {
  const scopeRank: Record<Grant['scopeType'], number> = { CLAN: 0, BRANCH: 1, SUBTREE: 2 };

  return [...grants]
    .sort((left, right) => scopeRank[left.scopeType] - scopeRank[right.scopeType])
    .map((grant) => describeGrant(grant, t));
}
