export type MFEName =
  | 'productCatalog'
  | 'cart'
  | 'checkout'
  | 'userDashboard'
  | 'adminDashboard'

export type MFEStatus = 'idle' | 'loading' | 'loaded' | 'error'

export type MFERuntime = 'webpack' | 'native'

export interface MFEConfig {
  name: MFEName
  displayName: string
  remoteUrl: string
  exposedModule: string
  fallbackSkeleton: 'page' | 'productList' | 'cart' | 'profile'
  requiresAuth: boolean
  requiredRoles?: string[]
  runtime: MFERuntime
  /** Shell base path where this MFE is mounted (e.g. "/profile", "/admin"). Used
   *  to set window.__MFE_BASE_HREF for Angular's PathLocationStrategy. */
  basePath?: string
}

export interface MFEState {
  status: MFEStatus
  error: Error | null
  retryCount: number
  lastLoadAttempt: number | null
}

export interface MFELoadResult {
  default: React.ComponentType<unknown>
}

export interface MFELoaderProps {
  mfeName: MFEName
  componentName?: string
  fallback?: React.ReactNode
  onLoad?: () => void
  onError?: (error: Error) => void
}

export interface MFEErrorBoundaryProps {
  mfeName: MFEName
  children: React.ReactNode
  fallback?: React.ReactNode
  onRetry?: () => void
  maxRetries?: number
}

export interface MFEPreloadOptions {
  delay?: number
  priority?: 'high' | 'low'
}
