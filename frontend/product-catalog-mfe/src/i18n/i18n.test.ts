import { describe, it, expect } from 'vitest'
import en from './locales/en.json'
import vi from './locales/vi.json'
import es from './locales/es.json'
import { SUPPORTED_LANGUAGES } from './i18n'

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

describe('product-catalog-mfe i18n locale resources', () => {
  const enKeys = flatten(en as LocaleObject)
  const viKeys = flatten(vi as LocaleObject)
  const esKeys = flatten(es as LocaleObject)

  it('exposes the three configured languages', () => {
    expect(SUPPORTED_LANGUAGES).toEqual(['en', 'vi', 'es'])
  })

  it('vi has the same keys as en', () => {
    expect(viKeys).toEqual(enKeys)
  })

  it('es has the same keys as en', () => {
    expect(esKeys).toEqual(enKeys)
  })
})
