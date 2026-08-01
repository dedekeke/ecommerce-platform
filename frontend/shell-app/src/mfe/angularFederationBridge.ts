/**
 * Bridge between Angular native-federation (import maps) and the vite-plugin-federation
 * webpack-style remotes used by the React shell.
 *
 * How it works:
 *  1. Wait for es-module-shims to finish loading (it is synchronous in index.html but
 *     the guard protects against race conditions on direct navigation).
 *  2. Fetch the Angular MFE's remoteEntry.json (native-federation manifest).
 *  3. Extract the import map it declares (imports + scopes).
 *  4. Inject that import map into the page via es-module-shims' `importShim.addImportMap()`.
 *  5. Use `importShim()` (the shim's dynamic-import equivalent) to load the exposed module
 *     so that Angular's bare-specifier imports resolve through the injected map.
 *  6. Return the loaded module to the caller (AngularMFEWrapper).
 *
 * Why not use vite-plugin-federation's remote string format directly?
 * vite-plugin-federation emits a webpack-compatible remoteEntry.js that uses
 * SystemJS/webpack container semantics. Angular native-federation emits a JSON
 * manifest + ESM chunks using import maps. The two protocols are incompatible at
 * the wire level, so we implement the native-federation client protocol here on
 * the shell side rather than relying on vite-plugin-federation's loader.
 */

/** Subset of es-module-shims' global API used by this bridge */
interface EsModuleShims {
  addImportMap: (map: ImportMapDescriptor) => void
}

interface ImportMapDescriptor {
  imports?: Record<string, string>
  scopes?: Record<string, Record<string, string>>
}

/** Shape of an Angular native-federation remoteEntry.json */
interface NativeFederationRemoteEntry {
  /** Map from exposed module key (e.g. "./UserDashboard") to its ESM URL */
  exposes?: Record<string, string>
  /** Additional import map entries the remote needs in scope */
  imports?: Record<string, string>
  scopes?: Record<string, Record<string, string>>
}

const SHIM_POLL_INTERVAL_MS = 20

/**
 * Poll globalThis.importShim until the es-module-shims script has evaluated.
 * Under normal conditions (sync script tag) the shim is already present when
 * this is called; the guard exists for direct navigation / race conditions.
 */
export async function waitForShim(timeoutMs = 5000): Promise<void> {
  const g = globalThis as Record<string, unknown>
  if (typeof g['importShim'] === 'function') return

  return new Promise((resolve, reject) => {
    const deadline = Date.now() + timeoutMs
    const interval = setInterval(() => {
      if (typeof g['importShim'] === 'function') {
        clearInterval(interval)
        resolve()
      } else if (Date.now() >= deadline) {
        clearInterval(interval)
        reject(
          new Error(
            `angularFederationBridge: es-module-shims did not load within ${timeoutMs}ms. ` +
              'Ensure /es-module-shims.js is served correctly.'
          )
        )
      }
    }, SHIM_POLL_INTERVAL_MS)
  })
}

/** Access es-module-shims on the global scope (injected via index.html) */
function getShim(): EsModuleShims | null {
  const g = globalThis as Record<string, unknown>
  return typeof g['importShim'] === 'function'
    ? (g as unknown as { importShim: EsModuleShims }).importShim
    : null
}

function assertHttpsUrl(url: string, context: string): void {
  if (!/^https?:\/\//i.test(url)) {
    throw new Error(
      `angularFederationBridge: ${context} must be an http(s):// URL, got: "${url}"`
    )
  }
}

/**
 * Keyed by remoteEntry URL.
 * Stores the in-flight fetch Promise OR the resolved entry so concurrent callers
 * (including StrictMode double-invokes) share a single network request.
 */
const remoteEntryCache = new Map<string, Promise<NativeFederationRemoteEntry>>()

function fetchRemoteEntry(remoteEntryUrl: string): Promise<NativeFederationRemoteEntry> {
  const cached = remoteEntryCache.get(remoteEntryUrl)
  if (cached) return cached

  const promise = (async () => {
    const res = await fetch(remoteEntryUrl)
    if (!res.ok) {
      throw new Error(
        `angularFederationBridge: failed to fetch remote entry at ${remoteEntryUrl} (HTTP ${res.status})`
      )
    }
    return (await res.json()) as NativeFederationRemoteEntry
  })()

  remoteEntryCache.set(remoteEntryUrl, promise)
  return promise
}

/**
 * Load an exposed module from an Angular native-federation remote.
 *
 * @param remoteEntryUrl  URL to the MFE's remoteEntry.json (must be http(s)://)
 * @param exposedModule   Key from the remote's `exposes` map, e.g. "./UserDashboard"
 * @param shimTimeoutMs   Max ms to wait for es-module-shims (default 5000)
 * @returns               The ESM module namespace object
 */
export async function loadAngularRemoteModule(
  remoteEntryUrl: string,
  exposedModule: string,
  shimTimeoutMs = 5000
): Promise<Record<string, unknown>> {
  assertHttpsUrl(remoteEntryUrl, 'remoteEntryUrl')

  // Ensure the shim is ready before injecting the import map
  await waitForShim(shimTimeoutMs).catch((err: Error) => {
    // Non-fatal: warn and continue so native importmap browsers still work
    console.warn(err.message)
  })

  const entry = await fetchRemoteEntry(remoteEntryUrl)

  // Merge the remote's own import map into the page so its chunks can resolve
  const shim = getShim()
  if (shim) {
    shim.addImportMap({
      imports: entry.imports ?? {},
      scopes: entry.scopes ?? {},
    })
  } else {
    // es-module-shims not available — native browser importmap or test env
    console.warn(
      'angularFederationBridge: es-module-shims not available. ' +
        'Import map injection skipped — Angular bare specifiers may fail to resolve.'
    )
  }

  const exposedUrl = entry.exposes?.[exposedModule]
  if (!exposedUrl) {
    throw new Error(
      `angularFederationBridge: exposed module "${exposedModule}" not found in ${remoteEntryUrl}. ` +
        `Available: ${Object.keys(entry.exposes ?? {}).join(', ')}`
    )
  }

  assertHttpsUrl(exposedUrl, `exposes["${exposedModule}"]`)

  // Use importShim() when available (respects the injected import map),
  // otherwise fall back to native dynamic import (works when browser supports importmap natively).
  const g = globalThis as Record<string, unknown>
  const dynamicImport =
    typeof g['importShim'] === 'function'
      ? (g['importShim'] as (url: string) => Promise<Record<string, unknown>>)
      : (url: string) => import(/* @vite-ignore */ url) as Promise<Record<string, unknown>>

  return dynamicImport(exposedUrl)
}

/** Clear the fetch cache — for tests only; not part of the public API. */
export function clearAngularBridgeCache(): void {
  remoteEntryCache.clear()
}
