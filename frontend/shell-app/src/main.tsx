import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { ThemeProvider, CssBaseline } from '@mui/material'
import { Auth0ProviderWithNavigate } from './providers/Auth0ProviderWithNavigate'
import { ErrorBoundary } from './components/common'
import theme from './theme'
import App from './App.tsx'
import './index.css'
import { initNativeFederation } from './mfe/nativeFederation'

// One-time localStorage key migration: cart-mfe-storage → cart-storage.
// Runs before React mounts so the store rehydrates with the correct key.
// No-ops safely on a fresh install (neither key exists).
;(function migrateCartStorage() {
  try {
    const legacyKey = 'cart-mfe-storage'
    const canonicalKey = 'cart-storage'
    const legacy = localStorage.getItem(legacyKey)
    const canonical = localStorage.getItem(canonicalKey)
    if (legacy !== null && canonical === null) {
      localStorage.setItem(canonicalKey, legacy)
      localStorage.removeItem(legacyKey)
    }
  } catch {
    // localStorage may be unavailable (e.g. private browsing quota exceeded)
  }
})()

function mountApp() {
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
}

// Initialize native-federation import maps for Angular MFEs before mounting.
// A failure here is non-fatal: Angular MFEs will show their own error state
// via AngularMFEWrapper, while all webpack-based MFEs continue to work.
initNativeFederation()
  .catch((err: unknown) => {
    console.warn('[shell] Native federation init failed — Angular MFEs may not load:', err)
  })
  .finally(() => {
    mountApp()
  })
