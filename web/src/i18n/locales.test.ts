import { describe, expect, it } from 'vitest';

import en from './locales/en/common.json';
import vi from './locales/vi/common.json';

interface Messages {
  [key: string]: string | Messages;
}

/** `{ a: { b: 'x' } }` → `Map { 'a.b' → 'x' }`. */
function flatten(messages: Messages, prefix = ''): Map<string, string> {
  const flat = new Map<string, string>();
  for (const [key, value] of Object.entries(messages)) {
    const path = prefix === '' ? key : `${prefix}.${key}`;
    if (typeof value === 'string') {
      flat.set(path, value);
    } else {
      for (const [inner, text] of flatten(value, path)) flat.set(inner, text);
    }
  }
  return flat;
}

/**
 * A key without its plural suffix.
 *
 * English has two plural forms and Vietnamese has one, so `memberCount` / `memberCount_other` in
 * `en` against a bare `memberCount` in `vi` is the two files agreeing, not disagreeing. What has to
 * match is the message, not how many forms each language needs to say it.
 */
function base(key: string): string {
  return key.replace(/_(zero|one|two|few|many|other)$/, '');
}

/** The `{{name}}` placeholders of one message, sorted. */
function placeholders(message: string): string[] {
  return [...message.matchAll(/\{\{\s*([\w.]+)\s*\}\}/g)].map((match) => match[1] ?? '').sort();
}

/** Every placeholder any plural form of a message uses, by base key. */
function placeholdersByMessage(flat: Map<string, string>): Map<string, Set<string>> {
  const byMessage = new Map<string, Set<string>>();
  for (const [key, text] of flat) {
    const names = byMessage.get(base(key)) ?? new Set<string>();
    for (const name of placeholders(text)) names.add(name);
    byMessage.set(base(key), names);
  }
  return byMessage;
}

const vietnamese = flatten(vi);
const english = flatten(en);

/**
 * Vietnamese is the reference locale and English is its translation (NFR-8). The two files are
 * edited by hand, in different commits, and nothing else compares them: a message added to one and
 * not the other renders as the Vietnamese fallback in an English interface — or, the other way
 * round, as a raw key.
 */
describe('the locale files', () => {
  it('define the same messages in vi and en', () => {
    const inVietnamese = new Set([...vietnamese.keys()].map(base));
    const inEnglish = new Set([...english.keys()].map(base));

    expect([...inVietnamese].filter((key) => !inEnglish.has(key))).toEqual([]);
    expect([...inEnglish].filter((key) => !inVietnamese.has(key))).toEqual([]);
  });

  it('interpolate the same values in both languages', () => {
    const inVietnamese = placeholdersByMessage(vietnamese);
    const inEnglish = placeholdersByMessage(english);

    const mismatched = [...inVietnamese.keys()].filter(
      (key) =>
        [...(inVietnamese.get(key) ?? [])].sort().join() !==
        [...(inEnglish.get(key) ?? [])].sort().join(),
    );

    expect(mismatched).toEqual([]);
  });
});
