import { ThemeProvider, CssBaseline } from '@mui/material'
import { Auth0ProviderWithNavigate } from './providers/Auth0ProviderWithNavigate'
import { ErrorBoundary } from './components/common'
import { useColorMode } from './hooks/useColorMode'
import App from './App'

export default function ThemedApp() {
  const { muiTheme } = useColorMode()

  return (
    <ThemeProvider theme={muiTheme}>
      <CssBaseline />
      <Auth0ProviderWithNavigate>
        <ErrorBoundary>
          <App />
        </ErrorBoundary>
      </Auth0ProviderWithNavigate>
    </ThemeProvider>
  )
}
