import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { MFERouteGuard } from './MFERouteGuard'

// ── external mocks ──────────────────────────────────────────────────────────
vi.mock('@auth0/auth0-react', () => ({ useAuth0: vi.fn() }))
vi.mock('../../mfe/registry', () => ({
  getMFEConfig: vi.fn(),
}))

import { useAuth0 } from '@auth0/auth0-react'
import { getMFEConfig } from '../../mfe/registry'
import { mockAuth0 } from '../../test/mocks/auth0'

const ROLES_CLAIM = 'https://ecommerce-platform.com/roles'

const mockedUseAuth0 = vi.mocked(useAuth0)
const mockedGetMFEConfig = vi.mocked(getMFEConfig)

// ── helpers ─────────────────────────────────────────────────────────────────
const AdminContent = () => <div data-testid="admin-content">Admin Area</div>

function buildConfig(overrides: Record<string, unknown> = {}) {
  return {
    name: 'adminDashboard',
    displayName: 'Admin Dashboard',
    remoteUrl: 'http://localhost:5005/remoteEntry.json',
    exposedModule: './AdminDashboard',
    fallbackSkeleton: 'page',
    requiresAuth: true,
    requiredRoles: ['admin'],
    runtime: 'native',
    ...overrides,
  }
}

function renderGuard(
  mfeName: 'adminDashboard' | 'userDashboard' | 'checkout',
  userRoles: string[] | null = null,
  isAuthenticated = true
) {
  const user =
    userRoles !== null
      ? { sub: 'u1', email: 'u@test.com', [ROLES_CLAIM]: userRoles }
      : { sub: 'u1', email: 'u@test.com' }

  mockedUseAuth0.mockReturnValue(
    mockAuth0({
      isAuthenticated,
      isLoading: false,
      user: isAuthenticated ? user : undefined,
      loginWithRedirect: vi.fn(),
    })
  )

  return render(
    <MemoryRouter>
      <MFERouteGuard mfeName={mfeName}>
        <AdminContent />
      </MFERouteGuard>
    </MemoryRouter>
  )
}

// ── tests ────────────────────────────────────────────────────────────────────
describe('MFERouteGuard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('when MFE does not require auth (requiresAuth: false)', () => {
    it('should render children without checking auth or roles', () => {
      mockedGetMFEConfig.mockReturnValue(buildConfig({ requiresAuth: false, requiredRoles: [] }) as ReturnType<typeof getMFEConfig>)
      mockedUseAuth0.mockReturnValue(mockAuth0({ isAuthenticated: false, isLoading: false }))

      render(
        <MemoryRouter>
          <MFERouteGuard mfeName="checkout">
            <AdminContent />
          </MFERouteGuard>
        </MemoryRouter>
      )

      expect(screen.getByTestId('admin-content')).toBeInTheDocument()
    })
  })

  describe('when MFE requires auth with no role restriction', () => {
    it('should show auth loading while Auth0 is resolving', () => {
      mockedGetMFEConfig.mockReturnValue(buildConfig({ requiredRoles: [] }) as ReturnType<typeof getMFEConfig>)
      mockedUseAuth0.mockReturnValue(mockAuth0({ isLoading: true }))

      render(
        <MemoryRouter>
          <MFERouteGuard mfeName="userDashboard">
            <AdminContent />
          </MFERouteGuard>
        </MemoryRouter>
      )

      expect(screen.getByTestId('auth-loading')).toBeInTheDocument()
      expect(screen.queryByTestId('admin-content')).not.toBeInTheDocument()
    })

    it('should render children for authenticated user when no roles required', () => {
      mockedGetMFEConfig.mockReturnValue(buildConfig({ requiredRoles: [] }) as ReturnType<typeof getMFEConfig>)
      renderGuard('userDashboard', [], true)

      expect(screen.getByTestId('admin-content')).toBeInTheDocument()
    })
  })

  describe('when MFE requires auth AND specific roles', () => {
    beforeEach(() => {
      mockedGetMFEConfig.mockReturnValue(buildConfig() as ReturnType<typeof getMFEConfig>)
    })

    it('should render children when authenticated user has the required role', () => {
      renderGuard('adminDashboard', ['admin'], true)

      expect(screen.getByTestId('admin-content')).toBeInTheDocument()
      expect(screen.queryByTestId('forbidden-page')).not.toBeInTheDocument()
    })

    it('should show forbidden page when authenticated user lacks the required role', () => {
      renderGuard('adminDashboard', ['editor'], true)

      expect(screen.getByTestId('forbidden-page')).toBeInTheDocument()
      expect(screen.queryByTestId('admin-content')).not.toBeInTheDocument()
    })

    it('should show forbidden page when authenticated user has no roles claim', () => {
      renderGuard('adminDashboard', null, true)

      expect(screen.getByTestId('forbidden-page')).toBeInTheDocument()
    })

    it('should redirect to login (via ProtectedRoute) when user is not authenticated', () => {
      const loginWithRedirect = vi.fn()
      mockedUseAuth0.mockReturnValue(
        mockAuth0({ isAuthenticated: false, isLoading: false, loginWithRedirect })
      )

      render(
        <MemoryRouter>
          <MFERouteGuard mfeName="adminDashboard">
            <AdminContent />
          </MFERouteGuard>
        </MemoryRouter>
      )

      expect(loginWithRedirect).toHaveBeenCalled()
      expect(screen.queryByTestId('admin-content')).not.toBeInTheDocument()
    })

    it('should grant access when user has multiple roles including the required one', () => {
      renderGuard('adminDashboard', ['user', 'admin', 'editor'], true)

      expect(screen.getByTestId('admin-content')).toBeInTheDocument()
    })
  })

  describe('registry integration', () => {
    it('should call getMFEConfig with the mfeName to read requiredRoles', () => {
      mockedGetMFEConfig.mockReturnValue(buildConfig() as ReturnType<typeof getMFEConfig>)
      mockedUseAuth0.mockReturnValue(mockAuth0({ isLoading: true }))

      render(
        <MemoryRouter>
          <MFERouteGuard mfeName="adminDashboard">
            <AdminContent />
          </MFERouteGuard>
        </MemoryRouter>
      )

      expect(mockedGetMFEConfig).toHaveBeenCalledWith('adminDashboard')
    })

    it('should use requiredRoles from the registry, not a hard-coded list', async () => {
      // Registry says the MFE needs 'superadmin', not 'admin'
      mockedGetMFEConfig.mockReturnValue(
        buildConfig({ requiredRoles: ['superadmin'] }) as ReturnType<typeof getMFEConfig>
      )
      renderGuard('adminDashboard', ['admin'], true)

      await waitFor(() => {
        expect(screen.getByTestId('forbidden-page')).toBeInTheDocument()
      })
    })
  })
})
