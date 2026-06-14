import { Suspense, useState, useCallback } from 'react'
import { Box, Typography, Button, Paper, CircularProgress } from '@mui/material'
import { Refresh as RefreshIcon, ErrorOutline as ErrorIcon } from '@mui/icons-material'
import type { MFEName } from './types'
import { getMFEConfig } from './registry'
import { clearModuleCache } from './moduleLoader'
import { mfeCallbacks, mfeComponents, makeLazy } from './lazyRegistry'
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

  const handleError = useCallback(
    (err: Error) => {
      setError(err)
      onError?.(err)
    },
    [onError]
  )

  // Register callbacks so the module-level factory in lazyRegistry can report
  // back to this specific component instance.
  mfeCallbacks.set(mfeName, { onError: handleError, onLoad })

  const handleRetry = useCallback(() => {
    clearModuleCache(mfeName)
    // Recreate the lazy component so the next Suspense mount gets a fresh
    // promise rather than the previously cached (rejected) one.
    mfeComponents[mfeName] = makeLazy(mfeName)
    setError(null)
    setRetryKey((prev) => prev + 1)
  }, [mfeName])

  const LazyComponent = mfeComponents[mfeName]

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

