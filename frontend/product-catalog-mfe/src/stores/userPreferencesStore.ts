import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'

// Mirrors shell-app's userPreferencesStore — only the currency slice is
// surfaced because that's all this MFE needs (§3.5). Federated zustand
// share-scope resolves both to the same singleton at runtime; this mirror
// kicks in only when the MFE runs standalone in dev.
export type Currency = 'USD' | 'EUR' | 'GBP' | 'JPY' | 'VND' | 'CAD'

interface UserPreferencesState {
  currency: Currency
  setCurrency: (currency: Currency) => void
}

export const useUserPreferencesStore = create<UserPreferencesState>()(
  persist(
    (set) => ({
      currency: 'USD',
      setCurrency: (currency) => set({ currency }),
    }),
    {
      name: 'user-preferences-storage',
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({ currency: state.currency }),
    }
  )
)

export const selectCurrency = (state: UserPreferencesState) => state.currency
