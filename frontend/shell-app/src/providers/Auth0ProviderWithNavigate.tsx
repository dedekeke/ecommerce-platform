import { Auth0Provider, type AppState } from '@auth0/auth0-react'
import { useNavigate } from 'react-router-dom'
import { lazy, Suspense, type ReactNode, useCallback } from 'react'
import { isMockAuthMode } from '../auth'

// DEV-gated dynamic import: `import.meta.env.DEV` is statically replaced at
// build time, so this ternary folds to `null` in production and the mock-auth
// chunk (component + identity/token strings) never enters the Rollup build
// graph. Belt-and-braces with MOCK_AUTH_PRODUCTION_GUARD.
const DevMockAuthGate = import.meta.env.DEV
  ? lazy(() =>
      import('../auth/MockAuthProvider').then((m) => ({ default: m.MockAuthProvider }))
    )
  : null

interface Auth0ProviderWithNavigateProps {
  children: ReactNode
}

export const Auth0ProviderWithNavigate = ({ children }: Auth0ProviderWithNavigateProps) => {
  const navigate = useNavigate()

  const domain = import.meta.env.VITE_AUTH0_DOMAIN
  const clientId = import.meta.env.VITE_AUTH0_CLIENT_ID
  const audience = import.meta.env.VITE_AUTH0_AUDIENCE
  const redirectUri = import.meta.env.VITE_AUTH0_REDIRECT_URI || window.location.origin

  const onRedirectCallback = useCallback(
    (appState?: AppState) => {
      navigate(appState?.returnTo || window.location.pathname)
    },
    [navigate]
  )

  // Local test auth mode (VITE_AUTH_MODE=mock, dev server only): bypass Auth0
  // entirely with a deterministic test identity. See src/auth/mockAuth.ts for
  // the runtime + build-time safety guards keeping this out of production.
  // Leading `import.meta.env.DEV` makes the whole branch statically dead in
  // builds; dev-only path, so a brief Suspense fallback while the chunk loads is fine.
  if (import.meta.env.DEV && DevMockAuthGate && isMockAuthMode()) {
    return (
      <Suspense fallback={null}>
        <DevMockAuthGate>{children}</DevMockAuthGate>
      </Suspense>
    )
  }

  if (!domain || !clientId) {
    console.error('Auth0 configuration is missing. Please check your environment variables.')
    return <>{children}</>
  }

  return (
    <Auth0Provider
      domain={domain}
      clientId={clientId}
      authorizationParams={{
        redirect_uri: redirectUri,
        audience: audience,
      }}
      onRedirectCallback={onRedirectCallback}
      cacheLocation="memory"
      useRefreshTokens={true}
    >
      {children}
    </Auth0Provider>
  )
}
