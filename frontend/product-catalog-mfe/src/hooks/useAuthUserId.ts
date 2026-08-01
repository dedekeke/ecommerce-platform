declare global {
  interface Window {
    /** Exposed by the shell app; returns the authenticated Auth0 user id (sub) or null. */
    __getAuthUserId?: () => string | null
  }
}

/**
 * Resolves the authenticated user id from the shell accessor
 * (`window.__getAuthUserId`, sourced from the Auth0 `sub` claim). Mirrors
 * checkout-mfe's `useAuthUserId` so the review form gates on the same real
 * identity signal instead of the unwired `isAuthenticated` prop.
 *
 * Read on every render (not memoized): the shell installs the accessor
 * asynchronously after Auth0 resolves. Returns null when unauthenticated or
 * the accessor is unavailable (standalone dev) — callers must treat null as
 * "show the sign-in prompt", never substitute a placeholder user id.
 */
export function useAuthUserId(): string | null {
  return window.__getAuthUserId?.() ?? null
}

export default useAuthUserId
