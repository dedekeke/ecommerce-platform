import { describe, it, expect, vi, afterEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { isFeatureEnabled, useFeatureFlag } from './featureFlags'

/**
 * Mirrors the shell-app's featureFlags.test.ts — same SDK, copied into the
 * MFE so each remote bundle stays self-contained. See `featureFlags.ts` for
 * why we duplicate rather than share.
 */
describe('featureFlags (product-catalog-mfe)', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('should return false when flag env var is not set', () => {
    expect(isFeatureEnabled('UNKNOWN_FLAG')).toBe(false)
  })

  it('should return true for "true" via stubEnv', () => {
    vi.stubEnv('VITE_FEATURE_FLAG_RECOMMENDATIONS', 'true')

    expect(isFeatureEnabled('RECOMMENDATIONS')).toBe(true)
  })

  it('should be case-insensitive', () => {
    vi.stubEnv('VITE_FEATURE_FLAG_X', 'TRUE')
    expect(isFeatureEnabled('X')).toBe(true)
  })

  it('useFeatureFlag should return the current flag value', () => {
    vi.stubEnv('VITE_FEATURE_FLAG_RECOMMENDATIONS', 'true')

    const { result } = renderHook(() => useFeatureFlag('RECOMMENDATIONS'))
    expect(result.current).toBe(true)
  })

  it('useFeatureFlag should return false when flag missing', () => {
    const { result } = renderHook(() => useFeatureFlag('UNKNOWN_X'))
    expect(result.current).toBe(false)
  })
})
