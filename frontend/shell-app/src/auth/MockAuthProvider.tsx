import {
  Auth0Context,
  initialContext,
  type Auth0ContextInterface,
  type IdToken,
  type User,
} from '@auth0/auth0-react'
import { type ReactNode, useEffect, useMemo } from 'react'

/**
 * Deterministic identity used by e2e specs; `sub` is what MFEs read via
 * `window.__getAuthUserId`. Defined HERE (not in mockAuth.ts) so the strings
 * exist only in this DEV-only, dynamically imported module and can never
 * reach a production bundle.
 */
export const MOCK_AUTH_USER = {
  sub: 'e2e|test-user',
  email: 'e2e-test-user@example.com',
  name: 'E2E Test User',
  email_verified: true,
} as const

/** Static dummy bearer token — deliberately not JWT-shaped so it can never pass real validation. */
export const MOCK_AUTH_TOKEN = 'e2e-mock-token'

interface MockAuthProviderProps {
  children: ReactNode
}

/**
 * Drop-in replacement for Auth0Provider in local test auth mode (VITE_AUTH_MODE=mock).
 *
 * Feeds a pre-authenticated, deterministic identity into the SAME Auth0Context that
 * `useAuth0()` reads, so every existing consumer (ProtectedRoute, RoleGuard,
 * useExposeAuthToken, useApiSetup, login/logout buttons) works unchanged and the
 * standard `window.__getAuthToken` / `window.__getAuthUserId` accessors get installed
 * with the mock values. Only ever rendered behind `isMockAuthMode()` — see mockAuth.ts.
 *
 * Methods the shell never calls (popup flows, redirect callback, ...) keep the
 * throwing stubs from `initialContext` on purpose: an unexpected call should fail
 * loudly in tests rather than silently succeed.
 */
export function MockAuthProvider({ children }: MockAuthProviderProps) {
  useEffect(() => {
    console.warn(
      `[shell] MOCK AUTH MODE active — test identity "${MOCK_AUTH_USER.sub}". ` +
        'Dev-server only; production builds are protected by MOCK_AUTH_PRODUCTION_GUARD.'
    )
  }, [])

  const value = useMemo<Auth0ContextInterface<User>>(
    () => ({
      ...initialContext,
      isAuthenticated: true,
      isLoading: false,
      user: { ...MOCK_AUTH_USER },
      error: undefined,
      getAccessTokenSilently: (async () =>
        MOCK_AUTH_TOKEN) as Auth0ContextInterface['getAccessTokenSilently'],
      getIdTokenClaims: async (): Promise<IdToken> => ({
        __raw: MOCK_AUTH_TOKEN,
        sub: MOCK_AUTH_USER.sub,
        email: MOCK_AUTH_USER.email,
        name: MOCK_AUTH_USER.name,
      }),
      loginWithRedirect: async () => {},
      logout: async () => {},
    }),
    []
  )

  return <Auth0Context.Provider value={value}>{children}</Auth0Context.Provider>
}
