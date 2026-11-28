import type { TFunction } from 'i18next';
import { describe, expect, it } from 'vitest';

import en from '@/i18n/locales/en/common.json';
import vi from '@/i18n/locales/vi/common.json';

import { declineReason } from './claimClosure';

/** A `t` over one locale file, with i18next's `defaultValue` behaviour for missing keys. */
function tOver(resources: object): TFunction {
  return ((key: string, options?: { defaultValue?: string }) => {
    const found = key
      .split('.')
      .reduce<unknown>(
        (node, part) =>
          node !== null && typeof node === 'object'
            ? (node as Record<string, unknown>)[part]
            : undefined,
        resources,
      );
    return typeof found === 'string' ? found : (options?.defaultValue ?? key);
  }) as unknown as TFunction;
}

describe('declineReason', () => {
  it.each(['NODE_TAKEN', 'REQUESTER_PLACED'])('words %s in both languages', (code) => {
    const english = declineReason(code, undefined, tOver(en));
    const vietnamese = declineReason(code, undefined, tOver(vi));

    expect(english).not.toBe('');
    expect(vietnamese).not.toBe('');
    expect(vietnamese).not.toBe(english);
  });

  it("shows what the approver wrote when the system didn't decline it", () => {
    expect(declineReason(undefined, 'Đây là bác Hai', tOver(en))).toBe('Đây là bác Hai');
  });

  it('falls back to the free-text reason for a code this client does not know', () => {
    expect(declineReason('SOMETHING_NEWER', 'Kept', tOver(en))).toBe('Kept');
    expect(declineReason('SOMETHING_NEWER', undefined, tOver(en))).toBe('');
  });
});
