import type { MFEName, MFELoadResult } from './types'
import { getMFEConfig } from './registry'

type ModuleFactory = () => Promise<MFELoadResult>

const moduleCache = new Map<string, Promise<MFELoadResult>>()
const preloadedModules = new Set<MFEName>()

export function loadRemoteModule(mfeName: MFEName): Promise<MFELoadResult> {
  const config = getMFEConfig(mfeName)
  const cacheKey = `${mfeName}:${config.exposedModule}`

  const cached = moduleCache.get(cacheKey)
  if (cached) {
    return cached
  }

  const loadPromise = importRemoteModule(mfeName)
  moduleCache.set(cacheKey, loadPromise)

  loadPromise.catch(() => {
    moduleCache.delete(cacheKey)
  })

  return loadPromise
}

async function importRemoteModule(mfeName: MFEName): Promise<MFELoadResult> {
  const moduleFactories: Record<MFEName, ModuleFactory> = {
    productCatalog: () => import('productCatalog/ProductCatalog'),
    cart: () => import('cart/Cart'),
    checkout: () => import('checkout/Checkout'),
    userDashboard: () => import('userDashboard/UserDashboard'),
    adminDashboard: () => import('adminDashboard/AdminDashboard'),
  }

  const factory = moduleFactories[mfeName]
  if (!factory) {
    throw new Error(`No module factory found for MFE: ${mfeName}`)
  }

  try {
    const module = await factory()
    return module as MFELoadResult
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Unknown error'
    throw new Error(`Failed to load MFE "${mfeName}": ${message}`)
  }
}

export function preloadModule(mfeName: MFEName): void {
  if (preloadedModules.has(mfeName)) {
    return
  }

  preloadedModules.add(mfeName)
  loadRemoteModule(mfeName).catch(() => {
    preloadedModules.delete(mfeName)
  })
}

export function clearModuleCache(mfeName?: MFEName): void {
  if (mfeName) {
    const config = getMFEConfig(mfeName)
    moduleCache.delete(`${mfeName}:${config.exposedModule}`)
    preloadedModules.delete(mfeName)
  } else {
    moduleCache.clear()
    preloadedModules.clear()
  }
}

export function isModuleCached(mfeName: MFEName): boolean {
  const config = getMFEConfig(mfeName)
  return moduleCache.has(`${mfeName}:${config.exposedModule}`)
}

export function isModulePreloaded(mfeName: MFEName): boolean {
  return preloadedModules.has(mfeName)
}
