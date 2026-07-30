import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { Auth0ProviderWithNavigate } from './Auth0ProviderWithNavigate'
import type { Auth0ProviderOptions } from '@auth0/auth0-react'

vi.mock('@auth0/auth0-react', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@auth0/auth0-react')>()),
  Auth0Provider: vi.fn(({ children }) => <div data-testid="auth0-provider">{children}</div>),
}))

import { Auth0Provider, useAuth0 } from '@auth0/auth0-react'

const mockedAuth0Provider = vi.mocked(Auth0Provider)

describe('Auth0ProviderWithNavigate', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubEnv('VITE_AUTH0_DOMAIN', 'test.auth0.com')
    vi.stubEnv('VITE_AUTH0_CLIENT_ID', 'test-client-id')
    vi.stubEnv('VITE_AUTH0_AUDIENCE', 'https://test-api.example.com')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('should render Auth0Provider with correct configuration', () => {
    render(
      <MemoryRouter>
        <Auth0ProviderWithNavigate>
          <div>Test Child</div>
        </Auth0ProviderWithNavigate>
      </MemoryRouter>
    )

    expect(screen.getByTestId('auth0-provider')).toBeInTheDocument()
    expect(screen.getByText('Test Child')).toBeInTheDocument()
  })

  it('should pass domain and clientId to Auth0Provider', () => {
    render(
      <MemoryRouter>
        <Auth0ProviderWithNavigate>
          <div>Test Child</div>
        </Auth0ProviderWithNavigate>
      </MemoryRouter>
    )

    const callArgs = mockedAuth0Provider.mock.calls[0][0] as Auth0ProviderOptions
    expect(callArgs.domain).toBe('test.auth0.com')
    expect(callArgs.clientId).toBe('test-client-id')
  })

  it('should configure authorizationParams with audience and redirect_uri', () => {
    render(
      <MemoryRouter>
        <Auth0ProviderWithNavigate>
          <div>Test Child</div>
        </Auth0ProviderWithNavigate>
      </MemoryRouter>
    )

    const callArgs = mockedAuth0Provider.mock.calls[0][0] as Auth0ProviderOptions
    expect(callArgs.authorizationParams).toBeDefined()
    expect(callArgs.authorizationParams?.audience).toBe('https://test-api.example.com')
    expect(callArgs.authorizationParams?.redirect_uri).toBeDefined()
  })

  it('should configure cacheLocation as memory for security', () => {
    render(
      <MemoryRouter>
        <Auth0ProviderWithNavigate>
          <div>Test Child</div>
        </Auth0ProviderWithNavigate>
      </MemoryRouter>
    )

    const callArgs = mockedAuth0Provider.mock.calls[0][0] as Auth0ProviderOptions
    expect(callArgs.cacheLocation).toBe('memory')
  })

  it('should configure useRefreshTokens for secure token management', () => {
    render(
      <MemoryRouter>
        <Auth0ProviderWithNavigate>
          <div>Test Child</div>
        </Auth0ProviderWithNavigate>
      </MemoryRouter>
    )

    const callArgs = mockedAuth0Provider.mock.calls[0][0] as Auth0ProviderOptions
    expect(callArgs.useRefreshTokens).toBe(true)
  })

  describe('when VITE_AUTH_MODE=mock (local test auth mode)', () => {
    beforeEach(() => {
      vi.stubEnv('VITE_AUTH_MODE', 'mock')
    })

    // The mock provider is code-split behind a DEV-gated dynamic import, so
    // assertions await the lazy chunk via findBy*.
    it('should render children through MockAuthProvider without mounting Auth0Provider', async () => {
      render(
        <MemoryRouter>
          <Auth0ProviderWithNavigate>
            <div>Test Child</div>
          </Auth0ProviderWithNavigate>
        </MemoryRouter>
      )

      expect(await screen.findByText('Test Child')).toBeInTheDocument()
      expect(screen.queryByTestId('auth0-provider')).not.toBeInTheDocument()
      expect(mockedAuth0Provider).not.toHaveBeenCalled()
    })

    it('should report the deterministic mock identity as authenticated', async () => {
      function Probe() {
        const { isAuthenticated, user } = useAuth0()
        return <div>{isAuthenticated ? `sub:${user?.sub}` : 'anonymous'}</div>
      }

      render(
        <MemoryRouter>
          <Auth0ProviderWithNavigate>
            <Probe />
          </Auth0ProviderWithNavigate>
        </MemoryRouter>
      )

      expect(await screen.findByText('sub:e2e|test-user')).toBeInTheDocument()
    })
  })

  describe('when VITE_AUTH_MODE has a non-mock value', () => {
    it('should keep the real Auth0Provider path', () => {
      vi.stubEnv('VITE_AUTH_MODE', 'auth0')

      render(
        <MemoryRouter>
          <Auth0ProviderWithNavigate>
            <div>Test Child</div>
          </Auth0ProviderWithNavigate>
        </MemoryRouter>
      )

      expect(screen.getByTestId('auth0-provider')).toBeInTheDocument()
      expect(mockedAuth0Provider).toHaveBeenCalledOnce()
    })
  })
})
