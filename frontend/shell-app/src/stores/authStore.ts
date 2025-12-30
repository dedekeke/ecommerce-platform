import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import { devtools } from 'zustand/middleware'
import type { AuthState, User } from './types'

const initialState = {
  token: null as string | null,
  user: null as User | null,
  isAuthenticated: false,
}

export const useAuthStore = create<AuthState>()(
  devtools(
    persist(
      (set) => ({
        ...initialState,

        setAuth: (token: string, user: User) =>
          set(
            {
              token,
              user,
              isAuthenticated: true,
            },
            false,
            'setAuth'
          ),

        clearAuth: () =>
          set(
            {
              ...initialState,
            },
            false,
            'clearAuth'
          ),
      }),
      {
        name: 'auth-storage',
        storage: createJSONStorage(() => sessionStorage),
        partialize: (state) => ({
          token: state.token,
          user: state.user,
          isAuthenticated: state.isAuthenticated,
        }),
      }
    ),
    { name: 'AuthStore' }
  )
)

// Selectors
export const selectToken = (state: AuthState) => state.token
export const selectUser = (state: AuthState) => state.user
export const selectIsAuthenticated = (state: AuthState) => state.isAuthenticated
export const selectUserEmail = (state: AuthState) => state.user?.email
export const selectUserName = (state: AuthState) => state.user?.name
