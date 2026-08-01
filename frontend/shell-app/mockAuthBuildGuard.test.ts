import { describe, it, expect, beforeEach, afterAll, vi } from 'vitest'
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import type { ConfigEnv } from 'vite'
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
  const mockEnvDir = mkdtempSync(join(tmpdir(), 'mock-auth-guard-mock-'))
  const cleanEnvDir = mkdtempSync(join(tmpdir(), 'mock-auth-guard-clean-'))
  writeFileSync(join(mockEnvDir, '.env.local'), 'VITE_AUTH_MODE=mock\n')

  function runConfigHook(envDir: string, env: ConfigEnv): void {
    const plugin = mockAuthBuildGuard(envDir)
    const hook = plugin.config as (config: unknown, env: ConfigEnv) => void
    hook.call(undefined, {}, env)
  }

  beforeEach(() => {
    vi.unstubAllEnvs()
    delete process.env.VITE_AUTH_MODE
  })

  afterAll(() => {
    rmSync(mockEnvDir, { recursive: true, force: true })
    rmSync(cleanEnvDir, { recursive: true, force: true })
  })

  it('should expose a config hook under a stable, greppable plugin name', () => {
    const plugin = mockAuthBuildGuard()
    expect(plugin.name).toBe('mock-auth-build-guard')
    expect(plugin.config).toBeTypeOf('function')
  })

  it('should fail a production build when VITE_AUTH_MODE=mock comes from a .env file (loadEnv path)', () => {
    expect(() => runConfigHook(mockEnvDir, { command: 'build', mode: 'production' })).toThrow(
      /MOCK_AUTH_PRODUCTION_GUARD/
    )
  })

  it('should fail a production build when VITE_AUTH_MODE=mock comes from the process environment', () => {
    vi.stubEnv('VITE_AUTH_MODE', 'mock')
    expect(() => runConfigHook(cleanEnvDir, { command: 'build', mode: 'production' })).toThrow(
      /MOCK_AUTH_PRODUCTION_GUARD/
    )
  })

  it('should allow the dev server even with the mock .env file present', () => {
    expect(() => runConfigHook(mockEnvDir, { command: 'serve', mode: 'development' })).not.toThrow()
  })

  it('should allow a production build when no .env file or env var sets mock', () => {
    expect(() => runConfigHook(cleanEnvDir, { command: 'build', mode: 'production' })).not.toThrow()
  })
})
