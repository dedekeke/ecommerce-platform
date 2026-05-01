import { describe, it, expect, vi, afterEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { isFeatureEnabled, useFeatureFlag } from './featureFlags'

/**
 * `vi.stubEnv` patches `import.meta.env` and `process.env` simultaneously.
 * `vi.unstubAllEnvs` (called automatically when the test file finishes
 * running, but we call it after each test for isolation) restores both.
 *
 * Note: Vitest 4 freezes `import.meta.env` keys that don't already exist
 * in the build's defaults — we sidestep that by using `stubEnv`, which goes
 * through Vitest's documented stub registry rather than mutating the env
 * object directly.
 */
describe('featureFlags', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  describe('isFeatureEnabled', () => {
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

    it('should treat numeric/string values other than "true" as disabled', () => {
      vi.stubEnv('VITE_FEATURE_FLAG_X', '1')
      expect(isFeatureEnabled('X')).toBe(false)

      vi.stubEnv('VITE_FEATURE_FLAG_X', 'yes')
      expect(isFeatureEnabled('X')).toBe(false)
    })

    it('should be case-insensitive on the value', () => {
      vi.stubEnv('VITE_FEATURE_FLAG_X', 'TRUE')
      expect(isFeatureEnabled('X')).toBe(true)
    })

    it('should be case-insensitive on the value (mixed case)', () => {
      vi.stubEnv('VITE_FEATURE_FLAG_Y', 'True')
      expect(isFeatureEnabled('Y')).toBe(true)
    })

    it('should return false for blank/empty flag names', () => {
      expect(isFeatureEnabled('')).toBe(false)
      expect(isFeatureEnabled('   ')).toBe(false)
    })
  })

  describe('useFeatureFlag', () => {
    it('should return true when the underlying flag is enabled', () => {
      vi.stubEnv('VITE_FEATURE_FLAG_RECOMMENDATIONS', 'true')

      const { result } = renderHook(() => useFeatureFlag('RECOMMENDATIONS'))

      expect(result.current).toBe(true)
    })

    it('should return false when the underlying flag is missing', () => {
      const { result } = renderHook(() => useFeatureFlag('SOMETHING_NEW'))

      expect(result.current).toBe(false)
    })

    it('should return a stable value across re-renders', () => {
      vi.stubEnv('VITE_FEATURE_FLAG_STABLE_TEST', 'true')

      const { result, rerender } = renderHook(() => useFeatureFlag('STABLE_TEST'))
      const first = result.current
      rerender()
      expect(result.current).toBe(first)
      expect(first).toBe(true)
    })
  })
})
