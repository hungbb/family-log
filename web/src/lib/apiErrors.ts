import type { FormInstance } from 'antd';
import type { TFunction } from 'i18next';
import type { ZodError, ZodType } from 'zod';

import { isApiError } from '../api/errors';

/**
 * Turning an API failure into something a person can act on (task F1.9).
 *
 * Two jobs, deliberately separate. Field-level violations belong on the fields they name, where the
 * person is already looking; everything else becomes one message, chosen from the error `code`
 * rather than from the server's `detail` — the interface is Vietnamese by default, and `detail` is
 * developer-facing English prose that has never been translated.
 */

/** Message keys per error code. Anything unlisted falls back to `errors.unexpected`. */
const MESSAGE_KEY_BY_CODE: Record<string, string> = {
  AUTH_REQUIRED: 'errors.authRequired',
  TOKEN_EXPIRED: 'errors.sessionExpired',
  TOKEN_INVALID: 'errors.sessionExpired',
  EMAIL_ALREADY_REGISTERED: 'errors.emailAlreadyRegistered',
  VALIDATION_FAILED: 'errors.validationFailed',
  RATE_LIMITED: 'errors.rateLimited',
  PERMISSION_DENIED: 'errors.permissionDenied',
  NETWORK_ERROR: 'errors.network',
  INTERNAL_ERROR: 'errors.server',

  // ClanOrg. `CLAN_NOT_FOUND` deliberately reads the same whether the clan does not exist or the
  // caller is simply not in it — the server refuses to tell those apart (doc 04 §7), and a client
  // that phrased them differently would give away exactly what the 404 is protecting.
  CLAN_NOT_FOUND: 'errors.clanNotFound',
  BRANCH_NOT_FOUND: 'errors.branchNotFound',
  REQUEST_NOT_FOUND: 'errors.requestNotFound',
  INVALID_CLAN_CODE: 'errors.invalidClanCode',
  DUPLICATE_PENDING_REQUEST: 'errors.duplicatePendingRequest',
  MEMBERSHIP_EXISTS: 'errors.membershipExists',
  VERSION_CONFLICT: 'errors.versionConflict',
  CYCLE_DETECTED: 'errors.cycleDetected',

  // Lineage. INVALID_RELATIONSHIP covers the guards a family meets while transcribing a real book —
  // a marriage between two people the tree already relates, a union that still has children under
  // it — so it names the check rather than saying the request failed.
  PERSON_NOT_FOUND: 'errors.personNotFound',
  INVALID_RELATIONSHIP: 'errors.invalidRelationship',
  DATE_RANGE_INVALID: 'errors.dateRangeInvalid',
  LAST_ADMIN_PROTECTED: 'errors.lastAdminProtected',

  // Authorization. These three are the refusals a person meets while doing something reasonable,
  // so each says what happened rather than that something did (F3.7). `OUT_OF_SCOPE` in particular
  // is not a failure at all — it is the branch isolation working, and the message says so.
  OUT_OF_SCOPE: 'errors.outOfScope',
  PERMISSION_ESCALATION_BLOCKED: 'errors.escalationBlocked',
  GRANT_ALREADY_EXISTS: 'errors.grantAlreadyExists',
  SUBTREE_SCOPE_UNAVAILABLE: 'errors.subtreeUnavailable',

  // Linking. Both conflicts are the same invariant seen from either end — one node per account per
  // clan (I-P4), one account per node (I-K1) — and a person meeting either has done something
  // reasonable, so each says which of the two happened rather than "conflict".
  NODE_ALREADY_LINKED: 'errors.nodeAlreadyLinked',
  USER_ALREADY_LINKED_IN_CLAN: 'errors.userAlreadyLinkedInClan',

  // Platform administration.
  SUPER_ADMIN_PROTECTED: 'errors.superAdminProtected',
  USER_STATUS_CONFLICT: 'errors.userStatusConflict',
  MAIL_NOT_CONFIGURED: 'errors.mailNotConfigured',
  MAIL_DELIVERY_FAILED: 'errors.mailDeliveryFailed',
};

/**
 * One line to show the person, given any thrown value.
 *
 * Takes `unknown` because that is what `catch` and TanStack Query hand over, and narrowing it here
 * means no call site has to.
 */
export function toMessage(error: unknown, t: TFunction): string {
  if (!isApiError(error)) {
    return t('errors.unexpected');
  }
  const key = MESSAGE_KEY_BY_CODE[error.code];
  return key === undefined ? t('errors.unexpected') : t(key);
}

/**
 * Puts a `422`'s field violations onto the matching form fields.
 *
 * The server's field messages are already specific — "Password must be at least 8 characters" —
 * and are shown as they arrive. Translating them would mean a key for every rule the policy has;
 * Phase 7's translation pass decides that, and until then a precise English sentence beats a vague
 * Vietnamese one.
 *
 * @returns whether anything was placed on a field. False means the caller should show a message.
 */
export function applyApiError(error: unknown, form: FormInstance): boolean {
  if (!isApiError(error) || error.errors.length === 0) {
    return false;
  }
  const fields = Object.entries(error.fieldErrors).map(([name, errors]) => ({ name, errors }));
  if (fields.length === 0) {
    return false;
  }
  form.setFields(fields);
  return true;
}

/**
 * Validates with a Zod schema and reports the failures on the form.
 *
 * AntD's `Form` owns the interaction and Zod owns the rules (doc 07 §1). This is the seam between
 * them: it runs the schema over the collected values and turns a `ZodError` into the shape
 * `setFields` wants, translating each message from the key the schema produced.
 *
 * @returns the parsed values, or null when validation failed
 */
export function validateWithSchema<Output>(
  schema: ZodType<Output>,
  values: unknown,
  form: FormInstance,
  t: TFunction,
): Output | null {
  const result = schema.safeParse(values);
  if (result.success) {
    return result.data;
  }
  form.setFields(toFormFields(result.error, t));
  return null;
}

function toFormFields(error: ZodError, t: TFunction): { name: string; errors: string[] }[] {
  const byField = new Map<string, string[]>();
  for (const issue of error.issues) {
    const name = issue.path.length > 0 ? String(issue.path[0]) : 'form';
    const existing = byField.get(name) ?? [];
    existing.push(
      t(issue.message, {
        ...boundOf(issue),
        // The schemas produce keys. If one ever produces something else — a rule added without a
        // message, a Zod upgrade that words an issue differently — the person sees a translated
        // "this is not valid" rather than the library's English sentence.
        defaultValue: t('errors.validationFailed'),
      }),
    );
    byField.set(name, existing);
  }
  return [...byField].map(([name, errors]) => ({ name, errors }));
}

/**
 * The bound a length rule tripped on, as an i18next interpolation value.
 *
 * Lets a message read "at least {{count}} characters" and take the number from the schema itself,
 * so changing `PASSWORD_MIN_LENGTH` cannot leave a translation quietly claiming the old one. Only
 * one of `minimum` and `maximum` is present on any given issue, so they cannot collide.
 *
 * <p>Returns an empty object rather than `undefined` for the issues that carry no bound: under
 * `exactOptionalPropertyTypes` an explicit `undefined` is not the same as an absent argument, and
 * i18next ignores interpolation values a message does not mention anyway.
 */
function boundOf(issue: ZodError['issues'][number]): Record<string, number> {
  if ('minimum' in issue) {
    return { count: Number(issue.minimum) };
  }
  if ('maximum' in issue) {
    return { count: Number(issue.maximum) };
  }
  return {};
}
