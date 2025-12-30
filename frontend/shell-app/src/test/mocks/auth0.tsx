/* eslint-disable react-refresh/only-export-components */
import { vi } from 'vitest'
import { type ReactNode } from 'react'
import type { useAuth0 } from '@auth0/auth0-react'

// Helper type - the return type of useAuth0 hook
type Auth0ContextType = ReturnType<typeof useAuth0>

export const mockUser = {
  sub: 'auth0|123456789',
  email: 'test@example.com',
  email_verified: true,
  name: 'Test User',
  nickname: 'testuser',
  picture: 'https://example.com/avatar.jpg',
  updated_at: '2024-01-01T00:00:00.000Z',
}

export const createAuth0Mock = (overrides = {}) => ({
  isAuthenticated: false,
  isLoading: false,
  user: undefined,
  loginWithRedirect: vi.fn(),
  loginWithPopup: vi.fn(),
  logout: vi.fn(),
  getAccessTokenSilently: vi.fn().mockResolvedValue('mock-access-token'),
  getAccessTokenWithPopup: vi.fn().mockResolvedValue('mock-access-token'),
  getIdTokenClaims: vi.fn().mockResolvedValue({ __raw: 'mock-id-token' }),
  handleRedirectCallback: vi.fn(),
  ...overrides,
})

/**
 * Helper function to create a typed Auth0 mock.
 * This avoids repeating "as unknown as Auth0ContextType" everywhere.
 *
 * Usage:
 *   mockedUseAuth0.mockReturnValue(mockAuth0())
 *   mockedUseAuth0.mockReturnValue(mockAuth0({ isAuthenticated: true }))
 */
export const mockAuth0 = (overrides = {}): Auth0ContextType => {
  return createAuth0Mock(overrides) as unknown as Auth0ContextType
}

export const authenticatedAuth0Mock = mockAuth0({
  isAuthenticated: true,
  user: mockUser,
})

export const loadingAuth0Mock = mockAuth0({
  isLoading: true,
})

interface MockAuth0ProviderProps {
  children: ReactNode
}

export const MockAuth0Provider = ({ children }: MockAuth0ProviderProps) => {
  return <>{children}</>
}

export const mockUseAuth0 = vi.fn(() => createAuth0Mock())
