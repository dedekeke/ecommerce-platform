import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import { devtools } from 'zustand/middleware'
import type { UserPreferencesState, ThemeMode, Language, Currency } from './types'

const defaultPreferences = {
  theme: 'light' as ThemeMode,
  language: 'en' as Language,
  currency: 'USD' as Currency,
}

export const useUserPreferencesStore = create<UserPreferencesState>()(
  devtools(
    persist(
      (set) => ({
        ...defaultPreferences,

        setTheme: (theme: ThemeMode) =>
          set(
            { theme },
            false,
            'setTheme'
          ),

        setLanguage: (language: Language) =>
          set(
            { language },
            false,
            'setLanguage'
          ),

        setCurrency: (currency: Currency) =>
          set(
            { currency },
            false,
            'setCurrency'
          ),

        resetPreferences: () =>
          set(
            { ...defaultPreferences },
            false,
            'resetPreferences'
          ),
      }),
      {
        name: 'user-preferences-storage',
        storage: createJSONStorage(() => localStorage),
        partialize: (state) => ({
          theme: state.theme,
          language: state.language,
          currency: state.currency,
        }),
      }
    ),
    { name: 'UserPreferencesStore' }
  )
)

// Selectors
export const selectTheme = (state: UserPreferencesState) => state.theme
export const selectLanguage = (state: UserPreferencesState) => state.language
export const selectCurrency = (state: UserPreferencesState) => state.currency
export const selectIsDarkMode = (state: UserPreferencesState) =>
  state.theme === 'dark'
export const selectIsSystemTheme = (state: UserPreferencesState) =>
  state.theme === 'system'
