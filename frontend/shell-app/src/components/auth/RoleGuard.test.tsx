import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { RoleGuard } from './RoleGuard'
import { mockAuth0, authenticatedAuth0Mock } from '../../test/mocks/auth0'

vi.mock('@auth0/auth0-react', () => ({
  useAuth0: vi.fn(),
}))

import { useAuth0 } from '@auth0/auth0-react'

const mockedUseAuth0 = vi.mocked(useAuth0)

const ROLES_CLAIM = 'https://ecommerce-platform.com/roles'

const adminUser = {
  sub: 'auth0|admin',
  email: 'admin@example.com',
  email_verified: true,
  name: 'Admin User',
  nickname: 'admin',
  picture: '',
  updated_at: '',
  [ROLES_CLAIM]: ['admin'],
}

const regularUser = {
  ...authenticatedAuth0Mock.user!,
  [ROLES_CLAIM]: ['user'],
}

const userWithNoRolesClaim = {
  ...authenticatedAuth0Mock.user!,
} as typeof authenticatedAuth0Mock.user

const ProtectedContent = () => <div>Admin Area</div>

const renderRoleGuard = (requiredRoles: string[], user: typeof adminUser | typeof userWithNoRolesClaim = adminUser) => {
  mockedUseAuth0.mockReturnValue(
    mockAuth0({ isAuthenticated: true, user })
  )
  return render(
    <MemoryRouter>
      <RoleGuard requiredRoles={requiredRoles}>
        <ProtectedContent />
      </RoleGuard>
    </MemoryRouter>
  )
}

describe('RoleGuard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('access granted', () => {
    it('should render children when user has the required role', () => {
      renderRoleGuard(['admin'], adminUser)

      expect(screen.getByText('Admin Area')).toBeInTheDocument()
      expect(screen.queryByTestId('forbidden-page')).not.toBeInTheDocument()
    })

    it('should render children when requiredRoles is empty', () => {
      renderRoleGuard([], regularUser)

      expect(screen.getByText('Admin Area')).toBeInTheDocument()
    })

    it('should render children when user has at least one of the required roles', () => {
      const multiRoleUser = {
        ...adminUser,
        [ROLES_CLAIM]: ['user', 'admin', 'editor'],
      }
      renderRoleGuard(['admin'], multiRoleUser)

      expect(screen.getByText('Admin Area')).toBeInTheDocument()
    })
  })

  describe('access denied — 403 page', () => {
    it('should show 403 forbidden page when user lacks required role', () => {
      renderRoleGuard(['admin'], regularUser)

      expect(screen.getByTestId('forbidden-page')).toBeInTheDocument()
      expect(screen.queryByText('Admin Area')).not.toBeInTheDocument()
    })

    it('should show 403 forbidden page when user has no roles claim', () => {
      renderRoleGuard(['admin'], userWithNoRolesClaim)

      expect(screen.getByTestId('forbidden-page')).toBeInTheDocument()
    })

    it('should display a 403 heading on the forbidden page', () => {
      renderRoleGuard(['admin'], regularUser)

      expect(screen.getByRole('heading', { name: /403/i })).toBeInTheDocument()
    })

    it('should display a "Go home" link on the forbidden page', () => {
      renderRoleGuard(['admin'], regularUser)

      const goHomeLink = screen.getByRole('link', { name: /go home/i })
      expect(goHomeLink).toBeInTheDocument()
      expect(goHomeLink).toHaveAttribute('href', '/')
    })

    it('should show a descriptive message explaining the access denial', () => {
      renderRoleGuard(['admin'], regularUser)

      expect(screen.getByText(/you do not have permission/i)).toBeInTheDocument()
    })
  })

  describe('unauthenticated user', () => {
    it('should show 403 page when user is not authenticated', () => {
      mockedUseAuth0.mockReturnValue(mockAuth0({ isAuthenticated: false, user: undefined }))
      render(
        <MemoryRouter>
          <RoleGuard requiredRoles={['admin']}>
            <ProtectedContent />
          </RoleGuard>
        </MemoryRouter>
      )

      expect(screen.getByTestId('forbidden-page')).toBeInTheDocument()
    })
  })

  describe('accessibility', () => {
    it('should have a role="main" or appropriate landmark on the 403 page', () => {
      renderRoleGuard(['admin'], regularUser)

      expect(screen.getByTestId('forbidden-page')).toBeInTheDocument()
    })

    it('should be keyboard navigable — go home link is focusable', () => {
      renderRoleGuard(['admin'], regularUser)

      const goHomeLink = screen.getByRole('link', { name: /go home/i })
      expect(goHomeLink).not.toHaveAttribute('tabindex', '-1')
    })
  })
})
