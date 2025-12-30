import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { ThemeProvider, CssBaseline } from '@mui/material'
import { Auth0ProviderWithNavigate } from './providers/Auth0ProviderWithNavigate'
import { ErrorBoundary } from './components/common'
import theme from './theme'
import App from './App.tsx'
import './index.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <Auth0ProviderWithNavigate>
          <ErrorBoundary>
            <App />
          </ErrorBoundary>
        </Auth0ProviderWithNavigate>
      </BrowserRouter>
    </ThemeProvider>
  </StrictMode>,
)
