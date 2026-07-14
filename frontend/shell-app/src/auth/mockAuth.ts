/**
 * Local test auth mode (`VITE_AUTH_MODE=mock`) — lets the Playwright e2e suite
 * run without Auth0 egress: the shell treats the session as authenticated with
 * a deterministic test identity instead of redirecting to the Auth0 tenant.
 *
 * SECURITY: this mode is dev-server-only, enforced twice:
 *  - runtime (below): only honored when `import.meta.env.DEV` is true — any
 *    `vite build` output has DEV=false, so mock auth can never activate in a
 *    deployed bundle even if the env var leaks into the build environment;
 *  - build time: `mockAuthBuildGuard.ts` fails `vite build` outright when
 *    VITE_AUTH_MODE=mock is set (grep marker: MOCK_AUTH_PRODUCTION_GUARD).
 *
 * Backends still validate Auth0 JWTs unless run with SECURITY_ENABLED=false —
 * see e2e/README.md.
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

export interface MockAuthEnv {
  readonly DEV: boolean
  readonly VITE_AUTH_MODE?: string
}

// RUNTIME SAFETY GUARD: `env.DEV === true` means mock auth is impossible in
// any built (production/preview) bundle regardless of VITE_AUTH_MODE.
export function isMockAuthMode(env: MockAuthEnv = import.meta.env): boolean {
  return env.DEV === true && env.VITE_AUTH_MODE === 'mock'
}
