import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, renderHook, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { useAuth0 } from '@auth0/auth0-react'
import type { ReactNode } from 'react'
import { MockAuthProvider } from './MockAuthProvider'
import { installMockWindowAccessors, MOCK_AUTH_TOKEN, MOCK_AUTH_USER } from './mockIdentity'
import { ProtectedRoute } from '../components/auth/ProtectedRoute'
import { useExposeAuthToken } from '../hooks/useExposeAuthToken'

const wrapper = ({ children }: { children: ReactNode }) => (
  <MockAuthProvider>{children}</MockAuthProvider>
)

describe('MockAuthProvider', () => {
  beforeEach(() => {
    delete window.__getAuthToken
    delete window.__getAuthUserId
  })

  afterEach(() => {
    delete window.__getAuthToken
    delete window.__getAuthUserId
    vi.restoreAllMocks()
  })

  it('should pin the deterministic identity and dummy token values', () => {
    expect(MOCK_AUTH_USER.sub).toBe('e2e|test-user')
    expect(MOCK_AUTH_USER.email).toBe('e2e-test-user@example.com')
    expect(MOCK_AUTH_USER.name).toBe('E2E Test User')
    expect(MOCK_AUTH_TOKEN).toBe('e2e-mock-token')
  })

  it('should install window accessors synchronously (MFEs read them during first render)', async () => {
    installMockWindowAccessors()
    expect(window.__getAuthUserId!()).toBe('e2e|test-user')
    await expect(window.__getAuthToken!()).resolves.toBe(MOCK_AUTH_TOKEN)
  })

  it('should tolerate re-install over the existing non-writable accessors', () => {
    installMockWindowAccessors()
    expect(() => installMockWindowAccessors()).not.toThrow()
    expect(window.__getAuthUserId!()).toBe('e2e|test-user')
  })

  it('should report an authenticated, non-loading session to useAuth0 consumers', () => {
    const { result } = renderHook(() => useAuth0(), { wrapper })
    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.isLoading).toBe(false)
  })

  it('should supply the deterministic test identity as the user', () => {
    const { result } = renderHook(() => useAuth0(), { wrapper })
    expect(result.current.user?.sub).toBe(MOCK_AUTH_USER.sub)
    expect(result.current.user?.email).toBe(MOCK_AUTH_USER.email)
    expect(result.current.user?.name).toBe(MOCK_AUTH_USER.name)
  })

  it('should resolve the static dummy token from getAccessTokenSilently', async () => {
    const { result } = renderHook(() => useAuth0(), { wrapper })
    await expect(result.current.getAccessTokenSilently()).resolves.toBe(MOCK_AUTH_TOKEN)
  })

  it('should never navigate on loginWithRedirect or logout (no-ops)', async () => {
    const { result } = renderHook(() => useAuth0(), { wrapper })
    const href = window.location.href
    await result.current.loginWithRedirect()
    await result.current.logout()
    expect(window.location.href).toBe(href)
  })

  it('should render ProtectedRoute children without triggering an Auth0 redirect', () => {
    render(
      <MemoryRouter initialEntries={['/checkout']}>
        <MockAuthProvider>
          <ProtectedRoute>
            <div>Protected Content</div>
          </ProtectedRoute>
        </MockAuthProvider>
      </MemoryRouter>
    )
    expect(screen.getByText('Protected Content')).toBeInTheDocument()
    expect(screen.queryByTestId('auth-loading')).not.toBeInTheDocument()
  })

  it('should install window.__getAuthUserId returning the test sub via useExposeAuthToken', () => {
    renderHook(() => useExposeAuthToken(), { wrapper })
    expect(window.__getAuthUserId).toBeTypeOf('function')
    expect(window.__getAuthUserId!()).toBe('e2e|test-user')
  })

  it('should install window.__getAuthToken resolving the dummy token via useExposeAuthToken', async () => {
    renderHook(() => useExposeAuthToken(), { wrapper })
    expect(window.__getAuthToken).toBeTypeOf('function')
    await expect(window.__getAuthToken!()).resolves.toBe(MOCK_AUTH_TOKEN)
  })
})
