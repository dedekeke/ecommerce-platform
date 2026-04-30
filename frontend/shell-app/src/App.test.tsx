import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import App from './App'

vi.mock('@auth0/auth0-react', () => ({
  useAuth0: vi.fn(),
  Auth0Provider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

vi.mock('./mfe', () => ({
  MicroFrontendLoader: ({ mfeName }: { mfeName: string }) => (
    <div data-testid={`mfe-${mfeName}`}>MFE: {mfeName}</div>
  ),
  MFEErrorBoundary: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useMFEPreload: () => ({
    onMouseEnter: vi.fn(),
    onMouseLeave: vi.fn(),
    onFocus: vi.fn(),
    onBlur: vi.fn(),
  }),
}))

vi.mock('./components/layout', () => ({
  MainLayout: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="main-layout">{children}</div>
  ),
}))

vi.mock('./components/common', () => ({
  PageSkeleton: () => <div data-testid="page-skeleton" />,
}))

vi.mock('./components/auth', () => ({
  ProtectedRoute: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="protected-route">{children}</div>
  ),
}))

vi.mock('./components/auth/RoleGuard', () => ({
  RoleGuard: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="role-guard">{children}</div>
  ),
}))

vi.mock('./stores', () => ({
  useCartStore: vi.fn(() => 0),
  selectCartItemCount: (s: { itemCount: number }) => s.itemCount,
}))

vi.mock('./hooks', () => ({
  useExposeAuthToken: vi.fn(),
}))

import { useAuth0 } from '@auth0/auth0-react'
import { useCartStore } from './stores'

const mockUseAuth0 = vi.mocked(useAuth0)
const mockUseCartStore = vi.mocked(useCartStore)

function renderAppAtRoute(route: string) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <App />
    </MemoryRouter>
  )
}

beforeEach(() => {
  mockUseAuth0.mockReturnValue({
    isLoading: false,
    isAuthenticated: false,
    user: undefined,
    loginWithRedirect: vi.fn(),
    loginWithPopup: vi.fn(),
    logout: vi.fn(),
    getAccessTokenSilently: vi.fn(),
    getAccessTokenWithPopup: vi.fn(),
    getIdTokenClaims: vi.fn(),
    handleRedirectCallback: vi.fn(),
  } as unknown as ReturnType<typeof useAuth0>)
  mockUseCartStore.mockReturnValue(0 as unknown as never)
})

describe('App routing', () => {
  it('renders the home page at /', () => {
    renderAppAtRoute('/')
    expect(screen.getByRole('heading', { name: /welcome to e-commerce/i })).toBeInTheDocument()
  })

  it('home page renders category navigation links', () => {
    renderAppAtRoute('/')
    expect(screen.getByRole('link', { name: /electronics/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /fashion/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /home & garden/i })).toBeInTheDocument()
  })

  it('renders productCatalog MFE at /products', () => {
    renderAppAtRoute('/products')
    expect(screen.getByTestId('mfe-productCatalog')).toBeInTheDocument()
  })

  it('renders productCatalog MFE at /categories', () => {
    renderAppAtRoute('/categories')
    expect(screen.getByTestId('mfe-productCatalog')).toBeInTheDocument()
  })

  it('renders cart MFE at /cart', () => {
    renderAppAtRoute('/cart')
    expect(screen.getByTestId('mfe-cart')).toBeInTheDocument()
  })

  it('renders checkout MFE at /checkout wrapped in ProtectedRoute', () => {
    renderAppAtRoute('/checkout')
    expect(screen.getByTestId('protected-route')).toBeInTheDocument()
    expect(screen.getByTestId('mfe-checkout')).toBeInTheDocument()
  })

  it('renders userDashboard MFE at /profile wrapped in ProtectedRoute', () => {
    renderAppAtRoute('/profile')
    expect(screen.getByTestId('protected-route')).toBeInTheDocument()
    expect(screen.getByTestId('mfe-userDashboard')).toBeInTheDocument()
  })

  it('renders userDashboard MFE at /orders wrapped in ProtectedRoute', () => {
    renderAppAtRoute('/orders')
    expect(screen.getByTestId('protected-route')).toBeInTheDocument()
    expect(screen.getByTestId('mfe-userDashboard')).toBeInTheDocument()
  })

  it('renders adminDashboard MFE at /admin wrapped in RoleGuard and ProtectedRoute', () => {
    renderAppAtRoute('/admin')
    expect(screen.getByTestId('protected-route')).toBeInTheDocument()
    expect(screen.getByTestId('role-guard')).toBeInTheDocument()
    expect(screen.getByTestId('mfe-adminDashboard')).toBeInTheDocument()
  })

  it('shows PageSkeleton while Auth0 is loading', () => {
    mockUseAuth0.mockReturnValue({
      isLoading: true,
      isAuthenticated: false,
      user: undefined,
      loginWithRedirect: vi.fn(),
      loginWithPopup: vi.fn(),
      logout: vi.fn(),
      getAccessTokenSilently: vi.fn(),
      getAccessTokenWithPopup: vi.fn(),
      getIdTokenClaims: vi.fn(),
      handleRedirectCallback: vi.fn(),
    } as unknown as ReturnType<typeof useAuth0>)

    renderAppAtRoute('/')
    expect(screen.getByTestId('page-skeleton')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /welcome/i })).not.toBeInTheDocument()
  })
})
