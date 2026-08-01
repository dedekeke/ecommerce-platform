import { useState, useCallback } from 'react'
import { ErrorBoundary, type FallbackProps } from 'react-error-boundary'
import { Box, Typography, Button, Paper, Container } from '@mui/material'
import {
  Refresh as RefreshIcon,
  Home as HomeIcon,
  ErrorOutline as ErrorIcon,
} from '@mui/icons-material'
import type { MFEName, MFEErrorBoundaryProps } from './types'
import { getMFEConfig } from './registry'

interface Props extends MFEErrorBoundaryProps {
  mfeName: MFEName
}

interface ErrorFallbackComponentProps extends FallbackProps {
  displayName: string
  retryCount: number
  maxRetries: number
  onRetryClick: () => void
  customFallback?: React.ReactNode
}

function ErrorFallbackComponent({
  error,
  displayName,
  retryCount,
  maxRetries,
  onRetryClick,
  customFallback,
}: ErrorFallbackComponentProps) {
  const isRetryDisabled = retryCount >= maxRetries

  if (customFallback) {
    return <>{customFallback}</>
  }

  const handleGoHome = () => {
    window.location.href = '/'
  }

  return (
    <Container maxWidth="sm">
      <Paper
        role="alert"
        elevation={0}
        sx={{
          p: 4,
          mt: 4,
          textAlign: 'center',
          bgcolor: 'error.light',
          color: 'error.contrastText',
          borderRadius: 2,
        }}
      >
        <ErrorIcon sx={{ fontSize: 64, mb: 2, opacity: 0.8 }} />

        <Typography variant="h5" gutterBottom fontWeight={600}>
          Something went wrong
        </Typography>

        <Typography variant="body1" sx={{ mb: 1 }}>
          The {displayName} module encountered an error.
        </Typography>

        {error instanceof Error && (
          <Typography
            variant="body2"
            sx={{
              mb: 3,
              p: 2,
              bgcolor: 'rgba(0,0,0,0.1)',
              borderRadius: 1,
              fontFamily: 'monospace',
              wordBreak: 'break-word',
            }}
          >
            {error.message}
          </Typography>
        )}

        {retryCount > 0 && (
          <Typography variant="caption" sx={{ display: 'block', mb: 2, opacity: 0.8 }}>
            Retry attempt {retryCount} of {maxRetries}
          </Typography>
        )}

        <Box sx={{ display: 'flex', gap: 2, justifyContent: 'center' }}>
          <Button
            variant="contained"
            color="inherit"
            startIcon={<RefreshIcon />}
            onClick={onRetryClick}
            disabled={isRetryDisabled}
            sx={{ color: 'error.main', bgcolor: 'common.white' }}
          >
            {isRetryDisabled ? 'Max Retries Reached' : 'Retry'}
          </Button>
          <Button
            variant="outlined"
            color="inherit"
            startIcon={<HomeIcon />}
            onClick={handleGoHome}
            sx={{ borderColor: 'currentColor' }}
          >
            Go Home
          </Button>
        </Box>
      </Paper>
    </Container>
  )
}

export function MFEErrorBoundary({ mfeName, children, fallback, onRetry, maxRetries = 3 }: Props) {
  const [retryCount, setRetryCount] = useState(0)
  const config = getMFEConfig(mfeName)

  const handleReset = useCallback(() => {
    if (retryCount < maxRetries) {
      setRetryCount((prev) => prev + 1)
      onRetry?.()
    }
  }, [retryCount, maxRetries, onRetry])

  const handleError = useCallback(
    (error: unknown, info: React.ErrorInfo) => {
      console.error(`MFE Error in ${config.displayName}:`, error, info)
    },
    [config.displayName]
  )

  return (
    <ErrorBoundary
      onError={handleError}
      onReset={handleReset}
      fallbackRender={({ error, resetErrorBoundary }) => (
        <ErrorFallbackComponent
          error={error}
          resetErrorBoundary={resetErrorBoundary}
          displayName={config.displayName}
          retryCount={retryCount}
          maxRetries={maxRetries}
          onRetryClick={resetErrorBoundary}
          customFallback={fallback}
        />
      )}
    >
      {children}
    </ErrorBoundary>
  )
}
