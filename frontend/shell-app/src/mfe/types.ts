export type MFEName =
  | 'productCatalog'
  | 'cart'
  | 'checkout'
  | 'userDashboard'
  | 'adminDashboard'

export type MFEStatus = 'idle' | 'loading' | 'loaded' | 'error'

export interface MFEConfig {
  name: MFEName
  displayName: string
  remoteUrl: string
  exposedModule: string
  fallbackSkeleton: 'page' | 'productList' | 'cart' | 'profile'
  requiresAuth: boolean
  requiredRoles?: string[]
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
