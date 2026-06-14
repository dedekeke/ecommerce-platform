import { useMemo } from 'react'

declare global {
  interface Window {
    /** Exposed by the shell app; returns the authenticated Auth0 user id (sub) or null. */
    __getAuthUserId?: () => string | null
  }
}

/** Prefix for the placeholder id used only when the visitor is genuinely unauthenticated. */
export const ANONYMOUS_USER_PREFIX = 'anonymous-'

/**
 * Resolves the user id to attach to a PaymentIntent.
 *
 * Prefers the authenticated identity exposed by the shell (`window.__getAuthUserId`, sourced from
 * the Auth0 `sub` claim). When the visitor is genuinely unauthenticated — or the shell accessor is
 * unavailable (standalone dev) — it falls back to a clearly-marked, stable anonymous id so the
 * value reaching Stripe metadata is never the misleading hardcoded `'guest'`. In authenticated mode
 * the backend still overrides this with the JWT subject.
 */
export function useAuthUserId(): string {
  return useMemo(() => {
    const authUserId = window.__getAuthUserId?.()
    if (authUserId) return authUserId
    return `${ANONYMOUS_USER_PREFIX}${crypto.randomUUID()}`
  }, [])
}

export default useAuthUserId
