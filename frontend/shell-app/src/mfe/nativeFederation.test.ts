import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

const mockInitFederation = vi.fn()

vi.mock('@angular-architects/native-federation-runtime', () => ({
  initFederation: (...args: unknown[]) => mockInitFederation(...args),
}))

import { initNativeFederation, isNativeFederationInitialized, resetNativeFederationState } from './nativeFederation'

describe('nativeFederation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    resetNativeFederationState()
  })

  afterEach(() => {
    resetNativeFederationState()
  })

  describe('initNativeFederation', () => {
    it('should call initFederation with the manifest containing both Angular MFE URLs', async () => {
      mockInitFederation.mockResolvedValue({})

      await initNativeFederation()

      expect(mockInitFederation).toHaveBeenCalledOnce()
      const manifest = mockInitFederation.mock.calls[0][0] as Record<string, string>
      expect(manifest).toHaveProperty('userDashboard')
      expect(manifest).toHaveProperty('adminDashboard')
      expect(manifest['userDashboard']).toContain('remoteEntry.json')
      expect(manifest['adminDashboard']).toContain('remoteEntry.json')
    })

    it('should use env-var URLs when set', async () => {
      mockInitFederation.mockResolvedValue({})
      const origEnv = import.meta.env
      vi.stubEnv('VITE_MFE_USER_DASHBOARD_URL', 'http://custom:9000')
      vi.stubEnv('VITE_MFE_ADMIN_DASHBOARD_URL', 'http://custom:9001')

      resetNativeFederationState()
      await initNativeFederation()

      const manifest = mockInitFederation.mock.calls[0][0] as Record<string, string>
      expect(manifest['userDashboard']).toBe('http://custom:9000/remoteEntry.json')
      expect(manifest['adminDashboard']).toBe('http://custom:9001/remoteEntry.json')

      vi.unstubAllEnvs()
      void origEnv
    })

    it('should not call initFederation a second time if already initialized', async () => {
      mockInitFederation.mockResolvedValue({})

      await initNativeFederation()
      await initNativeFederation()

      expect(mockInitFederation).toHaveBeenCalledOnce()
    })

    it('should return the same promise on concurrent calls', async () => {
      let resolveInit!: () => void
      const pending = new Promise<void>((res) => { resolveInit = res })
      mockInitFederation.mockReturnValue(pending)

      const p1 = initNativeFederation()
      const p2 = initNativeFederation()

      resolveInit()
      await Promise.all([p1, p2])

      expect(mockInitFederation).toHaveBeenCalledOnce()
    })

    it('should throw and reset initialized flag when initFederation rejects', async () => {
      mockInitFederation.mockRejectedValue(new Error('network failure'))

      await expect(initNativeFederation()).rejects.toThrow('network failure')
      expect(isNativeFederationInitialized()).toBe(false)
    })
  })

  describe('isNativeFederationInitialized', () => {
    it('should return false before initialization', () => {
      expect(isNativeFederationInitialized()).toBe(false)
    })

    it('should return true after successful initialization', async () => {
      mockInitFederation.mockResolvedValue({})

      await initNativeFederation()

      expect(isNativeFederationInitialized()).toBe(true)
    })
  })
})
