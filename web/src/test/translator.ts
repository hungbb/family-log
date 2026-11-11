import { createInstance, type TFunction } from 'i18next';

import en from '@/i18n/locales/en/common.json';
import vi from '@/i18n/locales/vi/common.json';

/**
 * A real `t`, over the real locale files, for unit tests.
 *
 * A test that stubbed `t` with the identity function would assert that a formatter asks for the
 * right key and nothing about what a person reads. What these tests are for is the sentence — that
 * "khoảng 1890" comes out as "khoảng 1890" — so they translate with the same resources the
 * application ships, and a key renamed in one place and not the other fails here.
 *
 * A private instance rather than the application's: that one is wired to the browser's language
 * detector and to React, neither of which exists in a Node test — which is also why this does not
 * import `@/i18n` at all, even for its constants: loading that module initializes the singleton.
 */
export function translator(locale: 'vi' | 'en' = 'vi'): TFunction {
  const instance = createInstance();
  // Synchronous because the resources are in memory; nothing here waits on a backend.
  void instance.init({
    lng: locale,
    // Vietnamese is the reference locale and the fallback, as in the application (NFR-8).
    fallbackLng: 'vi',
    defaultNS: 'common',
    ns: ['common'],
    resources: { vi: { common: vi }, en: { common: en } },
    interpolation: { escapeValue: false },
    returnNull: false,
    initAsync: false,
  });
  return instance.t;
}
