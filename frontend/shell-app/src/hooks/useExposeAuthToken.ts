import { useEffect } from 'react'
import { useAuth0 } from '@auth0/auth0-react'

/**
 * Exposes `window.__getAuthToken` so federated MFEs can acquire the Auth0
 * Bearer token without being coupled to the Auth0 SDK directly.
 *
 * MFE apiClients call `window.__getAuthToken?.()` in their request interceptor.
 * The function is set once after Auth0 initialises and torn down on unmount.
 */
export function useExposeAuthToken(): void {
  const { getAccessTokenSilently, isAuthenticated } = useAuth0()

  useEffect(() => {
    window.__getAuthToken = async (): Promise<string | null> => {
      if (!isAuthenticated) return null
      try {
        return await getAccessTokenSilently()
      } catch {
        return null
      }
    }

    return () => {
      delete window.__getAuthToken
    }
  }, [isAuthenticated, getAccessTokenSilently])
}

export default useExposeAuthToken
