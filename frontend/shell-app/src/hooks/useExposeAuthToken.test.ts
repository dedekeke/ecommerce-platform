import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useExposeAuthToken } from './useExposeAuthToken'
import * as auth0 from '@auth0/auth0-react'
import { mockAuth0, mockUser } from '../test/mocks/auth0'

vi.mock('@auth0/auth0-react', () => ({
  useAuth0: vi.fn(),
}))

describe('useExposeAuthToken', () => {
  const mockGetAccessTokenSilently = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    delete window.__getAuthToken
    delete window.__getAuthUserId
  })

  afterEach(() => {
    delete window.__getAuthToken
    delete window.__getAuthUserId
  })

  function mockAuth(isAuthenticated: boolean, user?: typeof mockUser) {
    vi.mocked(auth0.useAuth0).mockReturnValue(mockAuth0({
      isAuthenticated,
      isLoading: false,
      user: isAuthenticated ? (user ?? mockUser) : undefined,
      getAccessTokenSilently: mockGetAccessTokenSilently,
    }))
  }

  it('should set window.__getAuthToken on mount', () => {
    mockAuth(true)
    renderHook(() => useExposeAuthToken())
    expect(window.__getAuthToken).toBeTypeOf('function')
  })

  it('should remove window.__getAuthToken on unmount', () => {
    mockAuth(true)
    const { unmount } = renderHook(() => useExposeAuthToken())
    unmount()
    expect(window.__getAuthToken).toBeUndefined()
  })

  it('should return the token when authenticated', async () => {
    mockAuth(true)
    mockGetAccessTokenSilently.mockResolvedValue('access-token-123')
    renderHook(() => useExposeAuthToken())
    const token = await window.__getAuthToken!()
    expect(token).toBe('access-token-123')
    expect(mockGetAccessTokenSilently).toHaveBeenCalledOnce()
  })

  it('should return null when not authenticated', async () => {
    mockAuth(false)
    renderHook(() => useExposeAuthToken())
    const token = await window.__getAuthToken!()
    expect(token).toBeNull()
    expect(mockGetAccessTokenSilently).not.toHaveBeenCalled()
  })

  it('should return null when getAccessTokenSilently throws', async () => {
    mockAuth(true)
    mockGetAccessTokenSilently.mockRejectedValue(new Error('token error'))
    renderHook(() => useExposeAuthToken())
    const token = await window.__getAuthToken!()
    expect(token).toBeNull()
  })

  it('should expose the authenticated user sub via window.__getAuthUserId', () => {
    mockAuth(true)
    renderHook(() => useExposeAuthToken())
    expect(window.__getAuthUserId).toBeTypeOf('function')
    expect(window.__getAuthUserId!()).toBe(mockUser.sub)
  })

  it('should return null from window.__getAuthUserId when not authenticated', () => {
    mockAuth(false)
    renderHook(() => useExposeAuthToken())
    expect(window.__getAuthUserId!()).toBeNull()
  })

  it('should remove window.__getAuthUserId on unmount', () => {
    mockAuth(true)
    const { unmount } = renderHook(() => useExposeAuthToken())
    unmount()
    expect(window.__getAuthUserId).toBeUndefined()
  })
})
