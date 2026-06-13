import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { loadAngularRemoteModule, clearAngularBridgeCache } from './angularFederationBridge'

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

  it('should cache the remoteEntry.json and not re-fetch on repeated calls', async () => {
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
    // Replace globalThis importShim with a simple dynamic import stand-in
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})

    // Because we removed importShim the bridge falls back to native dynamic import.
    // We cannot intercept native import() in vitest easily, so we re-attach a plain fn.
    ;(globalThis as Record<string, unknown>)['importShim'] = undefined

    await loadAngularRemoteModule(REMOTE_ENTRY_URL, './UserDashboard').catch(() => {
      // native import() of a localhost URL will fail in test env — that's expected
    })

    expect(warnSpy).toHaveBeenCalledWith(expect.stringContaining('es-module-shims not available'))
    warnSpy.mockRestore()
  })

  it('should clear the cache on clearAngularBridgeCache()', async () => {
    await loadAngularRemoteModule(REMOTE_ENTRY_URL, './UserDashboard')
    clearAngularBridgeCache()
    await loadAngularRemoteModule(REMOTE_ENTRY_URL, './UserDashboard')

    expect(globalThis.fetch).toHaveBeenCalledTimes(2)
  })
})
