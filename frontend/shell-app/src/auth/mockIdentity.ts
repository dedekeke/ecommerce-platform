/**
 * Mock identity/token for local test auth mode. Imported ONLY by
 * MockAuthProvider.tsx (the DEV-gated dynamically imported chunk) and test
 * files — never add a static import from production code paths, or the
 * strings would ship in real bundles.
 */

/** Deterministic identity used by e2e specs; `sub` is what MFEs read via `window.__getAuthUserId`. */
export const MOCK_AUTH_USER = {
  sub: 'e2e|test-user',
  email: 'e2e-test-user@example.com',
  name: 'E2E Test User',
  email_verified: true,
} as const

/** Static dummy bearer token — deliberately not JWT-shaped so it can never pass real validation. */
export const MOCK_AUTH_TOKEN = 'e2e-mock-token'

/**
 * Installs the standard window accessors with the static mock values.
 *
 * Called eagerly when the mock chunk loads (see MockAuthProvider.tsx):
 * federated MFEs (e.g. checkout-mfe's useAuthUserId) read
 * `window.__getAuthUserId` synchronously on first render, which can happen
 * BEFORE App's useExposeAuthToken passive effect flushes — React schedules
 * passive effects asynchronously, and an already-fetched federation remote can
 * win that race. The identity is static, so eager install is safe;
 * useExposeAuthToken later re-installs equivalent accessors from the mock
 * Auth0 context and manages their lifecycle.
 */
export function installMockWindowAccessors(): void {
  if (typeof window === 'undefined') return
  for (const [name, value] of [
    ['__getAuthUserId', (): string => MOCK_AUTH_USER.sub],
    ['__getAuthToken', async (): Promise<string> => MOCK_AUTH_TOKEN],
  ] as const) {
    try {
      delete (window as Window & typeof globalThis)[name]
    } catch {
      // Property may be non-configurable in exotic environments; defineProperty below will throw visibly.
    }
    Object.defineProperty(window, name, {
      value,
      writable: false,
      configurable: true,
      enumerable: false,
    })
  }
}
