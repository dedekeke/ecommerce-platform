import { useEffect, useRef, useReducer } from 'react'
import { loadRemoteModule } from '@angular-architects/native-federation-runtime'
import { Box, CircularProgress, Typography, Paper, Button } from '@mui/material'
import { ErrorOutline as ErrorIcon, Refresh as RefreshIcon } from '@mui/icons-material'
import type { MFEName } from './types'
import { getMFEConfig } from './registry'
import { loadAngularRemoteModule } from './angularFederationBridge'

/** bootstrap() must return a destroy handle to prevent ApplicationRef leaks */
interface AngularBootstrapModule {
  bootstrap: (elementId: string) => Promise<() => void>
}

interface AngularMFEWrapperProps {
  mfeName: MFEName
}

type WrapperStatus = 'loading' | 'mounted' | 'error'

interface WrapperState {
  status: WrapperStatus
  errorMessage: string
  retryKey: number
}

type WrapperAction =
  | { type: 'RETRY' }
  | { type: 'MOUNTED' }
  | { type: 'ERROR'; message: string }

function wrapperReducer(state: WrapperState, action: WrapperAction): WrapperState {
  switch (action.type) {
    case 'RETRY':
      return { status: 'loading', errorMessage: '', retryKey: state.retryKey + 1 }
    case 'MOUNTED':
      return { ...state, status: 'mounted' }
    case 'ERROR':
      return { ...state, status: 'error', errorMessage: action.message }
  }
}

export function AngularMFEWrapper({ mfeName }: AngularMFEWrapperProps) {
  const config = getMFEConfig(mfeName)
  const mountId = `angular-mount-${mfeName}`
  const mountRef = useRef<HTMLDivElement | null>(null)
  const [state, dispatch] = useReducer(wrapperReducer, {
    status: 'loading',
    errorMessage: '',
    retryKey: 0,
  })

  useEffect(() => {
    let destroyed = false
    let destroyAngular: (() => void) | undefined

    async function loadAndBootstrap() {
      try {
        // Inject the base path so Angular's PathLocationStrategy can resolve
        // sub-routes relative to the shell-assigned mount path.
        ;(globalThis as Record<string, unknown>)['__MFE_BASE_HREF'] = config.basePath ?? `/${mfeName}`

        // Prefer the bridge for native-federation remotes (remoteEntry.json based).
        // The bridge injects the Angular import map via es-module-shims so bare
        // Angular specifiers resolve correctly in the shell's ESM context.
        // Fall back to the @angular-architects runtime loader when remoteUrl is
        // not a JSON manifest (future webpack-based Angular MFEs).
        const isNativeManifest = config.remoteUrl.endsWith('.json')
        const module = (
          isNativeManifest
            ? await loadAngularRemoteModule(config.remoteUrl, config.exposedModule)
            : await loadRemoteModule({ remoteName: mfeName, exposedModule: config.exposedModule })
        ) as Partial<AngularBootstrapModule>

        if (destroyed) return

        if (typeof module.bootstrap !== 'function') {
          throw new Error(
            `No bootstrap function exported from "${mfeName}" remote. The Angular MFE must export a bootstrap(elementId) function.`
          )
        }

        destroyAngular = await module.bootstrap(mountId)

        if (!destroyed) {
          dispatch({ type: 'MOUNTED' })
        }
      } catch (err) {
        if (destroyed) return
        const message = err instanceof Error ? err.message : String(err)
        dispatch({ type: 'ERROR', message })
      }
    }

    void loadAndBootstrap()

    return () => {
      destroyed = true
      destroyAngular?.()
    }
  }, [mfeName, config.basePath, config.exposedModule, config.remoteUrl, mountId, state.retryKey])

  if (state.status === 'error') {
    return (
      <Paper
        data-testid="angular-mfe-error"
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
          Failed to load {config.displayName}
        </Typography>
        <Typography variant="body2" sx={{ mb: 3, opacity: 0.9 }}>
          {state.errorMessage}
        </Typography>
        <Button
          variant="contained"
          color="inherit"
          startIcon={<RefreshIcon />}
          onClick={() => dispatch({ type: 'RETRY' })}
          sx={{ color: 'error.main', bgcolor: 'common.white' }}
        >
          Retry
        </Button>
      </Paper>
    )
  }

  return (
    <>
      {state.status === 'loading' && (
        <Box
          data-testid="angular-mfe-loading"
          aria-busy="true"
          aria-label={`Loading ${config.displayName}`}
          sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 4 }}
        >
          <CircularProgress size={20} />
          <Typography variant="body2" color="text.secondary">
            Loading {config.displayName}...
          </Typography>
        </Box>
      )}
      <div
        id={mountId}
        data-testid={mountId}
        ref={mountRef}
        style={{ width: '100%', height: '100%' }}
      />
    </>
  )
}
