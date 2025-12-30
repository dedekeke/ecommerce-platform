import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { ProtectedRoute } from './ProtectedRoute'
import { mockAuth0, authenticatedAuth0Mock, loadingAuth0Mock } from '../../test/mocks/auth0'

vi.mock('@auth0/auth0-react', () => ({
  useAuth0: vi.fn(),
}))

import { useAuth0 } from '@auth0/auth0-react'

const mockedUseAuth0 = vi.mocked(useAuth0)

const TestComponent = () => <div>Protected Content</div>

const renderProtectedRoute = () => {
  return render(
    <MemoryRouter initialEntries={['/protected']}>
      <Routes>
        <Route path="/" element={<div>Home</div>} />
        <Route
          path="/protected"
          element={
            <ProtectedRoute>
              <TestComponent />
            </ProtectedRoute>
          }
        />
      </Routes>
    </MemoryRouter>
  )
}

describe('ProtectedRoute', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should show loading indicator when authentication is loading', () => {
    mockedUseAuth0.mockReturnValue(loadingAuth0Mock)

    renderProtectedRoute()

    expect(screen.getByTestId('auth-loading')).toBeInTheDocument()
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument()
  })

  it('should render children when user is authenticated', () => {
    mockedUseAuth0.mockReturnValue(authenticatedAuth0Mock)

    renderProtectedRoute()

    expect(screen.getByText('Protected Content')).toBeInTheDocument()
    expect(screen.queryByTestId('auth-loading')).not.toBeInTheDocument()
  })

  it('should redirect to login when user is not authenticated', () => {
    const loginWithRedirect = vi.fn()
    mockedUseAuth0.mockReturnValue(mockAuth0({ loginWithRedirect }))

    renderProtectedRoute()

    expect(loginWithRedirect).toHaveBeenCalledWith({
      appState: { returnTo: '/protected' },
    })
  })

  it('should not redirect when already loading', () => {
    const loginWithRedirect = vi.fn()
    mockedUseAuth0.mockReturnValue(mockAuth0({ isLoading: true, loginWithRedirect }))

    renderProtectedRoute()

    expect(loginWithRedirect).not.toHaveBeenCalled()
  })
})
