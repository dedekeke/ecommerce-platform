import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { LoginButton } from './LoginButton'
import { mockAuth0, authenticatedAuth0Mock, loadingAuth0Mock } from '../../test/mocks/auth0'

vi.mock('@auth0/auth0-react', () => ({
  useAuth0: vi.fn(),
}))

import { useAuth0 } from '@auth0/auth0-react'

const mockedUseAuth0 = vi.mocked(useAuth0)

describe('LoginButton', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should render login button when user is not authenticated', () => {
    mockedUseAuth0.mockReturnValue(mockAuth0())

    render(<LoginButton />)

    expect(screen.getByRole('button', { name: /log in/i })).toBeInTheDocument()
  })

  it('should not render when user is authenticated', () => {
    mockedUseAuth0.mockReturnValue(authenticatedAuth0Mock)

    render(<LoginButton />)

    expect(screen.queryByRole('button', { name: /log in/i })).not.toBeInTheDocument()
  })

  it('should be disabled when loading', () => {
    mockedUseAuth0.mockReturnValue(loadingAuth0Mock)

    render(<LoginButton />)

    expect(screen.getByRole('button', { name: /log in/i })).toBeDisabled()
  })

  it('should call loginWithRedirect when clicked', () => {
    const loginWithRedirect = vi.fn()
    mockedUseAuth0.mockReturnValue(mockAuth0({ loginWithRedirect }))

    render(<LoginButton />)

    fireEvent.click(screen.getByRole('button', { name: /log in/i }))

    expect(loginWithRedirect).toHaveBeenCalledTimes(1)
  })

  it('should accept custom className', () => {
    mockedUseAuth0.mockReturnValue(mockAuth0())

    render(<LoginButton className="custom-class" />)

    expect(screen.getByRole('button', { name: /log in/i })).toHaveClass('custom-class')
  })

  it('should accept custom children', () => {
    mockedUseAuth0.mockReturnValue(mockAuth0())

    render(<LoginButton>Sign In</LoginButton>)

    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
  })
})
