import { describe, it, expect } from 'vitest'
import en from './locales/en.json'
import vi from './locales/vi.json'
import es from './locales/es.json'
import i18n, { SUPPORTED_LANGUAGES } from './i18n'

type LocaleObject = Record<string, unknown>

function flatten(obj: LocaleObject, prefix = ''): string[] {
  const keys: string[] = []
  for (const [k, v] of Object.entries(obj)) {
    const path = prefix ? `${prefix}.${k}` : k
    if (v !== null && typeof v === 'object' && !Array.isArray(v)) {
      keys.push(...flatten(v as LocaleObject, path))
    } else {
      keys.push(path)
    }
  }
  return keys.sort()
}

describe('i18n locale resources', () => {
  const enKeys = flatten(en as LocaleObject)
  const viKeys = flatten(vi as LocaleObject)
  const esKeys = flatten(es as LocaleObject)

  it('exposes the three configured languages', () => {
    expect(SUPPORTED_LANGUAGES).toEqual(['en', 'vi', 'es'])
  })

  it('vi has the same keys as en (no missing translations)', () => {
    expect(viKeys).toEqual(enKeys)
  })

  it('es has the same keys as en (no missing translations)', () => {
    expect(esKeys).toEqual(enKeys)
  })

  it('translates the home title across all locales', async () => {
    await i18n.changeLanguage('en')
    expect(i18n.t('home.title')).toBe('Welcome to E-Commerce')
    await i18n.changeLanguage('vi')
    expect(i18n.t('home.title')).toContain('E-Commerce')
    await i18n.changeLanguage('es')
    expect(i18n.t('home.title')).toBe('Bienvenido a E-Commerce')
    await i18n.changeLanguage('en')
  })
})
