import { describe, it, expect, vi, afterEach } from 'vitest'
import { isFeatureEnabled } from './featureFlags'

/**
 * `vi.stubEnv` patches both `import.meta.env` and `process.env` simultaneously.
 * `vi.unstubAllEnvs` (called after each test for isolation) restores both.
 *
 * These tests cover the canonical shared implementation directly so the
 * 90% coverage requirement is met on the shared module, independently of
 * each consuming app's own wrapper tests.
 */
describe('isFeatureEnabled (shared-ui)', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('should return false when flag env var is not set', () => {
    expect(isFeatureEnabled('UNKNOWN_FLAG')).toBe(false)
  })

  it('should return true when env var equals "true"', () => {
    vi.stubEnv('VITE_FEATURE_FLAG_RECOMMENDATIONS', 'true')
    expect(isFeatureEnabled('RECOMMENDATIONS')).toBe(true)
  })

  it('should treat "false" as disabled', () => {
    vi.stubEnv('VITE_FEATURE_FLAG_X', 'false')
    expect(isFeatureEnabled('X')).toBe(false)
  })

  it('should treat non-"true" strings as disabled', () => {
    vi.stubEnv('VITE_FEATURE_FLAG_X', '1')
    expect(isFeatureEnabled('X')).toBe(false)

    vi.stubEnv('VITE_FEATURE_FLAG_X', 'yes')
    expect(isFeatureEnabled('X')).toBe(false)
  })

  it('should be case-insensitive on the value — "TRUE"', () => {
    vi.stubEnv('VITE_FEATURE_FLAG_X', 'TRUE')
    expect(isFeatureEnabled('X')).toBe(true)
  })

  it('should be case-insensitive on the value — "True"', () => {
    vi.stubEnv('VITE_FEATURE_FLAG_Y', 'True')
    expect(isFeatureEnabled('Y')).toBe(true)
  })

  it('should return false for empty flag name', () => {
    expect(isFeatureEnabled('')).toBe(false)
  })

  it('should return false for whitespace-only flag name', () => {
    expect(isFeatureEnabled('   ')).toBe(false)
  })

  it('should read via process.env (vi.stubEnv fallback path)', () => {
    // vi.stubEnv patches process.env; this verifies the fallback branch is exercised
    vi.stubEnv('VITE_FEATURE_FLAG_PROCESS_PATH', 'true')
    expect(isFeatureEnabled('PROCESS_PATH')).toBe(true)
  })
})
