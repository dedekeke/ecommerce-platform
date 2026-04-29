import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ThemeProvider } from '@mui/material'
import theme from '../theme'

// Mock Auth0 — unauthenticated by default so ProtectedRoute redirects
vi.mock('@auth0/auth0-react', () => ({
  useAuth0: vi.fn(),
}))

// Mock MFE module loader so tests never hit real remotes
const mockLoadRemote = vi.fn()
vi.mock('./moduleLoader', () => ({
  loadRemoteModule: () => mockLoadRemote(),
  clearModuleCache: vi.fn(),
  preloadModule: vi.fn(),
  isModuleCached: vi.fn(() => false),
  isModulePreloaded: vi.fn(() => false),
}))

vi.mock('./registry', () => ({
  getMFEConfig: vi.fn((name: string) => ({
    name,
    displayName: name === 'checkout' ? 'Checkout' : name,
    remoteUrl: `http://localhost:5003/assets/remoteEntry.js`,
    exposedModule: './Checkout',
    fallbackSkeleton: 'page',
    requiresAuth: name === 'checkout',
  })),
}))

import { useAuth0 } from '@auth0/auth0-react'
import { mockAuth0 } from '../test/mocks/auth0'
import App from '../App'

function renderApp(initialEntries: string[]) {
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter initialEntries={initialEntries}>
        <App />
      </MemoryRouter>
    </ThemeProvider>
  )
}

describe('Checkout route integration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should redirect unauthenticated users away from /checkout', async () => {
    vi.mocked(useAuth0).mockReturnValue(mockAuth0({ isAuthenticated: false, isLoading: false }))

    renderApp(['/checkout'])

    await waitFor(() => {
      expect(screen.queryByTestId('mfe-loading-skeleton')).not.toBeInTheDocument()
    })
  })

  it('should mount the checkout MFE loader for authenticated users at /checkout', async () => {
    vi.mocked(useAuth0).mockReturnValue(mockAuth0({ isAuthenticated: true, isLoading: false }))
    mockLoadRemote.mockReturnValue(new Promise(() => {}))

    renderApp(['/checkout'])

    await waitFor(() => {
      expect(screen.getByTestId('mfe-loading-skeleton')).toBeInTheDocument()
    })
  })

  it('should mount the checkout MFE loader at /checkout/confirmation/order-123', async () => {
    vi.mocked(useAuth0).mockReturnValue(mockAuth0({ isAuthenticated: true, isLoading: false }))
    mockLoadRemote.mockReturnValue(new Promise(() => {}))

    renderApp(['/checkout/confirmation/order-123'])

    await waitFor(() => {
      expect(screen.getByTestId('mfe-loading-skeleton')).toBeInTheDocument()
    })
  })

  it('should render the checkout MFE component when loader resolves', async () => {
    vi.mocked(useAuth0).mockReturnValue(mockAuth0({ isAuthenticated: true, isLoading: false }))
    const FakeCheckout = () => (
      <div data-testid="checkout-mfe">Checkout MFE</div>
    )
    mockLoadRemote.mockResolvedValue({ default: FakeCheckout })

    renderApp(['/checkout'])

    await waitFor(() => {
      expect(screen.getByTestId('checkout-mfe')).toBeInTheDocument()
    })
  })
})
