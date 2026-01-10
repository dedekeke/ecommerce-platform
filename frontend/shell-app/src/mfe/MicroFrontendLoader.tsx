import { Suspense, lazy, useState, useCallback, useMemo, type ComponentType } from 'react'
import { Box, Typography, Button, Paper, CircularProgress } from '@mui/material'
import { Refresh as RefreshIcon, ErrorOutline as ErrorIcon } from '@mui/icons-material'
import type { MFEName, MFELoadResult } from './types'
import { getMFEConfig } from './registry'
import { loadRemoteModule, clearModuleCache } from './moduleLoader'
import {
  PageSkeleton,
  ProductListSkeleton,
  CartItemSkeleton,
  ProfileSkeleton,
} from '../components/common/LoadingSkeleton'

interface MicroFrontendLoaderProps {
  mfeName: MFEName
  fallback?: React.ReactNode
  onLoad?: () => void
  onError?: (error: Error) => void
  componentProps?: Record<string, unknown>
}

type SkeletonType = 'page' | 'productList' | 'cart' | 'profile'

function getSkeletonComponent(type: SkeletonType): React.ReactNode {
  switch (type) {
    case 'productList':
      return <ProductListSkeleton count={6} />
    case 'cart':
      return (
        <Box>
          {[1, 2, 3].map((i) => (
            <CartItemSkeleton key={i} />
          ))}
        </Box>
      )
    case 'profile':
      return <ProfileSkeleton />
    case 'page':
    default:
      return <PageSkeleton />
  }
}

interface LoadingFallbackProps {
  displayName: string
  skeletonType: SkeletonType
  customFallback?: React.ReactNode
}

function LoadingFallback({ displayName, skeletonType, customFallback }: LoadingFallbackProps) {
  if (customFallback) {
    return <>{customFallback}</>
  }

  return (
    <Box
      data-testid="mfe-loading-skeleton"
      aria-busy="true"
      aria-label={`Loading ${displayName}`}
      sx={{ py: 4 }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3 }}>
        <CircularProgress size={20} />
        <Typography variant="body2" color="text.secondary">
          Loading {displayName}...
        </Typography>
      </Box>
      {getSkeletonComponent(skeletonType)}
    </Box>
  )
}

interface ErrorFallbackProps {
  displayName: string
  error: Error
  onRetry: () => void
}

function ErrorFallback({ displayName, error, onRetry }: ErrorFallbackProps) {
  return (
    <Paper
      role="alert"
      elevation={0}
      sx={{
        p: 4,
        textAlign: 'center',
        bgcolor: 'error.light',
        color: 'error.contrastText',
        borderRadius: 2,
        maxWidth: 500,
        mx: 'auto',
        my: 4,
      }}
    >
      <ErrorIcon sx={{ fontSize: 48, mb: 2, opacity: 0.8 }} />
      <Typography variant="h6" gutterBottom>
        Failed to load {displayName}
      </Typography>
      <Typography variant="body2" sx={{ mb: 3, opacity: 0.9 }}>
        {error.message}
      </Typography>
      <Button
        variant="contained"
        color="inherit"
        startIcon={<RefreshIcon />}
        onClick={onRetry}
        sx={{ color: 'error.main', bgcolor: 'common.white' }}
      >
        Retry
      </Button>
    </Paper>
  )
}

function createLazyComponent(
  mfeName: MFEName,
  onError?: (error: Error) => void,
  onLoad?: () => void
): React.LazyExoticComponent<ComponentType<unknown>> {
  return lazy(async (): Promise<{ default: ComponentType<unknown> }> => {
    try {
      const module = await loadRemoteModule(mfeName)
      onLoad?.()
      return module as MFELoadResult
    } catch (err) {
      const loadError = err instanceof Error ? err : new Error('Failed to load module')
      onError?.(loadError)
      throw loadError
    }
  })
}

export function MicroFrontendLoader({
  mfeName,
  fallback,
  onLoad,
  onError,
  componentProps = {},
}: MicroFrontendLoaderProps) {
  const [error, setError] = useState<Error | null>(null)
  const [retryKey, setRetryKey] = useState(0)
  const config = getMFEConfig(mfeName)

  const handleRetry = useCallback(() => {
    clearModuleCache(mfeName)
    setError(null)
    setRetryKey((prev) => prev + 1)
  }, [mfeName])

  const handleError = useCallback(
    (err: Error) => {
      setError(err)
      onError?.(err)
    },
    [onError]
  )

  const LazyComponent = useMemo(
    () => createLazyComponent(mfeName, handleError, onLoad),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [mfeName, retryKey]
  )

  if (error) {
    return (
      <ErrorFallback displayName={config.displayName} error={error} onRetry={handleRetry} />
    )
  }

  return (
    <Suspense
      key={retryKey}
      fallback={
        <LoadingFallback
          displayName={config.displayName}
          skeletonType={config.fallbackSkeleton}
          customFallback={fallback}
        />
      }
    >
      <LazyComponent {...componentProps} />
    </Suspense>
  )
}
