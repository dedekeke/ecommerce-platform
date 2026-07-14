import { describe, it, expect } from 'vitest'
import { assertMockAuthNotInBuild, mockAuthBuildGuard } from './mockAuthBuildGuard'

describe('assertMockAuthNotInBuild', () => {
  it('should throw when building for production with VITE_AUTH_MODE=mock', () => {
    expect(() => assertMockAuthNotInBuild('build', 'production', 'mock')).toThrow(
      /MOCK_AUTH_PRODUCTION_GUARD/
    )
  })

  it('should throw for ANY build mode with VITE_AUTH_MODE=mock (staging, test, ...)', () => {
    expect(() => assertMockAuthNotInBuild('build', 'staging', 'mock')).toThrow(
      /MOCK_AUTH_PRODUCTION_GUARD/
    )
  })

  it('should allow the dev server (serve) with VITE_AUTH_MODE=mock', () => {
    expect(() => assertMockAuthNotInBuild('serve', 'development', 'mock')).not.toThrow()
  })

  it('should allow production builds when VITE_AUTH_MODE is unset or non-mock', () => {
    expect(() => assertMockAuthNotInBuild('build', 'production', undefined)).not.toThrow()
    expect(() => assertMockAuthNotInBuild('build', 'production', 'auth0')).not.toThrow()
  })
})

describe('mockAuthBuildGuard vite plugin', () => {
  it('should expose a config hook under a stable, greppable plugin name', () => {
    const plugin = mockAuthBuildGuard()
    expect(plugin.name).toBe('mock-auth-build-guard')
    expect(plugin.config).toBeTypeOf('function')
  })
})
