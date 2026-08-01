import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'
import en from './locales/en.json'
import vi from './locales/vi.json'
import es from './locales/es.json'

/**
 * §3.5 i18n bootstrap. Detection order: localStorage → navigator → 'en'.
 * Uses the same `user-preferences-storage` key as zustand so a stored
 * `language` survives a page reload regardless of which path set it.
 *
 * Federated MFEs share this singleton via the federation `shared` config —
 * see vite.config.ts. If we ever need a per-MFE i18n instance, switch to
 * `createInstance()` and remove the `shared` entry.
 */
export const SUPPORTED_LANGUAGES = ['en', 'vi', 'es'] as const
export type SupportedLanguage = typeof SUPPORTED_LANGUAGES[number]

export const resources = {
  en: { translation: en },
  vi: { translation: vi },
  es: { translation: es },
} as const

if (!i18n.isInitialized) {
  i18n
    .use(LanguageDetector)
    .use(initReactI18next)
    .init({
      resources,
      fallbackLng: 'en',
      supportedLngs: SUPPORTED_LANGUAGES,
      interpolation: { escapeValue: false },
      detection: {
        order: ['localStorage', 'navigator', 'htmlTag'],
        caches: ['localStorage'],
        lookupLocalStorage: 'i18nextLng',
      },
    })
    // Init returns a thenable; we don't block module load on it.
    .catch((err) => console.warn('[i18n] init failed:', err))
}

export default i18n
