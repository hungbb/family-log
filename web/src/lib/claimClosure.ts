import type { TFunction } from 'i18next';

/**
 * Why the system declined a claim, as a sentence in the reader's language.
 *
 * When a member is linked to an entry, the claims that link makes impossible are declined by the
 * server: other claims on that entry (`NODE_TAKEN`) and the member's own claims on other entries
 * (`REQUESTER_PLACED`). The server sends the code and never a phrase, so a Vietnamese reader is not
 * told in English why their request was declined.
 *
 * Shared by the claims feature and the notification bell, which lives in the shell and may not import
 * a feature (doc 07 §2). An unknown code, from a newer server, gives an empty string so the caller
 * falls back to whatever free-text reason it has rather than rendering a key.
 */
export function closureText(code: string | undefined, t: TFunction): string {
  if (code === undefined || code === '') return '';
  return t(`claims.closure.${code}`, { defaultValue: '' });
}

/** The reason to show for a decided claim: the system's code, worded, or what the approver wrote. */
export function declineReason(
  closureCode: string | undefined,
  decisionReason: string | undefined,
  t: TFunction,
): string {
  const worded = closureText(closureCode, t);
  return worded !== '' ? worded : (decisionReason ?? '');
}
