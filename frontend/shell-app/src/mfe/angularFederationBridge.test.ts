import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  loadAngularRemoteModule,
  waitForShim,
  clearAngularBridgeCache,
} from './angularFederationBridge'

const REMOTE_ENTRY_URL = 'http://localhost:5004/remoteEntry.json'
const EXPOSED_MODULE_URL = 'http://localhost:5004/chunks/user-dashboard.js'

const mockRemoteEntry = {
  exposes: {
    './UserDashboard': EXPOSED_MODULE_URL,
  },
  imports: {
    '@angular/core': 'http://localhost:5004/chunks/angular-core.js',
  },
  scopes: {},
}

const mockAddImportMap = vi.fn()
const mockImportShim = vi.fn().mockResolvedValue({ bootstrap: vi.fn() })

describe('angularFederationBridge', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    clearAngularBridgeCache()

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockRemoteEntry),
    } as Response)

    // Simulate es-module-shims being present
    Object.assign(mockImportShim, { addImportMap: mockAddImportMap })
    ;(globalThis as Record<string, unknown>)['importShim'] = mockImportShim
  })

  afterEach(() => {
    delete (globalThis as Record<string, unknown>)['importShim']
    vi.restoreAllMocks()
  })

  it('should fetch the remoteEntry.json from the given URL', async () => {
    await loadAngularRemoteModule(REMOTE_ENTRY_URL, './UserDashboard')

    expect(globalThis.fetch).toHaveBeenCalledWith(REMOTE_ENTRY_URL)
  })

  it('should inject the import map via es-module-shims addImportMap', async () => {
    await loadAngularRemoteModule(REMOTE_ENTRY_URL, './UserDashboard')

    expect(mockAddImportMap).toHaveBeenCalledWith({
      imports: mockRemoteEntry.imports,
      scopes: mockRemoteEntry.scopes,
    })
  })

  it('should call importShim with the exposed module URL', async () => {
    await loadAngularRemoteModule(REMOTE_ENTRY_URL, './UserDashboard')

    expect(mockImportShim).toHaveBeenCalledWith(EXPOSED_MODULE_URL)
  })

  it('should deduplicate concurrent fetches — only one network request for simultaneous calls', async () => {
    // Fire two concurrent calls before the first fetch resolves
    const [a, b] = await Promise.all([
      loadAngularRemoteModule(REMOTE_ENTRY_URL, './UserDashboard'),
      loadAngularRemoteModule(REMOTE_ENTRY_URL, './UserDashboard'),
    ])

    expect(globalThis.fetch).toHaveBeenCalledTimes(1)
    expect(a).toBe(b)
  })

  it('should cache the remoteEntry.json and not re-fetch on sequential repeated calls', async () => {
    await loadAngularRemoteModule(REMOTE_ENTRY_URL, './UserDashboard')
    await loadAngularRemoteModule(REMOTE_ENTRY_URL, './UserDashboard')

    expect(globalThis.fetch).toHaveBeenCalledTimes(1)
  })

  it('should throw when the exposed module key is not in the remote entry', async () => {
    await expect(
      loadAngularRemoteModule(REMOTE_ENTRY_URL, './NonExistent')
    ).rejects.toThrow('not found in')
  })

  it('should throw when fetch returns a non-ok response', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
    } as Response)

    await expect(
      loadAngularRemoteModule(REMOTE_ENTRY_URL, './UserDashboard')
    ).rejects.toThrow('HTTP 404')
  })

  it('should warn and continue when es-module-shims is not available', async () => {
    delete (globalThis as Record<string, unknown>)['importShim']
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})

    // Pass a very short shim timeout so waitForShim rejects quickly, then the
    // bridge warns and falls back — the subsequent import() may fail too; both are OK.
    await loadAngularRemoteModule(REMOTE_ENTRY_URL, './UserDashboard', 50).catch(() => {})

    expect(warnSpy).toHaveBeenCalled()
  }, 2000)

  it('should clear the cache on clearAngularBridgeCache()', async () => {
    await loadAngularRemoteModule(REMOTE_ENTRY_URL, './UserDashboard')
    clearAngularBridgeCache()
    await loadAngularRemoteModule(REMOTE_ENTRY_URL, './UserDashboard')

    expect(globalThis.fetch).toHaveBeenCalledTimes(2)
  })

  describe('URL scheme validation', () => {
    it('should reject a remoteEntryUrl that is not http(s)://', async () => {
      await expect(
        loadAngularRemoteModule('file:///etc/remoteEntry.json', './UserDashboard')
      ).rejects.toThrow('must be an http(s):// URL')
    })

    it('should reject a relative remoteEntryUrl', async () => {
      await expect(
        loadAngularRemoteModule('/remoteEntry.json', './UserDashboard')
      ).rejects.toThrow('must be an http(s):// URL')
    })

    it('should reject a non-https exposedUrl returned by the remote entry', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: () =>
          Promise.resolve({
            exposes: { './UserDashboard': 'data:text/javascript,malicious' },
            imports: {},
            scopes: {},
          }),
      } as Response)

      await expect(
        loadAngularRemoteModule(REMOTE_ENTRY_URL, './UserDashboard')
      ).rejects.toThrow('must be an http(s):// URL')
    })

    it('should accept https:// remoteEntryUrl', async () => {
      const httpsUrl = 'https://cdn.example.com/remoteEntry.json'
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: () =>
          Promise.resolve({
            exposes: { './UserDashboard': 'https://cdn.example.com/chunks/user-dashboard.js' },
            imports: {},
            scopes: {},
          }),
      } as Response)

      await expect(
        loadAngularRemoteModule(httpsUrl, './UserDashboard')
      ).resolves.toBeDefined()
    })
  })

  describe('waitForShim', () => {
    it('should resolve immediately when importShim is already present', async () => {
      // importShim is set in beforeEach
      await expect(waitForShim(100)).resolves.toBeUndefined()
    })

    it('should resolve after polling when importShim appears asynchronously', async () => {
      delete (globalThis as Record<string, unknown>)['importShim']

      const g = globalThis as Record<string, unknown>
      const timer = setTimeout(() => {
        g['importShim'] = mockImportShim
      }, 50)

      await expect(waitForShim(500)).resolves.toBeUndefined()

      clearTimeout(timer)
    })

    it('should reject with a descriptive error when the shim never loads within timeout', async () => {
      delete (globalThis as Record<string, unknown>)['importShim']

      await expect(waitForShim(50)).rejects.toThrow('did not load within')
    })

    it('should warn (not throw) when waitForShim times out inside loadAngularRemoteModule', async () => {
      delete (globalThis as Record<string, unknown>)['importShim']
      const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})

      // Short shim timeout so the test completes quickly
      await loadAngularRemoteModule(REMOTE_ENTRY_URL, './UserDashboard', 50).catch(() => {})

      expect(warnSpy).toHaveBeenCalled()
    }, 2000)
  })
})
