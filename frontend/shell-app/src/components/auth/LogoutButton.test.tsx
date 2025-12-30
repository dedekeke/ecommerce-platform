import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { LogoutButton } from './LogoutButton'
import { mockAuth0, authenticatedAuth0Mock } from '../../test/mocks/auth0'

vi.mock('@auth0/auth0-react', () => ({
  useAuth0: vi.fn(),
}))

import { useAuth0 } from '@auth0/auth0-react'

const mockedUseAuth0 = vi.mocked(useAuth0)

describe('LogoutButton', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should render logout button when user is authenticated', () => {
    mockedUseAuth0.mockReturnValue(authenticatedAuth0Mock)

    render(<LogoutButton />)

    expect(screen.getByRole('button', { name: /log out/i })).toBeInTheDocument()
  })

  it('should not render when user is not authenticated', () => {
    mockedUseAuth0.mockReturnValue(mockAuth0())

    render(<LogoutButton />)

    expect(screen.queryByRole('button', { name: /log out/i })).not.toBeInTheDocument()
  })

  it('should be disabled when loading', () => {
    mockedUseAuth0.mockReturnValue(mockAuth0({ isAuthenticated: true, isLoading: true }))

    render(<LogoutButton />)

    expect(screen.getByRole('button', { name: /log out/i })).toBeDisabled()
  })

  it('should call logout with returnTo when clicked', () => {
    const logout = vi.fn()
    mockedUseAuth0.mockReturnValue(mockAuth0({ isAuthenticated: true, logout }))

    render(<LogoutButton />)

    fireEvent.click(screen.getByRole('button', { name: /log out/i }))

    expect(logout).toHaveBeenCalledWith({
      logoutParams: {
        returnTo: window.location.origin,
      },
    })
  })

  it('should accept custom className', () => {
    mockedUseAuth0.mockReturnValue(authenticatedAuth0Mock)

    render(<LogoutButton className="custom-class" />)

    expect(screen.getByRole('button', { name: /log out/i })).toHaveClass('custom-class')
  })

  it('should accept custom children', () => {
    mockedUseAuth0.mockReturnValue(authenticatedAuth0Mock)

    render(<LogoutButton>Sign Out</LogoutButton>)

    expect(screen.getByRole('button', { name: /sign out/i })).toBeInTheDocument()
  })
})
