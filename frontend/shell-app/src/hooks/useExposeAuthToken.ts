import { useEffect } from 'react'
import { useAuth0 } from '@auth0/auth0-react'

/**
 * Dispatched whenever the auth accessors are (re)installed. MFEs mount BEFORE this
 * shell effect runs (child effects fire first), so a deep-linked MFE that snapshots
 * `window.__getAuthUserId` at render would miss it — listening for this event (see
 * cart-mfe useCartServerSyncEnabled) closes that race. Purely additive; the detail is
 * a convenience and listeners must still read the accessor.
 */
export const AUTH_READY_EVENT = 'ecommerce:auth-ready'

/**
 * Exposes `window.__getAuthToken` and `window.__getAuthUserId` so federated MFEs can acquire the
 * Auth0 Bearer token and the authenticated user's stable id (`sub` claim) without being coupled to
 * the Auth0 SDK directly.
 *
 * MFE apiClients call `window.__getAuthToken?.()` in their request interceptor; checkout reads
 * `window.__getAuthUserId?.()` to attach the real user id to a PaymentIntent instead of a hardcoded
 * placeholder. Both descriptors are non-writable so a compromised MFE or browser extension cannot
 * overwrite them, and are re-applied whenever the auth state changes.
 */
export function useExposeAuthToken(): void {
  const { getAccessTokenSilently, isAuthenticated, user } = useAuth0()
  const userId = isAuthenticated ? (user?.sub ?? null) : null

  useEffect(() => {
    const tokenFn = async (): Promise<string | null> => {
      if (!isAuthenticated) return null
      try {
        return await getAccessTokenSilently()
      } catch {
        return null
      }
    }

    const userIdFn = (): string | null => userId

    defineAccessor('__getAuthToken', tokenFn)
    defineAccessor('__getAuthUserId', userIdFn)
    window.dispatchEvent(new CustomEvent(AUTH_READY_EVENT, { detail: { userId } }))

    return () => {
      removeAccessor('__getAuthToken', tokenFn)
      removeAccessor('__getAuthUserId', userIdFn)
    }
  }, [isAuthenticated, getAccessTokenSilently, userId])
}

type AccessorName = '__getAuthToken' | '__getAuthUserId'

/** Re-defines a non-writable accessor on window, replacing any prior (hot-reload safe) descriptor. */
function defineAccessor(name: AccessorName, value: unknown): void {
  try {
    delete (window as Window & typeof globalThis)[name]
  } catch {
    // Property may already be non-configurable on first load in some environments.
  }
  Object.defineProperty(window, name, {
    value,
    writable: false,
    configurable: true, // Must stay configurable so cleanup can remove it on unmount.
    enumerable: false,
  })
}

/** Removes the accessor only if it is still the function we installed. */
function removeAccessor(name: AccessorName, value: unknown): void {
  const descriptor = Object.getOwnPropertyDescriptor(window, name)
  if (descriptor?.value === value) {
    delete (window as Window & typeof globalThis)[name]
  }
}

export default useExposeAuthToken
