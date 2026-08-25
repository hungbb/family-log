import i18n from 'i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import { initReactI18next } from 'react-i18next';

import en from './locales/en/common.json';
import vi from './locales/vi/common.json';

/**
 * Vietnamese is the default and the reference locale (NFR-8): it is what the product is for, and
 * `en` is the translation, not the other way round. `vi` is therefore also the fallback — an
 * untranslated key should surface Vietnamese, never an English string in a Vietnamese interface.
 */
export const DEFAULT_LOCALE = 'vi';
export const SUPPORTED_LOCALES = ['vi', 'en'] as const;
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number];

export const resources = {
  vi: { common: vi },
  en: { common: en },
} as const;

void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: DEFAULT_LOCALE,
    supportedLngs: [...SUPPORTED_LOCALES],
    defaultNS: 'common',
    ns: ['common'],
    detection: {
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: 'giaky.locale',
      caches: ['localStorage'],
    },
    interpolation: {
      // React escapes for us; doing it twice mangles Vietnamese punctuation and any HTML entity.
      escapeValue: false,
    },
    returnNull: false,
  });

export default i18n;
