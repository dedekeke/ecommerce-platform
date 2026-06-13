/**
 * Bridge between Angular native-federation (import maps) and the vite-plugin-federation
 * webpack-style remotes used by the React shell.
 *
 * How it works:
 *  1. Fetch the Angular MFE's remoteEntry.json (native-federation manifest).
 *  2. Extract the import map it declares (imports + scopes).
 *  3. Inject that import map into the page via es-module-shims' `importShim.addImportMap()`.
 *  4. Use `importShim()` (the shim's dynamic-import equivalent) to load the exposed module
 *     so that Angular's bare-specifier imports resolve through the injected map.
 *  5. Return the loaded module to the caller (AngularMFEWrapper).
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

/** Access es-module-shims on the global scope (injected via index.html) */
function getShim(): EsModuleShims | null {
  const g = globalThis as Record<string, unknown>
  return typeof g['importShim'] === 'function'
    ? (g as unknown as { importShim: EsModuleShims }).importShim
    : null
}

const remoteEntryCache = new Map<string, NativeFederationRemoteEntry>()

async function fetchRemoteEntry(remoteEntryUrl: string): Promise<NativeFederationRemoteEntry> {
  const cached = remoteEntryCache.get(remoteEntryUrl)
  if (cached) return cached

  const res = await fetch(remoteEntryUrl)
  if (!res.ok) {
    throw new Error(
      `angularFederationBridge: failed to fetch remote entry at ${remoteEntryUrl} (HTTP ${res.status})`
    )
  }

  const entry = (await res.json()) as NativeFederationRemoteEntry
  remoteEntryCache.set(remoteEntryUrl, entry)
  return entry
}

/**
 * Load an exposed module from an Angular native-federation remote.
 *
 * @param remoteEntryUrl  URL to the MFE's remoteEntry.json
 * @param exposedModule   Key from the remote's `exposes` map, e.g. "./UserDashboard"
 * @returns               The ESM module namespace object
 */
export async function loadAngularRemoteModule(
  remoteEntryUrl: string,
  exposedModule: string
): Promise<Record<string, unknown>> {
  const entry = await fetchRemoteEntry(remoteEntryUrl)

  // Merge the remote's own import map into the page so its chunks can resolve
  const shim = getShim()
  if (shim) {
    shim.addImportMap({
      imports: entry.imports ?? {},
      scopes: entry.scopes ?? {},
    })
  } else {
    // es-module-shims not loaded yet (e.g. test environment) — proceed anyway;
    // native browser importmap support or mocked loader will handle resolution.
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

  // Use importShim() when available (respects the injected import map),
  // otherwise fall back to native dynamic import (works when browser supports importmap natively).
  const g = globalThis as Record<string, unknown>
  const dynamicImport =
    typeof g['importShim'] === 'function'
      ? (g['importShim'] as (url: string) => Promise<Record<string, unknown>>)
      : (url: string) => import(/* @vite-ignore */ url) as Promise<Record<string, unknown>>

  return dynamicImport(exposedUrl)
}

/** Clear the fetch cache (useful in tests) */
export function clearAngularBridgeCache(): void {
  remoteEntryCache.clear()
}
