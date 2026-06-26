import { lazy, type ComponentType } from 'react'
import type { MFEName, MFELoadResult } from './types'
import { loadRemoteModule } from './moduleLoader'

export const mfeCallbacks = new Map<MFEName, { onError: (e: Error) => void; onLoad?: () => void }>()

export async function loadMFE(mfeName: MFEName): Promise<{ default: ComponentType<unknown> }> {
  try {
    const module = await loadRemoteModule(mfeName)
    // Re-read callbacks at settlement time so effects registered after the
    // lazy factory was first invoked are always captured.
    mfeCallbacks.get(mfeName)?.onLoad?.()
    return module as MFELoadResult
  } catch (err) {
    const loadError = err instanceof Error ? err : new Error('Failed to load module')
    mfeCallbacks.get(mfeName)?.onError(loadError)
    throw loadError
  }
}

export function makeLazy(mfeName: MFEName): React.LazyExoticComponent<ComponentType<unknown>> {
  return lazy(() => loadMFE(mfeName))
}

/** Mutable so entries can be replaced by handleRetry or test reset helpers. */
export const mfeComponents: Record<MFEName, React.LazyExoticComponent<ComponentType<unknown>>> = {
  productCatalog: makeLazy('productCatalog'),
  cart: makeLazy('cart'),
  checkout: makeLazy('checkout'),
  userDashboard: makeLazy('userDashboard'),
  adminDashboard: makeLazy('adminDashboard'),
}

/**
 * Test-only escape hatch: reinitialise the lazy component for `mfeName` so
 * each test gets a fresh promise rather than the cached result from the
 * previous test run. Call this in `beforeEach` when testing MicroFrontendLoader.
 */
export function __resetMFELazyComponents(mfeName?: MFEName): void {
  const targets: MFEName[] = mfeName
    ? [mfeName]
    : (Object.keys(mfeComponents) as MFEName[])
  for (const name of targets) {
    mfeComponents[name] = makeLazy(name)
  }
}
