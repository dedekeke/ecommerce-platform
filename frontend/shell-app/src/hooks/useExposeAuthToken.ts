import { useEffect } from 'react'
import { useAuth0 } from '@auth0/auth0-react'

/**
 * Exposes `window.__getAuthToken` so federated MFEs can acquire the Auth0
 * Bearer token without being coupled to the Auth0 SDK directly.
 *
 * MFE apiClients call `window.__getAuthToken?.()` in their request interceptor.
 * The property is defined as non-writable + non-configurable via Object.defineProperty
 * so that third-party browser extensions or a compromised MFE cannot overwrite it and
 * intercept tokens. The descriptor is re-applied whenever isAuthenticated changes.
 */
export function useExposeAuthToken(): void {
  const { getAccessTokenSilently, isAuthenticated } = useAuth0()

  useEffect(() => {
    const tokenFn = async (): Promise<string | null> => {
      if (!isAuthenticated) return null
      try {
        return await getAccessTokenSilently()
      } catch {
        return null
      }
    }

    // Delete any previously set (possibly configurable) descriptor first so we can
    // redefine. This handles the case where React hot-reload re-runs the effect.
    try {
      delete (window as Window & typeof globalThis).__getAuthToken
    } catch {
      // Property may already be non-configurable on first load in some environments.
    }

    Object.defineProperty(window, '__getAuthToken', {
      value: tokenFn,
      writable: false,
      configurable: true, // Must stay configurable so cleanup can remove it on unmount.
      enumerable: false,
    })

    return () => {
      // Make it deletable on cleanup only if it is still our function.
      const descriptor = Object.getOwnPropertyDescriptor(window, '__getAuthToken')
      if (descriptor?.value === tokenFn) {
        delete (window as Window & typeof globalThis).__getAuthToken
      }
    }
  }, [isAuthenticated, getAccessTokenSilently])
}

export default useExposeAuthToken
