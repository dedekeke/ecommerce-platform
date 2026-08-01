import { useCallback, type ReactNode } from 'react'
import { ErrorBoundary as ReactErrorBoundary, type FallbackProps } from 'react-error-boundary'
import { Box, Typography, Button, Container, Paper } from '@mui/material'
import { Error as ErrorIcon, Home as HomeIcon, Refresh as RefreshIcon } from '@mui/icons-material'

interface Props {
  children: ReactNode
  fallback?: ReactNode
}

function ErrorFallbackComponent({ error, resetErrorBoundary }: FallbackProps) {
  const handleGoHome = () => {
    window.location.href = '/'
  }

  return (
    <Container maxWidth="sm">
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: '60vh',
          textAlign: 'center',
        }}
      >
        <Paper
          elevation={0}
          sx={{
            p: 4,
            bgcolor: 'error.light',
            color: 'error.contrastText',
            borderRadius: 2,
            mb: 3,
          }}
        >
          <ErrorIcon sx={{ fontSize: 64 }} />
        </Paper>

        <Typography variant="h4" gutterBottom fontWeight={600}>
          Something went wrong
        </Typography>

        <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>
          We're sorry, but something unexpected happened.
        </Typography>

        {error instanceof Error && (
          <Paper
            sx={{
              p: 2,
              mb: 3,
              bgcolor: 'grey.100',
              width: '100%',
              overflow: 'auto',
            }}
          >
            <Typography
              variant="body2"
              component="pre"
              sx={{
                fontFamily: 'monospace',
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
                m: 0,
              }}
            >
              {error.message}
            </Typography>
          </Paper>
        )}

        <Box sx={{ display: 'flex', gap: 2 }}>
          <Button variant="contained" startIcon={<RefreshIcon />} onClick={resetErrorBoundary}>
            Try Again
          </Button>
          <Button variant="outlined" startIcon={<HomeIcon />} onClick={handleGoHome}>
            Go Home
          </Button>
        </Box>
      </Box>
    </Container>
  )
}

export function ErrorBoundary({ children, fallback }: Props) {
  const handleError = useCallback((error: unknown, info: React.ErrorInfo) => {
    console.error('Error caught by boundary:', error, info)
  }, [])

  if (fallback) {
    return (
      <ReactErrorBoundary onError={handleError} fallback={<>{fallback}</>}>
        {children}
      </ReactErrorBoundary>
    )
  }

  return (
    <ReactErrorBoundary onError={handleError} FallbackComponent={ErrorFallbackComponent}>
      {children}
    </ReactErrorBoundary>
  )
}
