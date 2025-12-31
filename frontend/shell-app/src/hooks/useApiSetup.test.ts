import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useApiSetup } from './useApiSetup'
import * as auth0 from '@auth0/auth0-react'
import * as router from 'react-router-dom'
import * as api from '../api'
import * as notifications from './useNotifications'

vi.mock('@auth0/auth0-react', () => ({
  useAuth0: vi.fn(),
}))

vi.mock('react-router-dom', () => ({
  useNavigate: vi.fn(),
}))

vi.mock('../api', () => ({
  setupAuthInterceptor: vi.fn(),
  setupResponseInterceptor: vi.fn(),
  removeAuthInterceptor: vi.fn(),
  removeResponseInterceptor: vi.fn(),
}))

vi.mock('./useNotifications', () => ({
  useNotifications: vi.fn(),
}))

describe('useApiSetup', () => {
  const mockGetAccessTokenSilently = vi.fn()
  const mockLogout = vi.fn()
  const mockNavigate = vi.fn()
  const mockShowError = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()

    vi.mocked(auth0.useAuth0).mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      getAccessTokenSilently: mockGetAccessTokenSilently,
      logout: mockLogout,
      user: undefined,
      getAccessTokenWithPopup: vi.fn(),
      getIdTokenClaims: vi.fn(),
      loginWithRedirect: vi.fn(),
      loginWithPopup: vi.fn(),
      handleRedirectCallback: vi.fn(),
    })

    vi.mocked(router.useNavigate).mockReturnValue(mockNavigate)

    vi.mocked(notifications.useNotifications).mockReturnValue({
      notifications: [],
      showSuccess: vi.fn(),
      showError: mockShowError,
      showWarning: vi.fn(),
      showInfo: vi.fn(),
      show: vi.fn(),
      remove: vi.fn(),
      clear: vi.fn(),
    })

    vi.mocked(api.setupAuthInterceptor).mockReturnValue(1)
    vi.mocked(api.setupResponseInterceptor).mockReturnValue(2)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('should setup auth interceptor on mount', () => {
    renderHook(() => useApiSetup())

    expect(api.setupAuthInterceptor).toHaveBeenCalled()
  })

  it('should setup response interceptor on mount', () => {
    renderHook(() => useApiSetup())

    expect(api.setupResponseInterceptor).toHaveBeenCalled()
  })

  it('should cleanup interceptors on unmount', () => {
    const { unmount } = renderHook(() => useApiSetup())

    unmount()

    expect(api.removeAuthInterceptor).toHaveBeenCalledWith(1)
    expect(api.removeResponseInterceptor).toHaveBeenCalledWith(2)
  })

  it('should get access token when authenticated', async () => {
    mockGetAccessTokenSilently.mockResolvedValue('test-token')

    renderHook(() => useApiSetup())

    const getTokenFn = vi.mocked(api.setupAuthInterceptor).mock.calls[0][0]

    const token = await getTokenFn()

    expect(mockGetAccessTokenSilently).toHaveBeenCalled()
    expect(token).toBe('test-token')
  })

  it('should return null when not authenticated', async () => {
    vi.mocked(auth0.useAuth0).mockReturnValue({
      isAuthenticated: false,
      isLoading: false,
      getAccessTokenSilently: mockGetAccessTokenSilently,
      logout: mockLogout,
      user: undefined,
      getAccessTokenWithPopup: vi.fn(),
      getIdTokenClaims: vi.fn(),
      loginWithRedirect: vi.fn(),
      loginWithPopup: vi.fn(),
      handleRedirectCallback: vi.fn(),
    })

    renderHook(() => useApiSetup())

    const getTokenFn = vi.mocked(api.setupAuthInterceptor).mock.calls[0][0]

    const token = await getTokenFn()

    expect(token).toBeNull()
    expect(mockGetAccessTokenSilently).not.toHaveBeenCalled()
  })

  it('should handle token fetch error gracefully', async () => {
    mockGetAccessTokenSilently.mockRejectedValue(new Error('Token error'))

    renderHook(() => useApiSetup())

    const getTokenFn = vi.mocked(api.setupAuthInterceptor).mock.calls[0][0]
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    const token = await getTokenFn()

    expect(token).toBeNull()
    expect(consoleSpy).toHaveBeenCalled()

    consoleSpy.mockRestore()
  })

  it('should call logout on 401 error', () => {
    renderHook(() => useApiSetup())

    const onUnauthorized = vi.mocked(api.setupResponseInterceptor).mock.calls[0][0]

    onUnauthorized?.()

    expect(mockShowError).toHaveBeenCalledWith(
      'Your session has expired. Please log in again.'
    )
    expect(mockLogout).toHaveBeenCalled()
  })

  it('should navigate home on 403 error', () => {
    renderHook(() => useApiSetup())

    const onForbidden = vi.mocked(api.setupResponseInterceptor).mock.calls[0][1]

    onForbidden?.()

    expect(mockShowError).toHaveBeenCalledWith(
      'You do not have permission to perform this action.'
    )
    expect(mockNavigate).toHaveBeenCalledWith('/')
  })
})
