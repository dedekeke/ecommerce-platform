import { loadEnv, type ConfigEnv, type Plugin } from 'vite'

/**
 * MOCK_AUTH_PRODUCTION_GUARD (CI-greppable marker)
 *
 * Build-time half of the mock-auth safety guard: fails ANY `vite build`
 * (production, staging, or otherwise) when VITE_AUTH_MODE=mock is set, from
 * either the process environment or a .env file. The runtime half lives in
 * src/auth/mockAuth.ts (mock mode requires import.meta.env.DEV).
 */
export function assertMockAuthNotInBuild(
  command: ConfigEnv['command'],
  mode: string,
  authMode: string | undefined
): void {
  if (command === 'build' && authMode === 'mock') {
    throw new Error(
      `MOCK_AUTH_PRODUCTION_GUARD: VITE_AUTH_MODE=mock is forbidden in "vite build" (mode "${mode}"). ` +
        'Mock auth is a dev-server-only test facility — unset VITE_AUTH_MODE to build.'
    )
  }
}

export function mockAuthBuildGuard(): Plugin {
  return {
    name: 'mock-auth-build-guard',
    config(_config, env) {
      const fileEnv = loadEnv(env.mode, process.cwd(), 'VITE_')
      assertMockAuthNotInBuild(
        env.command,
        env.mode,
        process.env.VITE_AUTH_MODE ?? fileEnv.VITE_AUTH_MODE
      )
    },
  }
}
