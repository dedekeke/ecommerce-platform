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
 * The MockAuthProvider component AND the mock identity/token strings live in
 * `MockAuthProvider.tsx`, reached only through a DEV-gated dynamic import in
 * Auth0ProviderWithNavigate — production build graphs exclude that chunk
 * entirely, so neither the code nor the strings ship.
 *
 * Backends still validate Auth0 JWTs unless run with SECURITY_ENABLED=false —
 * see e2e/README.md.
 */

export interface MockAuthEnv {
  readonly DEV: boolean
  readonly VITE_AUTH_MODE?: string
}

// RUNTIME SAFETY GUARD: `env.DEV === true` means mock auth is impossible in
// any built (production/preview) bundle regardless of VITE_AUTH_MODE.
export function isMockAuthMode(env: MockAuthEnv = import.meta.env): boolean {
  return env.DEV === true && env.VITE_AUTH_MODE === 'mock'
}
