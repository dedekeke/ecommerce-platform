import { Auth0Provider, type AppState } from '@auth0/auth0-react'
import { useNavigate } from 'react-router-dom'
import { type ReactNode, useCallback } from 'react'
import { isMockAuthMode, MockAuthProvider } from '../auth'

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
  if (isMockAuthMode()) {
    return <MockAuthProvider>{children}</MockAuthProvider>
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
