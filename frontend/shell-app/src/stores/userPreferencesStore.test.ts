import { describe, it, expect, beforeEach } from 'vitest'
import { useUserPreferencesStore } from './userPreferencesStore'

describe('userPreferencesStore', () => {
  beforeEach(() => {
    useUserPreferencesStore.getState().resetPreferences()
  })

  describe('initial state', () => {
    it('should have light theme by default', () => {
      const { theme } = useUserPreferencesStore.getState()
      expect(theme).toBe('light')
    })

    it('should have English language by default', () => {
      const { language } = useUserPreferencesStore.getState()
      expect(language).toBe('en')
    })

    it('should have USD currency by default', () => {
      const { currency } = useUserPreferencesStore.getState()
      expect(currency).toBe('USD')
    })
  })

  describe('setTheme', () => {
    it('should set theme to dark', () => {
      useUserPreferencesStore.getState().setTheme('dark')

      const { theme } = useUserPreferencesStore.getState()
      expect(theme).toBe('dark')
    })

    it('should set theme to system', () => {
      useUserPreferencesStore.getState().setTheme('system')

      const { theme } = useUserPreferencesStore.getState()
      expect(theme).toBe('system')
    })

    it('should set theme to light', () => {
      useUserPreferencesStore.getState().setTheme('dark')
      useUserPreferencesStore.getState().setTheme('light')

      const { theme } = useUserPreferencesStore.getState()
      expect(theme).toBe('light')
    })
  })

  describe('setLanguage', () => {
    it('should set language to Spanish', () => {
      useUserPreferencesStore.getState().setLanguage('es')

      const { language } = useUserPreferencesStore.getState()
      expect(language).toBe('es')
    })

    it('should set language to French', () => {
      useUserPreferencesStore.getState().setLanguage('fr')

      const { language } = useUserPreferencesStore.getState()
      expect(language).toBe('fr')
    })

    it('should set language to German', () => {
      useUserPreferencesStore.getState().setLanguage('de')

      const { language } = useUserPreferencesStore.getState()
      expect(language).toBe('de')
    })
  })

  describe('setCurrency', () => {
    it('should set currency to EUR', () => {
      useUserPreferencesStore.getState().setCurrency('EUR')

      const { currency } = useUserPreferencesStore.getState()
      expect(currency).toBe('EUR')
    })

    it('should set currency to GBP', () => {
      useUserPreferencesStore.getState().setCurrency('GBP')

      const { currency } = useUserPreferencesStore.getState()
      expect(currency).toBe('GBP')
    })
  })

  describe('resetPreferences', () => {
    it('should reset all preferences to defaults', () => {
      useUserPreferencesStore.getState().setTheme('dark')
      useUserPreferencesStore.getState().setLanguage('es')
      useUserPreferencesStore.getState().setCurrency('EUR')

      useUserPreferencesStore.getState().resetPreferences()

      const { theme, language, currency } = useUserPreferencesStore.getState()
      expect(theme).toBe('light')
      expect(language).toBe('en')
      expect(currency).toBe('USD')
    })
  })
})
