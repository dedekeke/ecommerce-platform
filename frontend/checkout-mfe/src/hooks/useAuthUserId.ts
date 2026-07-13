import { useMemo } from 'react'

declare global {
  interface Window {
    /** Exposed by the shell app; returns the authenticated Auth0 user id (sub) or null. */
    __getAuthUserId?: () => string | null
  }
}

/**
 * Resolves the authenticated user id for checkout from the shell accessor
 * (`window.__getAuthUserId`, sourced from the Auth0 `sub` claim).
 *
 * Returns null when the visitor is unauthenticated or the accessor is unavailable (standalone
 * dev). The shell already gates /checkout behind auth, so callers must treat null as "cannot
 * place a real order" and block the flow — never substitute a placeholder id.
 */
export function useAuthUserId(): string | null {
  return useMemo(() => window.__getAuthUserId?.() ?? null, [])
}

export default useAuthUserId
